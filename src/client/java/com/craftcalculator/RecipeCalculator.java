package com.craftcalculator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core recipe calculation logic.
 *
 * Recipes are loaded from a bundled JSON index (generated from the vanilla
 * crafting recipes) instead of the live server RecipeManager. This is what
 * makes the mod work identically in singleplayer AND multiplayer: since the
 * 1.21.2 recipe rework the client no longer receives the full crafting recipe
 * list, so we ship our own copy.
 *
 * Bundled file: assets/craft_calculator/recipes.json
 * Format: [ { "r": "<result id>", "c": <count>, "i": [ ["<opt>", ...], ... ] }, ... ]
 */
public final class RecipeCalculator {

    /** Result of a calculation. */
    public static final class Requirements {
        public final Map<String, Long> craftItems   = new HashMap<>();
        public final Map<String, Long> rawMaterials = new HashMap<>();
    }

    /** Lightweight bundled recipe: how many the recipe yields, plus one entry per ingredient slot. */
    public record BundledRecipe(int count, List<List<Item>> slots) {}

    private static final String RESOURCE_PATH = "/assets/craft_calculator/recipes.json";

    // Lazily-loaded index of Item -> recipe that produces it (built once, cached).
    private static volatile Map<Item, BundledRecipe> CACHED_INDEX = null;

    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Returns the recipe index (Item -> first crafting recipe that produces it),
     * loading and parsing the bundled JSON on first use.
     */
    public static Map<Item, BundledRecipe> buildRecipeIndex() {
        Map<Item, BundledRecipe> idx = CACHED_INDEX;
        if (idx != null) return idx;
        synchronized (RecipeCalculator.class) {
            if (CACHED_INDEX != null) return CACHED_INDEX;
            CACHED_INDEX = loadFromResource();
            return CACHED_INDEX;
        }
    }

    private static Map<Item, BundledRecipe> loadFromResource() {
        Map<Item, BundledRecipe> index = new HashMap<>();
        try (InputStream in = RecipeCalculator.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                System.err.println("[CraftCalculator] Bundled recipe index not found: " + RESOURCE_PATH);
                return index;
            }
            JsonArray arr = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();

            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                Item result = resolveItem(o.get("r").getAsString());
                if (result == null) continue;

                int count = o.has("c") ? o.get("c").getAsInt() : 1;

                List<List<Item>> slots = new ArrayList<>();
                for (JsonElement slotEl : o.getAsJsonArray("i")) {
                    List<Item> options = new ArrayList<>();
                    for (JsonElement optEl : slotEl.getAsJsonArray()) {
                        Item it = resolveItem(optEl.getAsString());
                        if (it != null) options.add(it);
                    }
                    if (!options.isEmpty()) slots.add(options);
                }
                if (slots.isEmpty()) continue;

                index.putIfAbsent(result, new BundledRecipe(Math.max(1, count), slots));
            }
        } catch (Exception e) {
            System.err.println("[CraftCalculator] Failed to load bundled recipes: " + e);
        }
        return index;
    }

    /**
     * Recursively collects raw materials and craftable sub-items for {@code targetKey}.
     */
    public static boolean collectRequirements(
            String targetKey, long need,
            Map<Item, BundledRecipe> index, Requirements out,
            Set<String> guard, boolean isRoot) {

        if (WoodGroups.isSentinel(targetKey)) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return true;
        }

        Item item = resolveItem(targetKey);
        if (item == null) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return false;
        }

        if (BaseMaterials.ALL.contains(item)) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return true;
        }

        BundledRecipe recipe = index.get(item);
        if (recipe == null) {
            out.rawMaterials.merge(targetKey, need, Long::sum);
            return false;
        }

        if (!isRoot) {
            // Non-root craftable items are shown as-is (e.g. "Bow" needed for a Dispenser)
            out.craftItems.merge(targetKey, need, Long::sum);
            return true;
        }

        if (!guard.add(targetKey)) return true; // cycle guard

        int  produced = Math.max(1, recipe.count());
        long crafts   = (long) Math.ceil((double) need / produced);

        for (List<Item> options : recipe.slots()) {
            if (options.isEmpty()) continue;

            if (options.size() > 1) {
                String groupKey = WoodGroups.classifyGroup(options);
                if (groupKey != null) {
                    out.rawMaterials.merge(groupKey, crafts, Long::sum);
                } else {
                    collectRequirements(itemKey(options.get(0)), crafts, index, out, guard, false);
                }
            } else {
                collectRequirements(itemKey(options.get(0)), crafts, index, out, guard, false);
            }
        }

        guard.remove(targetKey);
        return true;
    }

    // ─── Item key / resolve helpers ───────────────────────────────────────────────

    public static String itemKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static Item resolveItem(String key) {
        Identifier id = Identifier.tryParse(key);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return (item == null || item == Items.AIR) ? null : item;
    }

    private RecipeCalculator() {}
}
