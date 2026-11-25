package ru.projectkingdom.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The schedule of the day of a resident.
 *
 * <p>The schedule decides who owns the resident: the Kingdom during the shift
 * and the rest, the ordinary life of MCA in the morning and in the evening.
 * Danger always wins over both.
 */
final class KingdomResidentScheduleTest {

    private static long at(int hour) {
        return KingdomResidentSchedule.tickOfHour(hour);
    }

    @Test void theHoursOfTheDayMapToTheTicksOfMinecraft() {
        assertEquals(0, at(6), "the day of Minecraft begins at six");
        assertEquals(2000, at(8));
        assertEquals(6000, at(12));
        assertEquals(7000, at(13));
        assertEquals(12000, at(18));
        assertEquals(0, at(30), "the hours wrap around");
        assertEquals(6, KingdomResidentSchedule.hourOfTick(at(6)));
        assertEquals(18, KingdomResidentSchedule.hourOfTick(at(18)));
    }

    @Test void theMorningAndTheEveningBelongToMca() {
        assertEquals(KingdomResidentSchedule.Mode.MCA_LIFE,
            KingdomResidentSchedule.modeOf(at(7), true, false, false),
            "06:00-08:00 is the life of MCA");
        assertEquals(KingdomResidentSchedule.Mode.MCA_LIFE,
            KingdomResidentSchedule.modeOf(at(20), true, false, false),
            "18:00-06:00 is the life of MCA, the sleep included");
        assertEquals(KingdomResidentSchedule.Mode.MCA_LIFE,
            KingdomResidentSchedule.modeOf(at(3), true, false, false),
            "the night belongs to MCA");
    }

    @Test void theShiftOfTheKingdomIsTheMorningAndTheAfternoon() {
        assertEquals(KingdomResidentSchedule.Mode.KINGDOM_WORK,
            KingdomResidentSchedule.modeOf(at(9), true, false, false), "08:00-12:00 is the work");
        assertEquals(KingdomResidentSchedule.Mode.KINGDOM_WORK,
            KingdomResidentSchedule.modeOf(at(15), true, false, false), "13:00-18:00 is the work");
        assertEquals(KingdomResidentSchedule.Mode.KINGDOM_REST,
            KingdomResidentSchedule.modeOf(at(12), true, false, false), "12:00-13:00 is the rest");
    }

    @Test void aResidentWithoutAContractNeverWorks() {
        assertEquals(KingdomResidentSchedule.Mode.MCA_LIFE,
            KingdomResidentSchedule.modeOf(at(9), false, false, false),
            "an idle resident lives its life of MCA");
        assertEquals(KingdomResidentSchedule.Mode.MCA_LIFE,
            KingdomResidentSchedule.modeOf(at(12), false, false, false),
            "the rest of the Kingdom belongs to the employed ones");
    }

    @Test void theOrderOfThePlannerAndTheDangerWinOverTheSchedule() {
        assertEquals(KingdomResidentSchedule.Mode.PLAYER2_TASK,
            KingdomResidentSchedule.modeOf(at(9), true, true, false),
            "the complex order replaces the ordinary work");
        assertEquals(KingdomResidentSchedule.Mode.EMERGENCY,
            KingdomResidentSchedule.modeOf(at(9), true, true, true),
            "danger wins over everything");
        assertEquals(KingdomResidentSchedule.Mode.EMERGENCY,
            KingdomResidentSchedule.modeOf(at(20), true, false, true),
            "the resident may run away at any hour");
    }

    @Test void theOwnershipOfTheNavigationFollowsTheMode() {
        assertTrue(KingdomResidentSchedule.kingdomOwns(KingdomResidentSchedule.Mode.KINGDOM_WORK));
        assertFalse(KingdomResidentSchedule.kingdomOwns(KingdomResidentSchedule.Mode.KINGDOM_REST));
        assertTrue(KingdomResidentSchedule.kingdomOwns(KingdomResidentSchedule.Mode.PLAYER2_TASK));
        assertFalse(KingdomResidentSchedule.kingdomOwns(KingdomResidentSchedule.Mode.MCA_LIFE),
            "the life of MCA owns the resident");
        assertFalse(KingdomResidentSchedule.kingdomOwns(KingdomResidentSchedule.Mode.EMERGENCY),
            "a resident in danger may run away");
        assertTrue(KingdomResidentSchedule.mcaLife(KingdomResidentSchedule.Mode.EMERGENCY));
    }

    @Test void theWorkingHoursAreRecognised() {
        assertTrue(KingdomResidentSchedule.working(KingdomResidentSchedule.Mode.KINGDOM_WORK));
        assertFalse(KingdomResidentSchedule.working(KingdomResidentSchedule.Mode.KINGDOM_REST));
        assertFalse(KingdomResidentSchedule.working(KingdomResidentSchedule.Mode.MCA_LIFE));
    }
    @Test void plannerCannotBypassRestOrNightOrEmployment() {
        assertEquals(KingdomResidentSchedule.Mode.KINGDOM_REST,
            KingdomResidentSchedule.modeOf(at(12), true, true, false));
        assertEquals(KingdomResidentSchedule.Mode.MCA_LIFE,
            KingdomResidentSchedule.modeOf(at(20), true, true, false));
        assertEquals(KingdomResidentSchedule.Mode.MCA_LIFE,
            KingdomResidentSchedule.modeOf(at(9), false, true, false));
    }

    @Test void workWindowsRepeatAcrossRestartAndNegativeDayTime() {
        for (int hour=0; hour<24; hour++) {
            boolean working = hour>=8 && hour<12 || hour>=13 && hour<18;
            for (long offset : new long[]{-24000, 0, 24000, 2400000}) {
                var mode = KingdomResidentSchedule.modeOf(at(hour)+offset, true, false, false);
                assertEquals(working, KingdomResidentSchedule.working(mode), "hour="+hour);
            }
        }
    }
    @Test void independentCropWorkRetainsShiftWithoutInventingContract() {
        assertTrue(KingdomResidentSchedule.participatesInShift(true,true,true,false));
        assertFalse(KingdomResidentSchedule.participatesInShift(true,true,false,false));
        assertFalse(KingdomResidentSchedule.participatesInShift(false,true,true,false));
        assertFalse(KingdomResidentSchedule.participatesInShift(true,false,true,false));
    }
}
