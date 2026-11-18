package net.ledok.arenas_ld.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server → lobby owner: the online players for the invite picker (4.40.3), each with how they stand to the owner
 * ({@code relation}: an {@code ArenasApi.PlayerRelation} ordinal), whether they're in the party or already invited, and
 * why they can't be invited at all (the invite filter's line).
 */
public record InviteCandidatesPayload(BlockPos controller, List<Entry> entries) implements CustomPacketPayload {
    public record Entry(UUID uuid, String name, int relation, boolean member, boolean invited, Optional<Component> blocked) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Entry::uuid,
            ByteBufCodecs.STRING_UTF8, Entry::name,
            ByteBufCodecs.VAR_INT, Entry::relation,
            ByteBufCodecs.BOOL, Entry::member,
            ByteBufCodecs.BOOL, Entry::invited,
            ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, Entry::blocked,
            Entry::new);
    }

    public static final Type<InviteCandidatesPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "invite_candidates"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InviteCandidatesPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, InviteCandidatesPayload::controller,
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), InviteCandidatesPayload::entries,
        InviteCandidatesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
