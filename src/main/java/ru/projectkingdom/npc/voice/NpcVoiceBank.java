package ru.projectkingdom.npc.voice;

import java.util.UUID;

public enum NpcVoiceBank {
    MALE_1("male_1"),
    MALE_2("male_2"),
    MALE_3("male_3"),
    FEMALE_1("female_1"),
    FEMALE_2("female_2"),
    FEMALE_3("female_3");

    private final String id;

    private NpcVoiceBank(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static NpcVoiceBank forNpc(UUID workerId, String gender) {
        if (workerId == null) {
            throw new IllegalArgumentException("NPC identity is required");
        }
        int index = Math.floorMod(Long.hashCode(workerId.getMostSignificantBits() ^ Long.rotateLeft(workerId.getLeastSignificantBits(), 17)), 3);
        return NpcVoiceBank.values()[index + ("female".equalsIgnoreCase(gender) ? 3 : 0)];
    }

    public static NpcVoiceBank fromId(String id) {
        for (NpcVoiceBank bank : NpcVoiceBank.values()) {
            if (!bank.id.equals(id)) continue;
            return bank;
        }
        throw new IllegalArgumentException("Unknown NPC voice bank");
    }
}

