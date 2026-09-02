package dev.rookiemines.mine;

public record ChestSpec(BlockPoint point, ChestKind kind) {
    public enum ChestKind {
        BARREL,
        REWARD,
        SKULL_TREASURE
    }
}
