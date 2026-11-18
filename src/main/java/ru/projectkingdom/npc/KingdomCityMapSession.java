package ru.projectkingdom.npc;

import java.util.UUID;

/**
 * Identity of an open city-planning map. A menu must not switch to another
 * city's saved proposal when a player's membership or dimension changes.
 *
 * This is an authorization precondition only; all land and payment checks
 * remain server-authoritative in the purchase/funding handlers.
 */
public record KingdomCityMapSession(UUID town, String dimension) {
    public boolean matches(UUID currentTown, String currentDimension) {
        return town != null && town.equals(currentTown)
            && dimension != null && dimension.equals(currentDimension);
    }

    public boolean mayPlan(UUID currentTown, String currentDimension,
                           UUID player, UUID founder) {
        return matches(currentTown, currentDimension)
            && player != null && player.equals(founder);
    }
}
