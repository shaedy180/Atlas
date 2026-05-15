package dev.atlasmod.fabric.payload;

import dev.atlasmod.core.registry.AtlasRegistrySnapshot;
import dev.atlasmod.fabric.AtlasSnapshotSerialization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AtlasSnapshotPayload(AtlasRegistrySnapshot snapshot) implements CustomPacketPayload {

    public static final Type<AtlasSnapshotPayload> TYPE = CustomPacketPayload.createType("atlas:snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasSnapshotPayload> CODEC =
            CustomPacketPayload.codec(AtlasSnapshotPayload::write, AtlasSnapshotPayload::new);

    public AtlasSnapshotPayload(RegistryFriendlyByteBuf buf) {
        this(AtlasSnapshotSerialization.readSnapshot(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        AtlasSnapshotSerialization.writeSnapshot(buf, snapshot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
