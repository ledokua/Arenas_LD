package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetDungeonNamePayload(BlockPos controllerPos, String name) implements CustomPacketPayload {
    public static final Type<SetDungeonNamePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_set_dungeon_name")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetDungeonNamePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetDungeonNamePayload::controllerPos,
        ByteBufCodecs.STRING_UTF8, SetDungeonNamePayload::name,
        SetDungeonNamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
