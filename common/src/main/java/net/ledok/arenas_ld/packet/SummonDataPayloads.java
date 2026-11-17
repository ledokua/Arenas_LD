package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The summon "Extra data" editor (4.30.0): saves a dungeon mob spawner, dungeon boss spawner or raid boss spawner's
 * extra mob data (SNBT) and split strength. The editor reads the current values from the spawner's synced block
 * entity; arena spawners edit theirs in their own mob list.
 */
public final class SummonDataPayloads {
    private SummonDataPayloads() {
    }

    /** Client → server: save. */
    public record Set(BlockPos pos, String nbt, int splitStrength) implements CustomPacketPayload {
        public static final Type<Set> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "summon_data_set"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Set> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Set::pos,
            ByteBufCodecs.stringUtf8(8192), Set::nbt,
            ByteBufCodecs.VAR_INT, Set::splitStrength,
            Set::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
