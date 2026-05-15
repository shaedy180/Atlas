package dev.atlasmod.fabric.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AtlasReloadRequestPayload() implements CustomPacketPayload {

    public static final AtlasReloadRequestPayload INSTANCE = new AtlasReloadRequestPayload();
    public static final Type<AtlasReloadRequestPayload> TYPE = CustomPacketPayload.createType("atlas:reload_request");
    public static final StreamCodec<FriendlyByteBuf, AtlasReloadRequestPayload> CODEC =
            CustomPacketPayload.codec((buf, payload) -> { }, buf -> INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
