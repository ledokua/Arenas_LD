package net.ledok.arenas_ld.dungeon.lobby;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Pending lobby invite tracked on the controller.
 */
public record PendingInvite(
    UUID lobbyId,
    UUID invitedUuid,
    UUID inviterUuid,
    long expiresAtTick,
    DifficultyTier tier,
    String ownerName
) {
    public static final Codec<PendingInvite> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("lobbyId").forGetter(PendingInvite::lobbyId),
            UUIDUtil.CODEC.fieldOf("invitedUuid").forGetter(PendingInvite::invitedUuid),
            UUIDUtil.CODEC.fieldOf("inviterUuid").forGetter(PendingInvite::inviterUuid),
            Codec.LONG.fieldOf("expiresAtTick").forGetter(PendingInvite::expiresAtTick),
            DifficultyTier.CODEC.optionalFieldOf("tier", DifficultyTier.NORMAL).forGetter(PendingInvite::tier),
            Codec.STRING.optionalFieldOf("ownerName", "").forGetter(PendingInvite::ownerName)
        ).apply(instance, PendingInvite::new)
    );
}
