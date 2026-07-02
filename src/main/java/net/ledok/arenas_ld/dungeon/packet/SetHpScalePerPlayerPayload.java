package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Admin setting: per-player mob HP scale factor for a dungeon controller. Mob HP is multiplied by
 * {@code players * scale}; a scale of 0 disables the scaling entirely.
 */
public record SetHpScalePerPlayerPayload(BlockPos controllerPos, double scale) implements CustomPacketPayload {
    public static final Type<SetHpScalePerPlayerPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_set_hp_scale_per_player")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetHpScalePerPlayerPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetHpScalePerPlayerPayload::controllerPos,
        ByteBufCodecs.DOUBLE, SetHpScalePerPlayerPayload::scale,
        SetHpScalePerPlayerPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
