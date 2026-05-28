package net.ledok.arenas_ld.dungeon.lobby;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record PendingJoinRequest(
    UUID lobbyId,
    UUID requesterUuid,
    String requesterName,
    long expiresAtTick,
    DifficultyTier tier
) {
    public static final Codec<PendingJoinRequest> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("lobbyId").forGetter(PendingJoinRequest::lobbyId),
            UUIDUtil.CODEC.fieldOf("requesterUuid").forGetter(PendingJoinRequest::requesterUuid),
            Codec.STRING.optionalFieldOf("requesterName", "").forGetter(PendingJoinRequest::requesterName),
            Codec.LONG.fieldOf("expiresAtTick").forGetter(PendingJoinRequest::expiresAtTick),
            DifficultyTier.CODEC.optionalFieldOf("tier", DifficultyTier.NORMAL).forGetter(PendingJoinRequest::tier)
        ).apply(instance, PendingJoinRequest::new)
    );
}
