package org.encinet.mik.module.presentation.motd.holiday;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class HolidayCnParser {

    private HolidayCnParser() {}

    static List<ChinaHolidayEvent> parseYearResponse(String json) {
        List<ChinaHolidayEvent> parsed = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonElement daysElement = root.get("days");
        if (daysElement == null || !daysElement.isJsonArray()) {
            return parsed;
        }

        for (JsonElement dayElement : daysElement.getAsJsonArray()) {
            if (!dayElement.isJsonObject()) continue;
            JsonObject day = dayElement.getAsJsonObject();
            if (!getBoolean(day, "isOffDay")) continue;

            String name = getString(day, "name");
            String date = getString(day, "date");
            if (name == null || date == null) continue;

            LocalDate start = LocalDate.parse(date);
            parsed.add(new ChinaHolidayEvent(start, start.plusDays(1), name));
        }
        return parsed;
    }

    private static boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return null;
        String value = element.getAsString();
        return value == null || value.isBlank() ? null : value;
    }
}
