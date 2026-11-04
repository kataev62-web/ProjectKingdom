package ru.projectkingdom.cityarchitect;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import ru.projectkingdom.cityarchitect.network.CityArchitectNetwork;

/** Standalone companion add-on for the Project Kingdom personal assistant. */
@Mod(KingdomCityArchitectMod.MOD_ID)
public final class KingdomCityArchitectMod {
    public static final String MOD_ID = "kingdom_city_architect";

    public KingdomCityArchitectMod() {
        CityArchitectNetwork.register();
        CityArchitectRuntime runtime = new CityArchitectRuntime();
        CityArchitectNetwork.configure(runtime::onDecision);
        MinecraftForge.EVENT_BUS.register(runtime);
    }
}
