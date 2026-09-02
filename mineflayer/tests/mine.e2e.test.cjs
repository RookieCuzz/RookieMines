'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const fs = require('node:fs')
const fsp = require('node:fs/promises')
const net = require('node:net')
const os = require('node:os')
const path = require('node:path')
const { spawn } = require('node:child_process')
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const BOT_NAME = 'MineTestBot'
const PAPER_JAR = process.env.PAPER_JAR
const PLUGIN_JAR = process.env.PLUGIN_JAR
const PAPER_RUNTIME_CACHE = process.env.PAPER_RUNTIME_CACHE
const E2E_ARTIFACTS_DIRECTORY = process.env.E2E_ARTIFACTS_DIRECTORY
const JAVA_EXECUTABLE = process.env.JAVA_EXECUTABLE || 'java'

function delay (milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds))
}

function withTimeout (promise, milliseconds, label) {
  let timer
  return Promise.race([
    promise.finally(() => clearTimeout(timer)),
    new Promise((resolve, reject) => {
      timer = setTimeout(() => reject(new Error(`Timed out after ${milliseconds}ms: ${label}`)), milliseconds)
    })
  ])
}

function offlineUuid (username) {
  const bytes = crypto.createHash('md5').update(`OfflinePlayer:${username}`, 'utf8').digest()
  bytes[6] = (bytes[6] & 0x0f) | 0x30
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = bytes.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

async function freePort () {
  const server = net.createServer()
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const port = server.address().port
  await new Promise(resolve => server.close(resolve))
  return port
}

async function prepareServer (directory, port) {
  if (PAPER_RUNTIME_CACHE && fs.existsSync(PAPER_RUNTIME_CACHE)) {
    for (const name of ['cache', 'libraries', 'versions']) {
      const source = path.join(PAPER_RUNTIME_CACHE, name)
      if (fs.existsSync(source)) {
        await fsp.cp(source, path.join(directory, name), { recursive: true })
      }
    }
  }
  await fsp.mkdir(path.join(directory, 'plugins', 'RookieMines'), { recursive: true })
  await fsp.copyFile(PAPER_JAR, path.join(directory, 'paper.jar'))
  await fsp.copyFile(PLUGIN_JAR, path.join(directory, 'plugins', path.basename(PLUGIN_JAR)))
  await fsp.writeFile(path.join(directory, 'eula.txt'), 'eula=true\n')
  await fsp.writeFile(path.join(directory, 'server.properties'), [
    'server-ip=127.0.0.1',
    `server-port=${port}`,
    'online-mode=false',
    'enforce-secure-profile=false',
    'spawn-protection=0',
    'gamemode=survival',
    'difficulty=peaceful',
    'view-distance=6',
    'simulation-distance=4',
    'max-players=4',
    'motd=RookieMines E2E',
    'pause-when-empty-seconds=-1',
    'network-compression-threshold=256'
  ].join('\n') + '\n')
  await fsp.writeFile(path.join(directory, 'ops.json'), JSON.stringify([{
    uuid: offlineUuid(BOT_NAME),
    name: BOT_NAME,
    level: 4,
    bypassesPlayerLimit: true
  }], null, 2))
  await fsp.writeFile(path.join(directory, 'plugins', 'RookieMines', 'config.yml'), `
world:
  name: rookie_mines
  seed: 731992431237
  source-day-world: world
  floor-spacing: 64
  base-y: 40
generation:
  min-size: 32
  max-size: 40
  height: 12
  blocks-per-tick: 50000
  normal-dark-chance: 0.15
  skull-dark-chance: 0.30
  monster-density: 0.0
  max-monsters: 0
  generate-monsters: false
  fishing-pool-chance: 0.0
  special-room-chance: 0.0
ladder:
  base-chance: 0.02
  no-enemies-bonus: 0.04
  skull-shaft-chance: 0.20
forced-day: 1001
test-mode: true
`.trimStart())
}

async function saveRuntimeCache (directory) {
  if (!PAPER_RUNTIME_CACHE) return
  await fsp.mkdir(PAPER_RUNTIME_CACHE, { recursive: true })
  for (const name of ['cache', 'libraries', 'versions']) {
    const source = path.join(directory, name)
    if (fs.existsSync(source)) {
      await fsp.cp(source, path.join(PAPER_RUNTIME_CACHE, name), { recursive: true, force: true })
    }
  }
}

function waitForServerReady (server, output, timeout = 180000) {
  return withTimeout(new Promise((resolve, reject) => {
    const inspect = chunk => {
      const text = chunk.toString('utf8')
      output.push(text)
      if (/Done \([^)]+\)! For help/.test(output.join(''))) {
        cleanup()
        resolve()
      }
    }
    const onExit = code => {
      cleanup()
      reject(new Error(`Paper exited before ready with code ${code}`))
    }
    const cleanup = () => {
      server.stdout.off('data', inspect)
      server.stderr.off('data', inspect)
      server.off('exit', onExit)
    }
    server.stdout.on('data', inspect)
    server.stderr.on('data', inspect)
    server.once('exit', onExit)
  }), timeout, 'Paper startup')
}

