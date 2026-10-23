package ru.projectkingdom.npc;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/** Read TFC's own temperature/hydration/nutrient rules. Never guess growth chances. */
public final class KingdomFarmClimate {
    private KingdomFarmClimate() {}
    public record Reading(boolean available, boolean suitable, int hydration, float temperature,
        String moisture, String warmth, float primaryNutrient, boolean enoughLight) {
        public String explanation() {
            if(!available)return "Нет данных климата TFC: посадка остановлена, проверь версию TFC и загрузку чанка.";
            StringBuilder s=new StringBuilder("Влажность "+hydration+"% ("+moisture+"), t="
                +String.format(java.util.Locale.ROOT,"%.1f",temperature)+" C ("+warmth+")");
            if(!enoughLight)s.append("; слишком темно — нужно освещение");
            if("LOW".equals(moisture))s.append("; рекомендуются канавы с пресной водой рядом с грядками");
            if("HIGH".equals(moisture))s.append("; этой культуре здесь слишком влажно");
            if("LOW".equals(warmth)||"HIGH".equals(warmth))s.append("; выбери другой сезон или культуру");
            if(primaryNutrient>=0&&primaryNutrient<0.35f)s.append("; мало основного N/P/K — добавь удобрение или чередуй культуры");
            return s.toString();
        }
    }
    private static final Reading UNKNOWN=new Reading(false,false,0,0,"?","?",-1,false);
    private static boolean loaded;
    private static boolean usable;
    private static Method temperature, hydration;
    private static Class<?> nutrientType;
    private static boolean init() {
        if(loaded)return usable;
        loaded=true;
        if(!ModList.get().isLoaded("tfc"))return false;
        try {
            Class<?> climate=Class.forName("net.dries007.tfc.util.climate.Climate");
            Class<?> farm=Class.forName("net.dries007.tfc.common.blocks.soil.FarmlandBlock");
            nutrientType=Class.forName("net.dries007.tfc.common.blockentities.FarmlandBlockEntity$NutrientType");
            temperature=climate.getMethod("getTemperature",Level.class,BlockPos.class);
            hydration=farm.getMethod("getHydration",LevelAccessor.class,BlockPos.class);
            usable=true;
        }catch(ReflectiveOperationException | LinkageError ex) {
            System.err.println("[Kingdom] TFC field climate unavailable: "+ex);
        }
        return usable;
    }

    /** cropPos is exactly one block ABOVE farmland. */
    public static Reading read(ServerLevel level,BlockPos cropPos,BlockState cropState) {
        if(!level.hasChunkAt(cropPos)||!level.hasChunkAt(cropPos.below())
                ||cropState==null||!init())return UNKNOWN;
        try {
            Object block=cropState.getBlock();
            Object range=block.getClass().getMethod("getClimateRange").invoke(block);
            int water=((Number)hydration.invoke(null,level,cropPos.below())).intValue();
            float heat=((Number)temperature.invoke(null,level,cropPos)).floatValue();
            String wet=range.getClass().getMethod("checkHydration",int.class,boolean.class)
                .invoke(range,water,false).toString();
            String warm=range.getClass().getMethod("checkTemperature",float.class,boolean.class)
                .invoke(range,heat,false).toString();
            float nutrient=-1f;
            Object farm=level.getBlockEntity(cropPos.below());
            if(farm!=null){
                Object type=block.getClass().getMethod("getPrimaryNutrient").invoke(block);
                if(nutrientType.isInstance(type)) {
                    nutrient=((Number)farm.getClass().getMethod("getNutrient",nutrientType)
                        .invoke(farm,type)).floatValue();
                }
            }
            boolean lit=level.getRawBrightness(cropPos,0)>=12;
            return new Reading(true,"VALID".equals(wet)&&"VALID".equals(warm)&&lit,
                water,heat,wet,warm,nutrient,lit);
        }catch(ReflectiveOperationException | LinkageError | ClassCastException ex) {
            // Class/interface changes in a new TFC release must block planting, not force it.
            return UNKNOWN;
        }
    }
}
