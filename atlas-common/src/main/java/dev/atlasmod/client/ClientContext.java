package dev.atlasmod.client;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.client.compat.McCompat;
import dev.atlasmod.core.availability.ContextSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects what the Availability Engine needs to know about the player.
 * Client side data (inventory, nearby blocks, biome) is read directly. Progress
 * data (advancements, recipe book) comes from the integrated server and is
 * gathered on the server thread to avoid racing it.
 */
public final class ClientContext {

    private static final int SCAN_RADIUS = 5;
    private static final int SCAN_HEIGHT = 3;
    private static final long REFRESH_MS = 1000;

    private ContextSnapshot snapshot = ContextSnapshot.empty();
    private long lastRefresh;

    // Written by the server thread, read by the render thread
    private volatile Set<String> advancements = Set.of();
    private volatile Set<String> lockedRecipes = Set.of();
    private volatile boolean limitedCrafting;
    private volatile Map<String, List<String>> unlockedBy = Map.of();
    private volatile Map<String, String> advancementTitles = Map.of();
    private volatile boolean progressKnown;

    /** Latest snapshot, refreshed at most once per second. */
    public ContextSnapshot snapshot() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh >= REFRESH_MS) {
            lastRefresh = now;
            refresh();
        }
        return snapshot;
    }

    /** Forces the next {@link #snapshot()} call to read fresh data. */
    public void invalidate() {
        lastRefresh = 0;
    }

    public void clear() {
        snapshot = ContextSnapshot.empty();
        advancements = Set.of();
        lockedRecipes = Set.of();
        unlockedBy = Map.of();
        advancementTitles = Map.of();
        progressKnown = false;
        lastRefresh = 0;
    }

    /** Advancements that grant the recipe, by id. */
    public List<String> advancementsUnlocking(String recipeId) {
        return unlockedBy.getOrDefault(baseRecipeId(recipeId), List.of());
    }

    public String advancementTitle(String id) {
        return advancementTitles.getOrDefault(id, ItemStacks.pretty(id));
    }

    public boolean isRecipeLocked(String recipeId) {
        return lockedRecipes.contains(baseRecipeId(recipeId));
    }

    /** False in multiplayer, where Atlas can not see the recipe book or advancements. */
    public boolean progressKnown() {
        return progressKnown;
    }

    public Map<String, Integer> inventoryCounts() {
        return snapshot().inventory();
    }

    private void refresh() {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            snapshot = ContextSnapshot.empty();
            return;
        }

        Map<String, Integer> inventory = new HashMap<>();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            var key = ItemStacks.entryOf(stack);
            if (key != null) inventory.merge(key.id(), stack.getCount(), Integer::sum);
        }

        Set<String> nearby = new HashSet<>();
        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_HEIGHT; dy <= SCAN_HEIGHT; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    var state = level.getBlockState(pos);
                    if (!state.isAir()) nearby.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                }
            }
        }

        var biome = level.getBiome(center);
        String biomeId = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("");
        Set<String> biomeTags = new HashSet<>();
        biome.tags().forEach(tag -> biomeTags.add(tag.location().toString()));

        Set<String> stages = AtlasApi.isReady() ? AtlasApi.get().stages() : Set.of();
        snapshot = new ContextSnapshot(level.dimension().identifier().toString(), biomeId, biomeTags,
                advancements, inventory, nearby, lockedRecipes, limitedCrafting, stages);

        requestProgress(mc);
    }

    private void requestProgress(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            progressKnown = false;
            return;
        }
        var uuid = mc.player.getUUID();
        List<String> recipeIds = List.copyOf(VanillaRecipeLoader.gameRecipeIds());
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return;
            try {
                Set<String> done = new HashSet<>();
                Map<String, List<String>> grants = new HashMap<>();
                Map<String, String> titles = new HashMap<>();
                for (var holder : server.getAdvancements().getAllAdvancements()) {
                    String id = holder.id().toString();
                    if (player.getAdvancements().getOrStartProgress(holder).isDone()) done.add(id);
                    holder.value().display().ifPresent(d -> titles.put(id, McCompat.advancementTitle(d)));
                    for (var recipe : holder.value().rewards().recipes()) {
                        grants.computeIfAbsent(recipe.identifier().toString(), k -> new ArrayList<>()).add(id);
                    }
                }
                Set<String> locked = new HashSet<>();
                var book = player.getRecipeBook();
                for (String id : recipeIds) {
                    Identifier parsed = Identifier.tryParse(id);
                    if (parsed != null && !book.contains(ResourceKey.create(Registries.RECIPE, parsed))) locked.add(id);
                }
                advancements = Set.copyOf(done);
                unlockedBy = Map.copyOf(grants);
                advancementTitles = Map.copyOf(titles);
                lockedRecipes = Set.copyOf(locked);
                limitedCrafting = server.getGameRules().get(GameRules.LIMITED_CRAFTING);
                progressKnown = true;
            } catch (RuntimeException e) {
                AtlasClient.LOGGER.debug("[Atlas] Could not read progress data", e);
            }
        });
    }

    /** Recipes with several displays are stored as "id#0", "id#1". */
    static String baseRecipeId(String nodeId) {
        int hash = nodeId.indexOf('#');
        return hash < 0 ? nodeId : nodeId.substring(0, hash);
    }
}