function waitForBotSpawn (bot) {
  return withTimeout(new Promise((resolve, reject) => {
    bot.once('spawn', resolve)
    bot.once('kicked', reason => reject(new Error(`Bot kicked: ${String(reason)}`)))
    bot.once('error', reject)
  }), 30000, 'Mineflayer spawn')
}

function commandJson (bot, command, id) {
  const prefix = `[MINE_E2E:${id}]`
  return new Promise((resolve, reject) => {
    let retry
    let timeout
    const cleanup = () => {
      bot.off('messagestr', listener)
      clearInterval(retry)
      clearTimeout(timeout)
    }
    const listener = message => {
      const index = message.indexOf(prefix)
      if (index < 0) return
      cleanup()
      try {
        resolve(JSON.parse(message.slice(index + prefix.length)))
      } catch (error) {
        reject(new Error(`Invalid machine response: ${message}`, { cause: error }))
      }
    }
    bot.on('messagestr', listener)
    bot.chat(command)
    // A dimension-change packet temporarily pauses outgoing chat. Re-send the
    // same correlated command until Paper acknowledges it.
    retry = setInterval(() => bot.chat(command), 1000)
    timeout = setTimeout(() => {
      cleanup()
      reject(new Error(`Timed out after 30000ms: command response ${command}`))
    }, 30000)
  })
}

async function waitForFloor (bot, floor, extraPredicate = () => true) {
  for (let attempt = 0; attempt < 120; attempt++) {
    const id = `status_${floor}_${attempt}_${Date.now()}`
    const status = await commandJson(bot, `/rmine status ${id}`, id)
    if (status.ok && status.inMine && status.floor === floor && extraPredicate(status)) {
      await withTimeout(bot.waitForChunksToLoad(), 20000, `chunks for floor ${floor}`)
      return status
    }
    await delay(250)
  }
  throw new Error(`Floor ${floor} did not become ready`)
}

async function waitForInventoryItem (bot, name) {
  for (let attempt = 0; attempt < 40; attempt++) {
    const item = bot.inventory.items().find(item => item.name === name)
    if (item) return item
    await delay(250)
  }
  throw new Error(`Inventory never received ${name}`)
}

async function waitForBlockName (bot, position, expectedName) {
  let block
  for (let attempt = 0; attempt < 50; attempt++) {
    block = bot.blockAt(position)
    if (block?.name === expectedName) return block
    await delay(100)
  }
  throw new Error(`Block at ${position} never became ${expectedName}; last=${block?.name}`)
}

async function waitForNearbyBlock (bot, name, maxDistance) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const block = bot.findBlock({
      matching: candidate => candidate != null && candidate.name === name,
      maxDistance
    })
    if (block) return block
    await delay(100)
  }
  throw new Error(`Nearby block never became visible: ${name}`)
}

async function stopServer (server) {
  if (server.exitCode !== null) return
  server.stdin.write('stop\n')
  try {
    await withTimeout(new Promise(resolve => server.once('exit', resolve)), 30000, 'Paper shutdown')
  } catch (error) {
    server.kill()
    throw error
  }
}

async function copyFailureArtifacts (serverDirectory, snapshot) {
  if (!E2E_ARTIFACTS_DIRECTORY) return serverDirectory

  const destination = path.join(
    path.resolve(E2E_ARTIFACTS_DIRECTORY),
    `failure-${new Date().toISOString().replace(/[:.]/g, '-')}`
  )
  await fsp.mkdir(destination, { recursive: true })
  const files = [
    ['e2e-console.log', 'e2e-console.log'],
    [path.join('logs', 'latest.log'), 'paper-latest.log'],
    [path.join('plugins', 'RookieMines', 'config.yml'), 'RookieMines-config.yml'],
    [path.join('plugins', 'RookieMines', 'floors.yml'), 'RookieMines-floors.yml'],
    [path.join('plugins', 'RookieMines', 'players.yml'), 'RookieMines-players.yml']
  ]
  for (const [sourceName, targetName] of files) {
    const source = path.join(serverDirectory, sourceName)
    if (fs.existsSync(source)) {
      await fsp.copyFile(source, path.join(destination, targetName))
    }
  }
  await fsp.writeFile(
    path.join(destination, 'bot-snapshot.json'),
    JSON.stringify(snapshot, null, 2) + '\n'
  )
  return destination
}

