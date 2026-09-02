package dev.rookiemines.mine;

import org.bukkit.Material;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class FloorPlan {
    private final FloorDescriptor descriptor;
    private final Material[] blocks;
    private final List<ChestSpec> chests = new ArrayList<>();
    private final List<MobSpec> mobs = new ArrayList<>();
    private int initialStones;
    private BlockPoint prebuiltLadder;

    public FloorPlan(FloorDescriptor descriptor) {
        this.descriptor = descriptor;
        this.blocks = new Material[descriptor.canvasSize() * descriptor.height() * descriptor.canvasSize()];
        java.util.Arrays.fill(this.blocks, Material.AIR);
    }

    public FloorDescriptor descriptor() {
        return descriptor;
    }

    public int volume() {
        return blocks.length;
    }

    public Material materialAtIndex(int index) {
        return blocks[index];
    }

    public BlockPoint worldPointAtIndex(int index) {
        int plane = descriptor.canvasSize() * descriptor.canvasSize();
        int y = index / plane;
        int remainder = index % plane;
        int z = remainder / descriptor.canvasSize();
        int x = remainder % descriptor.canvasSize();
        return new BlockPoint(
                descriptor.originX() + x,
                descriptor.baseY() + y,
                descriptor.originZ() + z
        );
    }

    public void setLocal(int x, int y, int z, Material material) {
        if (x < 0 || x >= descriptor.canvasSize()
                || y < 0 || y >= descriptor.height()
                || z < 0 || z >= descriptor.canvasSize()) {
            return;
        }
        blocks[index(x, y, z)] = material;
    }

    public Material getLocal(int x, int y, int z) {
        if (x < 0 || x >= descriptor.canvasSize()
                || y < 0 || y >= descriptor.height()
                || z < 0 || z >= descriptor.canvasSize()) {
            return Material.AIR;
        }
        return blocks[index(x, y, z)];
    }

    private int index(int x, int y, int z) {
        return y * descriptor.canvasSize() * descriptor.canvasSize()
                + z * descriptor.canvasSize()
                + x;
    }

    public List<ChestSpec> chests() {
        return chests;
    }

    public List<MobSpec> mobs() {
        return mobs;
    }

    public int initialStones() {
        return initialStones;
    }

    public void setInitialStones(int initialStones) {
        this.initialStones = initialStones;
    }

    public BlockPoint prebuiltLadder() {
        return prebuiltLadder;
    }

    public void setPrebuiltLadder(BlockPoint prebuiltLadder) {
        this.prebuiltLadder = prebuiltLadder;
    }

    public String layoutHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Long.toString(descriptor.seed()).getBytes(StandardCharsets.UTF_8));
            for (Material block : blocks) {
                digest.update(block.name().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
