package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * The boundary between Kingdom and the villagers of Minecraft Comes Alive.
 *
 * <p>MCA is deliberately NOT a compile dependency of Kingdom. The shipped MCA
 * jar relocates its own classes to {@code forge.net.mca} and its method names
 * come from the Yarn mappings, while Kingdom is compiled with the Mojang
 * mappings. A direct compile dependency would therefore produce calls that
 * cannot be resolved at runtime. This adapter uses only what both sides agree
 * on: the entity type registry names and the vanilla villager API.
 *
 * <p>The adapter degrades quietly: without MCA the Kingdom keeps its own
 * villagers and every method here reports "not an MCA resident".
 */
public final class KingdomMcaAdapter {
    private KingdomMcaAdapter() {}

    /** The mod id of Minecraft Comes Alive. */
    public static final String MOD_ID = "mca";
    /** The registry names of the two MCA villager types. */
    public static final String MALE = "mca:male_villager";
    public static final String FEMALE = "mca:female_villager";

    /** Whether MCA is installed and its villagers are registered. */
    public static boolean available() {
        return type(MALE) != null && type(FEMALE) != null;
    }

    /** The MCA entity type, null when MCA is not installed. */
    public static EntityType<?> type(String name) {
        ResourceLocation key = new ResourceLocation(name);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
        return type == null ? null : type;
    }

    /** Whether the entity is a villager of MCA. */
    public static boolean isResident(Entity entity) {
        if (!(entity instanceof Villager)) return false;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && MOD_ID.equals(key.getNamespace());
    }

    /** Whether the entity is one of the two MCA villager types. */
    public static boolean isResidentType(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && MOD_ID.equals(key.getNamespace());
    }

    /**
     * Creates an MCA villager.
     *
     * @return the resident, or null when MCA is not installed.
     */
    public static Villager create(ServerLevel level, boolean female) {
        EntityType<?> type = type(female ? FEMALE : MALE);
        if (type == null) return null;
        Entity entity = type.create(level);
        return entity instanceof Villager villager ? villager : null;
    }

    /**
     * The personal inventory of a resident.
     *
     * <p>MCA overrides {@code getInventory()} with its own inventory of nine
     * rows of three slots, so the vanilla call already returns the real
     * personal inventory of an MCA villager and the trade inventory of a
     * vanilla one. The container is the single source of truth for the
     * belongings of the resident.
     */
    public static Container personal(Villager resident) {
        if (resident == null || !isResident(resident)) return null;
        return resident.getInventory();
    }

    /** Whether the personal inventory of the resident belongs to MCA. */
    public static boolean hasMcaInventory(Villager resident) {
        return isResident(resident) && personal(resident) != null;
    }

    /** The identity of the resident for the operator: the type and the uuid. */
    public static String describe(Villager resident) {
        if (resident == null) return "нет жителя";
        if (!isResident(resident)) return "ванильный житель " + resident.getUUID();
        return "MCA " + (isFemale(resident) ? "женщина" : "мужчина") + " " + resident.getUUID();
    }

    private static boolean isFemale(Villager resident) {
        // The MCA entity type carries the gender; the registry name is enough.
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(resident.getType());
        return key != null && FEMALE.equals(key.toString());
    }

    /**
     * The family of the resident, read through the MCA API.
     *
     * <p>The call is reflective on purpose: the MCA classes are relocated and
     * their names are not part of any contract Kingdom may rely on. A missing
     * method or a missing mod is not an error, it simply means that the
     * family cannot be shown.
     *
     * @return a short description, or null when MCA cannot answer.
     */
    public static String family(Villager resident) {
        if (!isResident(resident)) return null;
        try {
            Method relationships = resident.getClass().getMethod("getRelationships");
            Object value = relationships.invoke(resident);
            if (value == null) return null;
            Method entry = value.getClass().getMethod("getFamilyEntry");
            Object node = entry.invoke(value);
            if (node == null) return null;
            Method partner = node.getClass().getMethod("getPartner");
            Object other = partner.invoke(node);
            if (other == null) return "один в семье";
            Method id = other.getClass().getMethod("getUuid");
            Object uuid = id.invoke(other);
            return "семья: " + uuid;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /** The uuid of the resident, the stable key of a Kingdom worker. */
    public static UUID identity(Villager resident) {
        return resident == null ? null : resident.getUUID();
    }

    /**
     * Puts a stack into the personal inventory of the resident.
     *
     * @return true when the stack was fully stored.
     */
    public static boolean store(Villager resident, ItemStack stack) {
        Container personal = personal(resident);
        if (personal == null || stack == null || stack.isEmpty()) return false;
        ItemStack copy = stack.copy();
        boolean stored = KingdomResidentInventory.insert(personal, copy);
        personal.setChanged();
        return stored && copy.isEmpty();
    }
}
