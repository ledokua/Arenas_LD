package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetTierConfigPayload(BlockPos controllerPos, DifficultyTier tier, RaidTierConfig config) implements CustomPacketPayload {
    public static final Type<RaidSetTierConfigPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_set_tier_config")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetTierConfigPayload> STREAM_CODEC = StreamCodec.of(
        RaidSetTierConfigPayload::encode,
        RaidSetTierConfigPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, RaidSetTierConfigPayload payload) {
        buf.writeBlockPos(payload.controllerPos());
        DifficultyTier.STREAM_CODEC.encode(buf, payload.tier());
        buf.writeNbt((CompoundTag) RaidTierConfig.CODEC.encodeStart(NbtOps.INSTANCE, payload.config()).getOrThrow());
    }

    private static RaidSetTierConfigPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        DifficultyTier tier = DifficultyTier.STREAM_CODEC.decode(buf);
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            throw new IllegalStateException("Missing tier config tag");
        }
        RaidTierConfig config = RaidTierConfig.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        return new RaidSetTierConfigPayload(pos, tier, config);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
