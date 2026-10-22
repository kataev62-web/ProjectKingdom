package ru.projectkingdom.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** World persistence for launcher accounts, characters and dynasties. */
public final class IdentitySavedData extends SavedData {
    private static final String KEY = "project_kingdom_identities_v1";
    private final IdentityRegistry registry = new IdentityRegistry();

    public static IdentitySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(IdentitySavedData::load, IdentitySavedData::new, KEY);
    }

    public IdentityRegistry.BindResult bind(String launcherAccountId, UUID minecraftSessionId) {
        IdentityRegistry.BindResult result = registry.bind(new IdentityIds.AccountId(launcherAccountId), minecraftSessionId);
        if (result.kind() != IdentityRegistry.BindKind.RETURNING_SESSION) setDirty();
        return result;
    }

    public IdentityRegistry registry() { return registry; }

    public static IdentitySavedData load(CompoundTag root) {
        IdentitySavedData data = new IdentitySavedData();
        ListTag rows = root.getList("Identities", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            try {
                PlayerIdentity identity = new PlayerIdentity(
                        new IdentityIds.AccountId(row.getString("Account")),
                        new IdentityIds.CharacterId(row.getUUID("Character")),
                        new IdentityIds.DynastyId(row.getUUID("Dynasty")),
                        row.getUUID("Session"), row.getBoolean("Heir"),
                        Math.max(0, row.getInt("Resurrections")));
                data.registry.restore(identity);
            } catch (RuntimeException ignored) {
                // Corrupt individual rows are quarantined; the rest of the world remains loadable.
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag rows = new ListTag();
        for (PlayerIdentity identity : registry.all()) {
            CompoundTag row = new CompoundTag();
            row.putString("Account", identity.accountId().value());
            row.putUUID("Character", identity.characterId().value());
            row.putUUID("Dynasty", identity.dynastyId().value());
            row.putUUID("Session", identity.currentSessionId());
            row.putBoolean("Heir", identity.heir());
            row.putInt("Resurrections", identity.resurrections());
            rows.add(row);
        }
        root.put("Identities", rows);
        return root;
    }
}
