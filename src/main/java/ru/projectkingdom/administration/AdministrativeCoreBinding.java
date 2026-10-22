package ru.projectkingdom.administration;

import java.util.Objects;
import java.util.UUID;

public record AdministrativeCoreBinding(UUID townId, AdministrativeKind kind, String dimension, long packedPos) {
    public AdministrativeCoreBinding {
        Objects.requireNonNull(townId,"townId");
        Objects.requireNonNull(kind,"kind");
        if(dimension==null||dimension.isBlank())throw new IllegalArgumentException("dimension");
    }
}
