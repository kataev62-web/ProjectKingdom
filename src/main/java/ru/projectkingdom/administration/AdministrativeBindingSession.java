package ru.projectkingdom.administration;

import java.util.UUID;

public record AdministrativeBindingSession(UUID playerId,UUID townId,AdministrativeKind kind,
                                           AdministrativeBindingRole role,String dimension,long corePos) {}
