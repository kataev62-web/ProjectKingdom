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

    public static long totalDays(MinecraftServer server) {
        if (server == null || !ModList.get().isLoaded("tfc")) return -1;
        if (!probed) {
            probed = true;
            try {
                Class<?> calendars = Class.forName("net.dries007.tfc.util.calendar.Calendars");
                serverCalendar = calendars.getField("SERVER");
                Class<?> calendar = Class.forName("net.dries007.tfc.util.calendar.ICalendar");
                totalDays = calendar.getMethod("getTotalDays");
            } catch (ReflectiveOperationException | LinkageError e) {
                System.err.println("[Kingdom] TFC calendar unavailable; food forecasts disabled: " + e);
                serverCalendar = null;
                totalDays = null;
            }
        }
        if (serverCalendar == null || totalDays == null) return -1;
        try {
            Object calendar = serverCalendar.get(null);
            return calendar == null ? -1 : Math.max(-1,((Number)totalDays.invoke(calendar)).longValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}
