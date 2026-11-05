package net.ledok.arenas_ld.activity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One instance of an activity: its spawner block, by dimension <em>and</em> position. Copies of an
 * instance in copied dimensions share their coordinates, so the position alone does not tell them
 * apart — runs keyed by it overwrote each other.
 */
public record InstanceKey(ResourceKey<Level> dimension, BlockPos pos) {
    public static final Codec<InstanceKey> CODEC = RecordCodecBuilder.create(i -> i.group(
        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(InstanceKey::dimension),
        BlockPos.CODEC.fieldOf("pos").forGetter(InstanceKey::pos)
    ).apply(i, InstanceKey::new));

    public InstanceKey {
        pos = pos.immutable();
    }
}
