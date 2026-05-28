package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetLootViaInboxPayload(BlockPos controllerPos, boolean lootViaInbox) implements CustomPacketPayload {
    public static final Type<SetLootViaInboxPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_admin_set_loot_via_inbox")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLootViaInboxPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetLootViaInboxPayload::controllerPos,
        ByteBufCodecs.BOOL, SetLootViaInboxPayload::lootViaInbox,
        SetLootViaInboxPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
