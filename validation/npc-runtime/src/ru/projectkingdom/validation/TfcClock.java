package ru.projectkingdom.validation;

import java.lang.reflect.Method;
import net.minecraft.server.MinecraftServer;

/** Explicit test-clock control through TFC; direct vanilla clock rewinds are rejected by TFC. */
final class TfcClock {
    private static Object calendar;
    private static Method ticks, setTime;
    static void set(MinecraftServer server, long desired) {
        try {
            if (calendar == null) {
                Class<?> type=Class.forName("net.dries007.tfc.util.calendar.Calendars",true,server.getClass().getClassLoader());
                calendar=type.getField("SERVER").get(null);
                ticks=calendar.getClass().getMethod("getCalendarTicks");
                setTime=calendar.getClass().getMethod("setTimeFromCalendarTime",long.class);
            }
            long delta=desired-server.overworld().getDayTime();
            if(delta!=0) setTime.invoke(calendar,((Number)ticks.invoke(calendar)).longValue()+delta);
        } catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot control the real TFC test calendar",failure);
        }
    }
}
