package ru.projectkingdom.npc;

/** Run with plain javac/java; validates all deterministic plans without Forge dependencies. */
public final class BuildingPlanSmokeTest {
    public static void main(String[] args) {
        for (var type : KingdomBuildingTypes.Type.values()) {
            var design = KingdomBuildingTypes.design(type);
            if (design.blocks().size() < 100 || design.bill().values().stream().mapToInt(Integer::intValue).sum() != design.blocks().size())
                throw new AssertionError("Bad material bill: " + type);
            if (design.blocks().stream().noneMatch(c -> c.material().equals(type.station)))
                throw new AssertionError("Missing station: " + type);
            if (KingdomBuildingTypes.type(type.name()) != type) throw new AssertionError("Name roundtrip");
            System.out.println(type + ": " + design.blocks().size() + " blocks, " + design.bill().size() + " materials");
        }
        System.out.println("BUILDING PLANS OK");
    }
}
