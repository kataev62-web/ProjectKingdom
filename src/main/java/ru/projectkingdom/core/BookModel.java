package ru.projectkingdom.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Stable table of contents for the permanent player Book. */
public final class BookModel {
    public enum Status { ACTIVE, PLACEHOLDER }
    public enum Section {
        PLAYER("Игрок"), BANK("Банк"), CAPITAL("Капитал"), STATE("Государство"), DYNASTY("Династия"), WORLD("Мир");
        private final String title;
        Section(String title) { this.title = title; }
        public String title() { return title; }
    }
    public record Entry(Section section, Status status, String summary) {
        public Entry { Objects.requireNonNull(section); Objects.requireNonNull(status); Objects.requireNonNull(summary); }
    }
    public record Finance(long bankBalance, long totalCapital) {
        public Finance {
            if (bankBalance < 0 || totalCapital < bankBalance) throw new IllegalArgumentException("invalid finance");
        }
    }

    private BookModel() {}

    public static List<Entry> contents(PlayerIdentity player, Finance finance) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(finance, "finance");
        return Arrays.asList(
                new Entry(Section.PLAYER, Status.ACTIVE, player.characterId().value().toString()),
                new Entry(Section.BANK, Status.ACTIVE, Long.toString(finance.bankBalance())),
                new Entry(Section.CAPITAL, Status.ACTIVE, Long.toString(finance.totalCapital())),
                new Entry(Section.STATE, Status.PLACEHOLDER, "Будет подключено на следующем этапе"),
                new Entry(Section.DYNASTY, Status.PLACEHOLDER, "Будет подключено на следующем этапе"),
                new Entry(Section.WORLD, Status.PLACEHOLDER, "Будет подключено на следующем этапе"));
    }
}
