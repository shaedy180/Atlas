package dev.atlasmod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.atlasmod.core.info.InfoPage;
import dev.atlasmod.core.info.InfoRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads info pages from resource packs. Any pack (or mod jar) can add files at
 * {@code assets/<namespace>/atlas/info/<name>.json}:
 * <pre>{@code
 * {
 *   "title": "Getting iron",
 *   "items": ["minecraft:iron_ingot", "#c:ores/iron"],
 *   "lines": ["Iron ore is rare in this pack.", "Use the ore washer from Create."]
 * }
 * }</pre>
 */
public final class InfoPageLoader {

    public static final String SOURCE = "resourcepack";
    private static final String FOLDER = "atlas/info";

    private InfoPageLoader() {}

    /** Replaces all resource pack pages. Returns how many were loaded. */
    public static int reload(InfoRegistry registry) {
        registry.clearSource(SOURCE);
        var resources = Minecraft.getInstance().getResourceManager()
                .listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        int loaded = 0;
        for (var entry : resources.entrySet()) {
            Identifier file = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                registry.register(parse(file, json, entry.getValue().sourcePackId()));
                loaded++;
            } catch (Exception e) {
                AtlasClient.LOGGER.warn("[Atlas] Skipping broken info page {}: {}", file, e.getMessage());
            }
        }
        return loaded;
    }

    static InfoPage parse(Identifier file, JsonObject json, String pack) {
        String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
        String id = file.getNamespace() + ":" + path;
        String title = json.has("title") ? json.get("title").getAsString() : ItemStacks.pretty(path);
        List<String> items = strings(json.get("items"));
        if (items.isEmpty()) throw new IllegalArgumentException("\"items\" is empty");
        List<String> lines = strings(json.get("lines"));
        return new InfoPage(id, title, lines, items, SOURCE + ":" + pack);
    }

    private static List<String> strings(JsonElement element) {
        List<String> result = new ArrayList<>();
        if (element == null) return result;
        if (element.isJsonPrimitive()) {
            result.add(element.getAsString());
        } else if (element instanceof JsonArray array) {
            array.forEach(e -> result.add(e.getAsString()));
        }
        return result;
    }
}
