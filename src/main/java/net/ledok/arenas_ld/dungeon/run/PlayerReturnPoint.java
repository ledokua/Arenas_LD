package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where to teleport a player when their dungeon run ends — win, loss, or quit.
 *
 * <p>Captured at the moment the controller teleports the player into the dungeon. Uses
 * {@link Vec3} rather than {@link net.minecraft.core.BlockPos} so a player who entered
 * standing on a half-slab or facing 47° returns to that exact spot, not snapped to a block.
 *
 * <p>This record is pure data — it does not perform the teleport. The consumer (DungeonRun
 * in Phase E) reads the fields and teleports.
 */
public record PlayerReturnPoint(
    ResourceKey<Level> dimension,
    Vec3 pos,
    float yaw,
    float pitch
) {
    public static final Codec<PlayerReturnPoint> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PlayerReturnPoint::dimension),
            Vec3.CODEC.fieldOf("pos").forGetter(PlayerReturnPoint::pos),
            Codec.FLOAT.fieldOf("yaw").forGetter(PlayerReturnPoint::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(PlayerReturnPoint::pitch)
        ).apply(instance, PlayerReturnPoint::new)
    );

    /**
     * Capture a player's current position, rotation, and dimension as a return point.
     * Call this at run start, before teleporting the player into the dungeon.
     */
    public static PlayerReturnPoint capture(ServerPlayer player) {
        return new PlayerReturnPoint(
            player.level().dimension(),
            player.position(),
            player.getYRot(),
            player.getXRot()
        );
    }
}
