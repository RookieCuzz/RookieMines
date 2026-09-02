package dev.rookiemines.mine;

public final class FloorState {
    private final FloorDescriptor descriptor;
    private int remainingStones;
    private int enemyCount;
    private int generationCount;
    private String layoutHash;
    private BlockPoint ladder;
    private int ladderDestination;
    private boolean ladderShaft;
    private BlockPoint rewardChest;

    public FloorState(
            FloorDescriptor descriptor,
            int remainingStones,
            int enemyCount,
            int generationCount,
            String layoutHash
    ) {
        this.descriptor = descriptor;
        this.remainingStones = remainingStones;
        this.enemyCount = enemyCount;
        this.generationCount = generationCount;
        this.layoutHash = layoutHash;
    }

    public FloorDescriptor descriptor() {
        return descriptor;
    }

    public int remainingStones() {
        return remainingStones;
    }

    public void decrementStone() {
        remainingStones = Math.max(0, remainingStones - 1);
    }

    public void setRemainingStones(int remainingStones) {
        this.remainingStones = Math.max(0, remainingStones);
    }

    public int enemyCount() {
        return enemyCount;
    }

    public void decrementEnemy() {
        enemyCount = Math.max(0, enemyCount - 1);
    }

    public int generationCount() {
        return generationCount;
    }

    public String layoutHash() {
        return layoutHash;
    }

    public BlockPoint ladder() {
        return ladder;
    }

    public void setLadder(BlockPoint ladder) {
        this.ladder = ladder;
    }

    public void clearLadder() {
        this.ladder = null;
        this.ladderDestination = 0;
        this.ladderShaft = false;
    }

    public int ladderDestination() {
        return ladderDestination;
    }

    public boolean ladderShaft() {
        return ladderShaft;
    }

    public void setLadderTarget(int ladderDestination, boolean ladderShaft) {
        this.ladderDestination = ladderDestination;
        this.ladderShaft = ladderShaft;
    }

    public BlockPoint rewardChest() {
        return rewardChest;
    }

    public void setRewardChest(BlockPoint rewardChest) {
        this.rewardChest = rewardChest;
    }
}