test('Paper 1.21.4 procedural mines work through a real Mineflayer client', { timeout: 300000 }, async () => {
  assert.ok(PAPER_JAR && fs.existsSync(PAPER_JAR), 'PAPER_JAR must point to Paper 1.21.4')
  assert.ok(PLUGIN_JAR && fs.existsSync(PLUGIN_JAR), 'PLUGIN_JAR must point to the built plugin')

  const serverDirectory = await fsp.mkdtemp(path.join(os.tmpdir(), 'rookie-mines-e2e-'))
  const port = await freePort()
  await prepareServer(serverDirectory, port)
  const output = []
  const server = spawn(JAVA_EXECUTABLE, [
    '-DrookieMines.e2e=true',
    `-Djdk.net.unixdomain.tmpdir=${path.join(serverDirectory, 'disabled-unix-domain-sockets')}`,
    '-Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.WindowsSelectorProvider',
    '-Djava.net.preferIPv4Stack=true',
    '-Dio.netty.eventLoopThreads=1',
    '-Xms512M', '-Xmx1200M', '-jar', 'paper.jar', '--nogui'
  ], {
    cwd: serverDirectory,
    stdio: ['pipe', 'pipe', 'pipe'],
    windowsHide: true
  })
  let bot
  let succeeded = false
  try {
    await waitForServerReady(server, output)
    bot = mineflayer.createBot({
      host: '127.0.0.1',
      port,
      username: BOT_NAME,
      auth: 'offline',
      version: '1.21.4',
      hideErrors: false
    })
    await waitForBotSpawn(bot)
    await withTimeout(bot.waitForChunksToLoad(), 20000, 'initial chunks')

    const expectedThemes = new Map([
      [1, 'EARTH'], [39, 'EARTH'], [40, 'FROST'], [79, 'FROST'],
      [80, 'LAVA'], [119, 'LAVA'], [120, 'LAVA'], [121, 'LOBBY'], [122, 'SKULL']
    ])
    for (const [floor, theme] of expectedThemes) {
      const id = `describe_${floor}`
      const description = await commandJson(bot, `/rmine admin describe ${floor} ${id}`, id)
      assert.equal(description.theme, theme, `floor ${floor} theme`)
    }

    bot.chat('/rmine admin goto 1')
    const first = await waitForFloor(bot, 1)
    assert.equal(first.day, 1001)
    assert.equal(first.generationCount, 1)
    assert.match(first.layoutHash, /^[0-9a-f]{16}$/)
    assert.ok(Math.abs(bot.entity.position.z - 64) < 8, 'bot arrived near floor 1 center')

    const visibleRock = bot.findBlock({
      matching: block => block != null && ['stone', 'copper_ore', 'coal_ore', 'amethyst_block'].includes(block.name),
      maxDistance: 30
    })
    assert.ok(visibleRock, 'Mineflayer can independently see a generated earth-theme block')

    bot.chat(`/give ${BOT_NAME} minecraft:diamond_pickaxe 1`)
    await waitForInventoryItem(bot, 'diamond_pickaxe')
    const fixtureId = `fixture_${Date.now()}`
    const fixture = await commandJson(bot, `/rmine admin fixture ${fixtureId}`, fixtureId)
    assert.equal(fixture.ok, true)
    const fixtureBlock = await waitForBlockName(
      bot,
      new Vec3(fixture.x, fixture.y, fixture.z),
      'stone'
    )
    await bot.equip(bot.inventory.items().find(item => item.name === 'diamond_pickaxe'), 'hand')
    await withTimeout(bot.dig(fixtureBlock, true, 'raycast'), 15000, 'real bot dig')

    const ladderStatus = await waitForFloor(bot, 1, status => status.ladder === true)
    assert.equal(ladderStatus.remainingStones, 0)
    const trapdoor = await waitForNearbyBlock(bot, 'oak_trapdoor', 12)
    await bot.activateBlock(trapdoor)
    const secondFloor = await waitForFloor(bot, 2)
    assert.equal(secondFloor.theme, 'EARTH')

    bot.chat('/rmine admin goto 1')
    const sameDay = await waitForFloor(bot, 1)
    assert.equal(sameDay.layoutHash, first.layoutHash)
    assert.equal(sameDay.generationCount, 1)
    assert.equal(sameDay.ladder, true)

    bot.chat('/rmine admin day 1002')
    await delay(100)
    bot.chat('/rmine admin goto 1')
    const nextDay = await waitForFloor(bot, 1, status => status.day === 1002)
    assert.notEqual(nextDay.layoutHash, first.layoutHash)
    assert.equal(nextDay.generationCount, 2)
    assert.equal(nextDay.ladder, false)

    bot.chat('/rmine admin goto 120')
    await waitForFloor(bot, 120)
    const rewardChest = bot.findBlock({
      matching: block => block != null && block.name === 'chest',
      maxDistance: 8
    })
    assert.ok(rewardChest, 'floor 120 reward chest is visible')
    await bot.activateBlock(rewardChest)
    await waitForInventoryItem(bot, 'trial_key')

    bot.chat('/rmine leave')
    await delay(300)
    bot.chat('/rmine skull')
    const lobby = await waitForFloor(bot, 121)
    assert.equal(lobby.theme, 'LOBBY')

    bot.chat('/rmine admin goto 122')
    const skullFirst = await waitForFloor(bot, 122)
    assert.equal(skullFirst.theme, 'SKULL')
    bot.chat('/rmine leave')
    await delay(500)
    bot.chat('/rmine admin goto 122')
    const skullSecond = await waitForFloor(bot, 122, status => status.seed !== skullFirst.seed)
    assert.notEqual(skullSecond.seed, skullFirst.seed, 'Skull Cavern session resets after everyone leaves')

    bot.chat('/execute in minecraft:overworld run tp @s 0 100 0')
    await delay(1000)
    const outsideId = `outside_${Date.now()}`
    const outside = await commandJson(bot, `/rmine status ${outsideId}`, outsideId)
    assert.equal(outside.inMine, false, 'external teleport clears the stale mine-floor marker')
    bot.chat('/rmine admin goto 122')
    const skullThird = await waitForFloor(bot, 122, status => status.seed !== skullSecond.seed)
    assert.notEqual(skullThird.seed, skullSecond.seed, 'external teleport also resets the Skull Cavern session')

    console.log(JSON.stringify({
      paper: '1.21.4-232',
      mineflayer: require('mineflayer/package.json').version,
      firstLayout: first.layoutHash,
      nextDayLayout: nextDay.layoutHash,
      skullSessionSeed1: skullFirst.seed,
      skullSessionSeed2: skullSecond.seed,
      skullSessionSeed3: skullThird.seed
    }, null, 2))
    succeeded = true
  } finally {
    const botSnapshot = bot
      ? {
          username: bot.username,
          position: bot.entity
            ? { x: bot.entity.position.x, y: bot.entity.position.y, z: bot.entity.position.z }
            : null,
          health: bot.health,
          food: bot.food,
          inventory: bot.inventory
            ? bot.inventory.items().map(item => ({ name: item.name, count: item.count }))
            : []
        }
      : null
    if (bot) {
      try { bot.quit('E2E complete') } catch {}
    }
    try {
      await stopServer(server)
    } catch (error) {
      output.push(`Shutdown error: ${error.stack}\n`)
    }
    if (succeeded) {
      try {
        await saveRuntimeCache(serverDirectory)
      } catch (error) {
        output.push(`Runtime cache update error: ${error.stack}\n`)
      }
    }
    const combined = output.join('')
    await fsp.writeFile(path.join(serverDirectory, 'e2e-console.log'), combined)
    if (!succeeded) {
      const artifactDirectory = await copyFailureArtifacts(serverDirectory, botSnapshot)
      console.error(`E2E artifacts retained at ${artifactDirectory}`)
      console.error(combined.slice(-12000))
    } else {
      const tempRoot = path.resolve(os.tmpdir()) + path.sep
      const resolved = path.resolve(serverDirectory)
      if (resolved.startsWith(tempRoot) && path.basename(resolved).startsWith('rookie-mines-e2e-')) {
        await fsp.rm(resolved, { recursive: true, force: true })
      }
    }
  }
})
