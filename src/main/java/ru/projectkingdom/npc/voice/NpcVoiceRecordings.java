package ru.projectkingdom.npc.voice;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class NpcVoiceRecordings {
    private static final Properties DURATIONS = new Properties();

    private NpcVoiceRecordings() {
    }

    public static String soundId(NpcVoiceBank bank, NpcVoiceLine line) {
        return "voice." + bank.id() + "." + line.id();
    }

    public static int durationTicks(NpcVoiceBank bank, NpcVoiceLine line) {
        try {
            int ms = Integer.parseInt(DURATIONS.getProperty(bank.id() + "." + line.id(), "0"));
            if (ms < 250 || ms > 10000) {
                return 100;
            }
            return Math.max(100, Math.min(240, (ms + 49) / 50 + 30));
        }
        catch (NumberFormatException ignored) {
            return 100;
        }
    }

    static {
        try (InputStream stream = NpcVoiceRecordings.class.getResourceAsStream("/assets/kingdomnpc/voice/recordings.properties");){
            if (stream != null) {
                DURATIONS.load(stream);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }
}

