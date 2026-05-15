package dev.atlasmod.fabric;

import dev.atlasmod.fabric.payload.AtlasReloadRequestPayload;
import dev.atlasmod.fabric.payload.AtlasSnapshotPayload;
import dev.atlasmod.core.registry.AtlasRegistrySnapshot;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class AtlasNetworking {

    private AtlasNetworking() {
    }

    public static void register() {
        registerPayloadTypes();

        ServerPlayNetworking.registerGlobalReceiver(AtlasReloadRequestPayload.TYPE, (payload, context) -> {
            AtlasRuntimeController.rebuildServerSnapshot(context.server());
            AtlasRuntimeController.syncSnapshotToAll(context.server());
        });
    }

    public static void registerClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                AtlasSnapshotPayload.TYPE,
                (payload, context) -> AtlasRuntimeController.applyClientSnapshot(payload.snapshot())
        );
    }

    public static void sendSnapshot(ServerPlayer player, AtlasRegistrySnapshot snapshot) {
        if (ServerPlayNetworking.canSend(player, AtlasSnapshotPayload.TYPE)) {
            ServerPlayNetworking.send(player, new AtlasSnapshotPayload(snapshot));
        }
    }

    public static void requestReload() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(AtlasReloadRequestPayload.INSTANCE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerPayloadTypes() {
        try {
            Object s2c = PayloadTypeRegistry.class.getMethod("playS2C").invoke(null);
            s2c.getClass()
                    .getMethod("registerLarge", CustomPacketPayload.Type.class, StreamCodec.class, int.class)
                    .invoke(s2c, AtlasSnapshotPayload.TYPE, AtlasSnapshotPayload.CODEC, 4 * 1024 * 1024);

            Object c2s = PayloadTypeRegistry.class.getMethod("playC2S").invoke(null);
            c2s.getClass()
                    .getMethod("register", CustomPacketPayload.Type.class, StreamCodec.class)
                    .invoke(c2s, AtlasReloadRequestPayload.TYPE, AtlasReloadRequestPayload.CODEC);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register Atlas payload codecs", e);
        }
    }
}
