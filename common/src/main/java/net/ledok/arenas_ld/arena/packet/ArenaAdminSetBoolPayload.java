package net.ledok.arenas_ld.arena.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Admin bool setting. {@code key}: lootViaInbox. */
public record ArenaAdminSetBoolPayload(BlockPos controllerPos, String key, boolean value) implements CustomPacketPayload {
    public static final Type<ArenaAdminSetBoolPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "arena_admin_set_bool"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaAdminSetBoolPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.controllerPos()); buf.writeUtf(p.key()); buf.writeBoolean(p.value()); },
        buf -> new ArenaAdminSetBoolPayload(buf.readBlockPos(), buf.readUtf(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
