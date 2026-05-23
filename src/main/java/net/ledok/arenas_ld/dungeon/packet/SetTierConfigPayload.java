package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetTierConfigPayload(BlockPos controllerPos, DifficultyTier tier, TierConfig config) implements CustomPacketPayload {
    public static final Type<SetTierConfigPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_set_tier_config")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTierConfigPayload> STREAM_CODEC = StreamCodec.of(
        SetTierConfigPayload::encode,
        SetTierConfigPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, SetTierConfigPayload payload) {
        buf.writeBlockPos(payload.controllerPos());
        DifficultyTier.STREAM_CODEC.encode(buf, payload.tier());
        buf.writeNbt((CompoundTag) TierConfig.CODEC.encodeStart(NbtOps.INSTANCE, payload.config()).getOrThrow());
    }

    private static SetTierConfigPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        DifficultyTier tier = DifficultyTier.STREAM_CODEC.decode(buf);
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            throw new IllegalStateException("Missing tier config tag");
        }
        TierConfig config = TierConfig.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        return new SetTierConfigPayload(pos, tier, config);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
