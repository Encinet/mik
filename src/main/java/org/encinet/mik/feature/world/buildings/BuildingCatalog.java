package org.encinet.mik.feature.world.buildings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** The public catalog projected onto the data needed by the map. */
record BuildingCatalog(List<Entry> entries) {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final Pattern ID = Pattern.compile("[0-9a-fA-F]{32}");

    BuildingCatalog {
        entries = List.copyOf(entries);
    }

    static BuildingCatalog parse(String json) {
        JsonElement root = JSON.fromJson(json, JsonElement.class);
        if (root == null || !root.isJsonArray() || root.getAsJsonArray().size() > 10_000) {
            throw new IllegalArgumentException("Expected a complete building catalog array");
        }
        Set<String> ids = new HashSet<>();
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            JsonObject item = element.getAsJsonObject();
            String id = text(item, "id", 32).toLowerCase(Locale.ROOT);
            if (!ID.matcher(id).matches() || !ids.add(id)) {
                throw new IllegalArgumentException("Invalid or duplicate building ID");
            }
            JsonObject name = item.getAsJsonObject("name");
            JsonObject position = item.getAsJsonObject("coordinates");
            List<Builder> builders = new ArrayList<>();
            for (JsonElement builderElement : item.getAsJsonArray("builders")) {
                JsonObject builder = builderElement.getAsJsonObject();
                double weight = builder.get("weight").getAsDouble();
                if (!Double.isFinite(weight) || weight < 0) {
                    throw new IllegalArgumentException("Invalid builder weight");
                }
                builders.add(new Builder(text(builder, "name", 128), weight));
            }
            if (builders.isEmpty() || builders.size() > 100) {
                throw new IllegalArgumentException("Expected building authors");
            }
            builders.sort(Comparator.comparingDouble(Builder::weight).reversed().thenComparing(Builder::name));
            String type = text(item, "buildType", 16);
            if (!Set.of("original", "derivative", "replica").contains(type)) {
                throw new IllegalArgumentException("Unknown building type");
            }
            String cover = "";
            var images = item.getAsJsonArray("images");
            if (images != null && !images.isEmpty()) {
                cover = httpsImage(images.get(0).getAsString());
            }
            JsonObject source = item.has("source") && !item.get("source").isJsonNull()
                    ? item.getAsJsonObject("source") : null;
            String originalAuthor = source != null && source.has("originalAuthor")
                    && !source.get("originalAuthor").isJsonNull()
                    ? text(source, "originalAuthor", 256) : "";
            entries.add(new Entry(id, text(name, "zh-CN", 256), text(name, "en", 256),
                    text(position, "world", 256), coordinate(position, "x", 30_000_000),
                    coordinate(position, "y", 32_768), coordinate(position, "z", 30_000_000),
                    builders, type, cover, originalAuthor));
        }
        entries.sort(Comparator.comparing(Entry::id));
        return new BuildingCatalog(entries);
    }

    private static int coordinate(JsonObject item, String key, int limit) {
        JsonElement value = item.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid coordinate: " + key);
        }
        int coordinate = value.getAsBigDecimal().intValueExact();
        if (Math.abs((long) coordinate) > limit) throw new IllegalArgumentException("Coordinate out of bounds");
        return coordinate;
    }

    private static String text(JsonObject item, String key, int limit) {
        JsonElement value = item.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing text: " + key);
        }
        String text = value.getAsString().strip();
        if (text.isBlank() || text.length() > limit) throw new IllegalArgumentException("Invalid text: " + key);
        return text;
    }

    private static String httpsImage(String value) {
        try {
            URI uri = URI.create(value);
            return value.length() <= 4096 && "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null ? value : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    record Builder(String name, double weight) { }

    record Entry(String id, String nameZh, String nameEn, String world, int x, int y, int z,
                 List<Builder> builders, String type, String cover, String originalAuthor) {
        Entry {
            builders = List.copyOf(builders);
        }
    }
}
