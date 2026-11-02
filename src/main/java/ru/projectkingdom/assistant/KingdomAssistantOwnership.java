package ru.projectkingdom.assistant;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure ownership state shared by the entity and unit tests. */
public final class KingdomAssistantOwnership {
    private UUID ownerId;
    private KingdomAssistantData.Mode mode = KingdomAssistantData.Mode.FOLLOW;

    public Optional<UUID> ownerId() {
        return Optional.ofNullable(ownerId);
    }

    /** Assignment is idempotent for the saved owner and closed to reassignment. */
    public boolean assign(UUID candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (ownerId == null) {
            ownerId = candidate;
            return true;
        }
        return ownerId.equals(candidate);
    }

    public boolean mayControl(UUID candidate, int permissionLevel) {
        return candidate != null && permissionLevel >= 4 && candidate.equals(ownerId);
    }

    public KingdomAssistantData.Mode mode() {
        return mode;
    }

    public void setMode(KingdomAssistantData.Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public CompoundTag save(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("AssistantOwner", ownerId);
        tag.putString("AssistantMode", mode.name());
        return tag;
    }

    public static KingdomAssistantOwnership load(CompoundTag tag) {
        KingdomAssistantOwnership result = new KingdomAssistantOwnership();
        if (tag.hasUUID("AssistantOwner")) result.ownerId = tag.getUUID("AssistantOwner");
        try {
            result.mode = KingdomAssistantData.Mode.valueOf(tag.getString("AssistantMode"));
        } catch (IllegalArgumentException ignored) {
            result.mode = KingdomAssistantData.Mode.FOLLOW;
        }
        return result;
    }
}
