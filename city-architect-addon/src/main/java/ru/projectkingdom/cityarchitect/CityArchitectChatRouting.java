package ru.projectkingdom.cityarchitect;

import ru.projectkingdom.cityarchitect.command.CityArchitectCommandParser;

public final class CityArchitectChatRouting {
    private CityArchitectChatRouting() {
    }

    public static Route route(String raw) {
        return CityArchitectCommandParser.parse(raw).isPresent()
                ? Route.CITY_ARCHITECT
                : Route.PASS_THROUGH;
    }

    public enum Route { CITY_ARCHITECT, PASS_THROUGH }
}
