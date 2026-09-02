param()

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$CacheDirectory = Join-Path $ProjectRoot 'e2e\cache'
$ArtifactsDirectory = Join-Path $ProjectRoot 'e2e\artifacts'
$PaperJar = Join-Path $CacheDirectory 'paper-1.21.4-232.jar'
$ExpectedSha256 = '5ee4f542f628a14c644410b08c94ea42e772ef4d29fe92973636b6813d4eaffc'
$PaperUrl = 'https://fill-data.papermc.io/v1/objects/5ee4f542f628a14c644410b08c94ea42e772ef4d29fe92973636b6813d4eaffc/paper-1.21.4-232.jar'

New-Item -ItemType Directory -Force -Path $CacheDirectory | Out-Null

Push-Location $ProjectRoot
try {
    $MavenWrapper = Join-Path $ProjectRoot 'mvnw.cmd'
    if (Test-Path -LiteralPath $MavenWrapper) {
        & $MavenWrapper -B -ntp clean verify
    } else {
        & mvn -B -ntp clean verify
    }
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }

    $PluginCandidates = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'target') -Filter 'RookieMines-*.jar' -File |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' })
    if ($PluginCandidates.Count -ne 1) {
        throw "Expected exactly one RookieMines plugin jar, found $($PluginCandidates.Count)."
    }

    if (-not (Test-Path -LiteralPath $PaperJar)) {
        Invoke-WebRequest -Uri $PaperUrl -OutFile $PaperJar -Headers @{
            'User-Agent' = 'RookieMines-E2E/1.0 (local integration test)'
        }
    }
    $ActualSha256 = (Get-FileHash -LiteralPath $PaperJar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($ActualSha256 -ne $ExpectedSha256) {
        throw "Paper jar checksum mismatch. Expected $ExpectedSha256 but got $ActualSha256"
    }

    Push-Location (Join-Path $ProjectRoot 'mineflayer')
    try {
        if (Test-Path -LiteralPath 'package-lock.json') {
            & npm ci
        } else {
            & npm install
        }
        if ($LASTEXITCODE -ne 0) { throw "npm dependency installation failed with exit code $LASTEXITCODE" }

        $env:PAPER_JAR = $PaperJar
        $env:PLUGIN_JAR = $PluginCandidates[0].FullName
        $env:PAPER_RUNTIME_CACHE = Join-Path $ProjectRoot 'e2e\runtime'
        $env:E2E_ARTIFACTS_DIRECTORY = $ArtifactsDirectory
        & npm run test:e2e
        if ($LASTEXITCODE -ne 0) { throw "Mineflayer E2E failed with exit code $LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}
finally {
    Pop-Location
}
