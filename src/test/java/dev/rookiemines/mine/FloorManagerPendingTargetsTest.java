package dev.rookiemines.mine;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorManagerPendingTargetsTest {
    @Test
    void newestEntryRequestReplacesThePreviousFloor() {
        FloorManager.PendingTargets targets = new FloorManager.PendingTargets();
        UUID player = UUID.randomUUID();

        targets.replace(player, 5);
        targets.replace(player, 10);

        assertTrue(targets.waitingFor(5).isEmpty());
        assertEquals(Set.of(player), targets.waitingFor(10));
        assertTrue(targets.contains(player));
    }

    @Test
    void oldFloorCompletionCannotConsumeANewerRequest() {
        FloorManager.PendingTargets targets = new FloorManager.PendingTargets();
        UUID player = UUID.randomUUID();

        targets.replace(player, 15);
        targets.replace(player, 20);

        assertTrue(targets.completeFloor(15).isEmpty());
        assertEquals(Set.of(player), targets.waitingFor(20));
        assertTrue(targets.contains(player));
    }

    @Test
    void readyEntryCancellationRemovesTheOutstandingRequest() {
        FloorManager.PendingTargets targets = new FloorManager.PendingTargets();
        UUID player = UUID.randomUUID();

        targets.replace(player, 25);
        targets.cancel(player);

        assertFalse(targets.contains(player));
        assertTrue(targets.waitingFor(25).isEmpty());
    }

    @Test
    void finishOrFailureCompletesOnlyCurrentWaitersForThatFloor() {
        FloorManager.PendingTargets targets = new FloorManager.PendingTargets();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID moved = UUID.randomUUID();

        targets.replace(first, 30);
        targets.replace(second, 30);
        targets.replace(moved, 30);
        targets.replace(moved, 35);

        assertEquals(Set.of(first, second), targets.completeFloor(30));
        assertFalse(targets.contains(first));
        assertFalse(targets.contains(second));
        assertEquals(Set.of(moved), targets.waitingFor(35));
    }

    @Test
    void leaveOrQuitCancellationDoesNotAffectOtherPlayers() {
        FloorManager.PendingTargets targets = new FloorManager.PendingTargets();
        UUID leaving = UUID.randomUUID();
        UUID remaining = UUID.randomUUID();

        targets.replace(leaving, 40);
        targets.replace(remaining, 40);
        targets.cancel(leaving);

        assertFalse(targets.contains(leaving));
        assertTrue(targets.contains(remaining));
        assertEquals(Set.of(remaining), targets.waitingFor(40));
    }

    @Test
    void evacuationDoesNotOverrideAnExplicitPendingDestination() {
        FloorManager.PendingTargets targets = new FloorManager.PendingTargets();
        UUID player = UUID.randomUUID();

        targets.replace(player, 50);
        targets.addIfAbsent(player, 45);

        assertTrue(targets.waitingFor(45).isEmpty());
        assertEquals(Set.of(player), targets.waitingFor(50));
    }

    @Test
    void skullPendingQueryAndShutdownClearUseBothIndexes() {
        FloorManager.PendingTargets targets = new FloorManager.PendingTargets();
        UUID player = UUID.randomUUID();

        targets.replace(player, 122);
        assertTrue(targets.hasWaitingFloor(GenerationRules::isSkullFloor));

        targets.clear();
        assertFalse(targets.contains(player));
        assertFalse(targets.hasWaitingFloor(GenerationRules::isSkullFloor));
        assertTrue(targets.waitingFor(122).isEmpty());
    }
}
