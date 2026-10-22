package ru.projectkingdom.enterprise;

import java.util.UUID;

/** Ephemeral server-side intent; no world mutation happens until a container is validated. */
public record EnterpriseBindingSession(UUID playerId,UUID enterpriseId,EnterpriseBindingRole role,String dimension) {}
