package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.util.RunSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin screen → server: a dungeon or raid controller's run settings (rental mode, base loot rolls). */
public record SetRunSettingsPayload(BlockPos controllerPos, RunSettings settings) implements CustomPacketPayload {
    public static final Type<SetRunSettingsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "set_run_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetRunSettingsPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetRunSettingsPayload::controllerPos,
        RunSettings.STREAM_CODEC, SetRunSettingsPayload::settings,
        SetRunSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
