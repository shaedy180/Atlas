package dev.atlasmod.fabric;

import dev.atlasmod.core.availability.ContextSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds a {@link ContextSnapshot} from the current client player state.
 * Used by the Availability Engine to evaluate unlock conditions against
 * the player's actual position, inventory, dimension, and surroundings.
 *
 * Scans nearby blocks within a configurable radius. The radius is kept
 * modest to avoid lag on each evaluation cycle.
 */
public final class ContextSnapshotBuilder {

    private static final int NEARBY_BLOCK_RADIUS = 5;
    private static long cachedGameTime = Long.MIN_VALUE;
    private static ContextSnapshot cachedSnapshot = ContextSnapshot.empty();

    private ContextSnapshotBuilder() {}

    /**
     * Captures the current state of the local player. Returns an empty snapshot
     * if the player or level is not available (e.g. during loading screens).
     */
    public static ContextSnapshot capture() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        if (player == null || level == null) {
            cachedGameTime = Long.MIN_VALUE;
            cachedSnapshot = ContextSnapshot.empty();
            return ContextSnapshot.empty();
        }

        long gameTime = level.getGameTime();
        if (cachedGameTime == gameTime) {
            return cachedSnapshot;
        }

        String dimensionId = level.dimension().identifier().toString();
        String biomeId = level.getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("");

        Set<String> inventoryItemIds = collectInventoryItems(player);
        Set<String> nearbyBlockIds = collectNearbyBlocks(level, player.blockPosition());

        // Advancements are server-side; the client has limited visibility.
        // For singleplayer, we could query the integrated server, but for
        // multiplayer the client doesn't have this data reliably.
        // Leave empty for now; a future network packet could sync this.
        Set<String> advancements = Set.of();

        cachedGameTime = gameTime;
        cachedSnapshot = new ContextSnapshot(dimensionId, biomeId, advancements, inventoryItemIds, nearbyBlockIds, false);
        return cachedSnapshot;
    }

    public static void invalidateCache() {
        cachedGameTime = Long.MIN_VALUE;
        cachedSnapshot = ContextSnapshot.empty();
    }

    private static Set<String> collectInventoryItems(LocalPlayer player) {
        Set<String> items = new HashSet<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            var key = stack.typeHolder().unwrapKey().orElse(null);
            if (key != null) {
                items.add(key.identifier().toString());
            }
        }
        return items;
    }

    private static Set<String> collectNearbyBlocks(ClientLevel level, BlockPos center) {
        Set<String> blocks = new HashSet<>();
        int r = NEARBY_BLOCK_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();
                    Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                    if (id != null) {
                        blocks.add(id.toString());
                    }
                }
            }
        }
        return blocks;
    }
}
