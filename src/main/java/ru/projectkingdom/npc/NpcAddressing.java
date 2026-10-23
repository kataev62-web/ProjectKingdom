package ru.projectkingdom.npc;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Pure parser for nearby, explicitly addressed NPC chat. */
public final class NpcAddressing {
    private NpcAddressing() {}

    public record Candidate(int npcId, String name, double distanceSquared, boolean available) {}
    public record Match(int npcId, String phrase) {}

    public static Optional<Match> match(String raw, List<Candidate> candidates) {
        if (raw == null || candidates == null) return Optional.empty();
        String text = raw.trim();
        if (text.isEmpty()) return Optional.empty();

        Optional<Match> numeric = candidates.stream()
                .filter(Candidate::available)
                .filter(candidate -> numericPhrase(text, candidate.npcId()) != null)
                .min(Comparator.comparingDouble(Candidate::distanceSquared))
                .map(candidate -> new Match(candidate.npcId(), numericPhrase(text, candidate.npcId())));
        if (numeric.isPresent()) return numeric;

        String lower = text.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(Candidate::available)
                .filter(candidate -> namedPhrase(text, lower, candidate.name()) != null)
                .min(Comparator.comparingDouble(Candidate::distanceSquared))
                .map(candidate -> new Match(candidate.npcId(), namedPhrase(text, lower, candidate.name())));
    }

    private static String numericPhrase(String text, int id) {
        String prefix = "#" + id;
        if (!text.startsWith(prefix) || text.length() == prefix.length()) return null;
        char separator = text.charAt(prefix.length());
        if (!Character.isWhitespace(separator) && separator != ':' && separator != ',') return null;
        String phrase = text.substring(prefix.length() + 1).trim();
        return phrase.isEmpty() ? null : phrase;
    }

    private static String namedPhrase(String original, String lower, String name) {
        if (name == null || name.isBlank()) return null;
        String prefix = name.trim().toLowerCase(Locale.ROOT);
        if (!lower.startsWith(prefix) || original.length() <= prefix.length()) return null;
        char separator = original.charAt(prefix.length());
        if (separator != ':' && separator != ',') return null;
        String phrase = original.substring(prefix.length() + 1).trim();
        return phrase.isEmpty() ? null : phrase;
    }
}

