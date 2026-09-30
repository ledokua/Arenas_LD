package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: dismiss the loot-reveal popup and anything queued behind it. Sent when the party enters
 * a new room, so a lingering bonus-room reward never clutters the screen once the next fight is
 * starting.
 */
public record LootHudClearPayload() implements CustomPacketPayload {
    public static final LootHudClearPayload INSTANCE = new LootHudClearPayload();

    public static final Type<LootHudClearPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "loot_hud_clear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LootHudClearPayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
