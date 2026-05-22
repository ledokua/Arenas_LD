package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MobSpawnerBlockEntity extends BlockEntity {

    private EntityDefinition entityDefinition = EntityDefinition.DEFAULT;

    public MobSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.MOB_SPAWNER_V2_BLOCK_ENTITY, pos, state);
    }

    public EntityDefinition getEntityDefinition() {
        return entityDefinition;
    }

    public void setEntityDefinition(EntityDefinition def) {
        this.entityDefinition = def;
        setChanged();
    }

    private static final Codec<State> STATE_CODEC =
        RecordCodecBuilder.create(i -> i.group(
            EntityDefinition.CODEC.fieldOf("entity").forGetter(State::entity)
        ).apply(i, State::new));

    private record State(EntityDefinition entity) {}

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        STATE_CODEC.encodeStart(NbtOps.INSTANCE, new State(entityDefinition))
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                "Failed to save MobSpawner at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            STATE_CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                    "Failed to load MobSpawner at {}: {}", worldPosition, err))
                .ifPresent(state -> this.entityDefinition = state.entity());
        }
    }
}
