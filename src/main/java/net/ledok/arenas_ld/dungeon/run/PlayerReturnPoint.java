package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where to teleport a player when their dungeon run ends — win, loss, or quit.
 * Also carries the game mode the player had when the run started, so it can be restored on exit.
 */
public record PlayerReturnPoint(
    ResourceKey<Level> dimension,
    Vec3 pos,
    float yaw,
    float pitch,
    GameType previousGameMode
) {
    private static final Codec<GameType> GAME_TYPE_CODEC = Codec.STRING.xmap(
        s -> GameType.byName(s, GameType.SURVIVAL),
        GameType::getName
    );

    public static final Codec<PlayerReturnPoint> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PlayerReturnPoint::dimension),
            Vec3.CODEC.fieldOf("pos").forGetter(PlayerReturnPoint::pos),
            Codec.FLOAT.fieldOf("yaw").forGetter(PlayerReturnPoint::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(PlayerReturnPoint::pitch),
            GAME_TYPE_CODEC.optionalFieldOf("previousGameMode", GameType.SURVIVAL).forGetter(PlayerReturnPoint::previousGameMode)
        ).apply(instance, PlayerReturnPoint::new)
    );

    public static PlayerReturnPoint capture(ServerPlayer player) {
        return new PlayerReturnPoint(
            player.level().dimension(),
            player.position(),
            player.getYRot(),
            player.getXRot(),
            player.gameMode.getGameModeForPlayer()
        );
    }
}
