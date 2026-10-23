package ru.projectkingdom.npc;

/** Immutable, bounded memory of one player's relationship with one NPC. */
public record NpcSocialMemory(int reputation, int conversations, int helped,
                              String lastTopic, long lastDay) {
    public NpcSocialMemory {
        reputation = Math.max(-100, Math.min(100, reputation));
        conversations = Math.max(0, conversations);
        helped = Math.max(0, helped);
        lastTopic = lastTopic == null || lastTopic.isBlank() ? "NONE" : lastTopic;
        lastDay = Math.max(0, lastDay);
    }

    public static NpcSocialMemory neutral() {
        return new NpcSocialMemory(0, 0, 0, "NONE", 0);
    }

    public NpcSocialMemory talked(String topic, long day) {
        return new NpcSocialMemory(reputation, increment(conversations), helped, topic, day);
    }

    public NpcSocialMemory helped(long day) {
        return new NpcSocialMemory(Math.min(100, reputation + 5), conversations,
                increment(helped), "HELP", day);
    }

    public String label() {
        if (reputation <= -50) return "враждебность";
        if (reputation < 0) return "недоверие";
        if (reputation < 20) return "незнакомец";
        if (reputation < 50) return "знакомый";
        if (reputation < 80) return "друг";
        return "союзник";
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }
}

