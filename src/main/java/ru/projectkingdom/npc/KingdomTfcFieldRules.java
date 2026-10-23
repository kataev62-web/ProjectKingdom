package ru.projectkingdom.npc;

/** Conservative classifiers for TFC 1.20.x resources. No arbitrary stone or crop is harvested. */
public final class KingdomTfcFieldRules {
    private KingdomTfcFieldRules() {}

    /** Pickable / double-height TFC crops have their own harvest mechanics: never break them here. */
    public static boolean simpleCrop(String registryId, String implementationClass) {
        return registryId != null && registryId.startsWith("tfc:crop/")
            && implementationClass != null
            && implementationClass.startsWith("net.dries007.tfc.common.blocks.crop.DefaultCropBlock");
    }

    /** All ore grades use TFC block registry IDs tfc:ore/poor_..., normal_..., rich_... */
    public static boolean ore(String registryId) {
        return registryId != null && registryId.startsWith("tfc:ore/")
            && (registryId.startsWith("tfc:ore/poor_")
                || registryId.startsWith("tfc:ore/normal_")
                || registryId.startsWith("tfc:ore/rich_"));
    }

    /** The same TFC crop's loot table supplies its real seed item. */
    public static String seeds(String cropId) {
        if (cropId == null || !cropId.startsWith("tfc:crop/")) return "";
        String name = cropId.substring("tfc:crop/".length());
        return name.isBlank() ? "" : "tfc:seeds/" + name;
    }
}
