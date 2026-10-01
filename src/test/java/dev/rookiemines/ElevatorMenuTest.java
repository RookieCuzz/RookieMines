package dev.rookiemines;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ElevatorMenuTest {
    @Test
    void destinationsForClampsProgressAndIncludesOnlyUnlockedCheckpoints() {
        assertEquals(List.of(1), ElevatorMenu.destinationsFor(-1));
        assertEquals(List.of(1), ElevatorMenu.destinationsFor(0));
        assertEquals(List.of(1), ElevatorMenu.destinationsFor(4));
        assertEquals(List.of(1, 5), ElevatorMenu.destinationsFor(5));
        assertEquals(List.of(1, 5, 10, 15, 20, 25), ElevatorMenu.destinationsFor(27));

        List<Integer> through115 = ElevatorMenu.allDestinations().stream()
                .filter(floor -> floor <= 115)
                .toList();
        assertEquals(through115, ElevatorMenu.destinationsFor(119));
        assertEquals(ElevatorMenu.allDestinations(), ElevatorMenu.destinationsFor(120));
        assertEquals(ElevatorMenu.allDestinations(), ElevatorMenu.destinationsFor(10_000));
    }

    @Test
    void allDestinationsContainsExactlyTheNormalMineElevatorStops() {
        List<Integer> destinations = ElevatorMenu.allDestinations();

        assertEquals(25, destinations.size());
        assertEquals(1, destinations.getFirst());
        assertEquals(120, destinations.getLast());
        assertFalse(destinations.contains(121));
        assertEquals(
                List.of(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60,
                        65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120),
                destinations.subList(1, destinations.size())
        );
    }

    @Test
    void everyDestinationMapsToOneUniqueInventorySlot() {
        Set<Integer> slots = new HashSet<>();
        for (int floor : ElevatorMenu.allDestinations()) {
            int slot = ElevatorMenu.slotForDestination(floor);
            assertTrue(slot >= 0 && slot < 45, "invalid slot for floor " + floor);
            assertTrue(slots.add(slot), "duplicate slot " + slot + " for floor " + floor);
        }

        assertEquals(25, slots.size());
        assertEquals(10, ElevatorMenu.slotForDestination(1));
        assertEquals(14, ElevatorMenu.slotForDestination(20));
        assertEquals(40, ElevatorMenu.slotForDestination(120));
        assertEquals(-1, ElevatorMenu.slotForDestination(121));
    }
}
