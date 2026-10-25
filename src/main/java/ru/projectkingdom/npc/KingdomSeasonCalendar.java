package ru.projectkingdom.npc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

/**
 * Read the real TerraFirmaCraft calendar, never Minecraft's 24000-tick day.
 * A missing/incompatible TFC API is UNKNOWN (-1), not day zero.
 */
public final class KingdomSeasonCalendar {
    private KingdomSeasonCalendar() {}
    private static boolean probed;
    private static Field serverCalendar;
    private static Method totalDays;
    private static Method monthDays;

    private static boolean init(MinecraftServer server) {
        if(server==null || !ModList.get().isLoaded("tfc"))return false;
        if (!probed) {
            probed = true;
            try {
                Class<?> calendars = Class.forName("net.dries007.tfc.util.calendar.Calendars");
                serverCalendar = calendars.getField("SERVER");
                Class<?> calendar = Class.forName("net.dries007.tfc.util.calendar.ICalendar");
                totalDays = calendar.getMethod("getTotalCalendarDays");
                monthDays = calendar.getMethod("getCalendarDaysInMonth");
            } catch (ReflectiveOperationException | LinkageError e) {
                System.err.println("[Kingdom] TFC calendar unavailable; food forecasts disabled: " + e);
                serverCalendar = null;
                totalDays = null;
                monthDays = null;
            }
        }
        return serverCalendar != null && totalDays != null && monthDays != null;
    }

    /** The TFC calendar tick domain, NOT monotonic player ticks. */
    public static long totalDays(MinecraftServer server) {
        if(!init(server))return -1;
        try {
            Object cal=serverCalendar.get(null);
            return cal==null ? -1 : Math.max(-1,((Number)totalDays.invoke(cal)).longValue());
        }catch(ReflectiveOperationException | RuntimeException e){return -1;}
    }

    /** Real configurable number of days in each TFC month. */
    public static int daysInMonth(MinecraftServer server) {
        if(!init(server))return -1;
        try {
            Object cal=serverCalendar.get(null);
            int days=cal==null ? -1 : ((Number)monthDays.invoke(cal)).intValue();
            return days>=1 && days<=1000 ? days : -1;
        }catch(ReflectiveOperationException | RuntimeException e){return -1;}
    }
}
