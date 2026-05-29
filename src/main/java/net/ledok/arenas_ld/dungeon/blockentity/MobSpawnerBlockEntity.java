package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.MobSpawnerData;
import net.ledok.arenas_ld.dungeon.screen.MobSpawnerScreenHandler;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.ledok.arenas_ld.util.EntityEquipmentHelper;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.EquipmentProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MobSpawnerBlockEntity extends BlockEntity implements AttributeProvider, EquipmentProvider, ExtendedScreenHandlerFactory<MobSpawnerData> {

    private EntityDefinition entityDefinition = EntityDefinition.DEFAULT;

    public MobSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.MOB_SPAWNER_BLOCK_ENTITY, pos, state);
    }

    public EntityDefinition getEntityDefinition() {
        return entityDefinition;
    }

    public void setEntityDefinition(EntityDefinition def) {
        this.entityDefinition = def;
        setChanged();
    }

    @Override
    public List<AttributeData> getAttributes() {
        return entityDefinition.attributes();
    }

    @Override
    public void setAttributes(List<AttributeData> attrs) {
        setEntityDefinition(entityDefinition.withAttributes(attrs));
    }

    @Override
    public EquipmentData getEquipment() {
        return entityDefinition.equipment();
    }

    @Override
    public void setEquipment(EquipmentData eq) {
        setEntityDefinition(entityDefinition.withEquipment(eq));
    }

    private static final Codec<State> STATE_CODEC =
        RecordCodecBuilder.create(i -> i.group(
            EntityDefinition.CODEC.fieldOf("entity").forGetter(State::entity)
        ).apply(i, State::new));

    private record State(EntityDefinition entity) {}

    /**
     * Spawn all mobs for this spawner (spawnCount mobs distributed across spawnOffsets),
     * scaled by the given health multiplier. Called by the RoomController.
     *
     * @return list of successfully spawned entities (may be empty on failure)
     */
    public List<LivingEntity> spawnScaled(ServerLevel world, double healthMultiplier) {
        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(this.entityDefinition.mobId());
        if (entityTypeOpt.isEmpty()) {
            ArenasLdMod.LOGGER.warn("MobSpawner: invalid mob ID {} at {}", entityDefinition.mobId(), worldPosition);
            return List.of();
        }

        List<BlockPos> offsets = this.entityDefinition.spawnOffsets();
        int count = Math.max(1, this.entityDefinition.spawnCount());
        List<LivingEntity> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Entity mob = entityTypeOpt.get().create(world);
            if (!(mob instanceof LivingEntity living)) {
                ArenasLdMod.LOGGER.warn("MobSpawner: not a LivingEntity: {}", entityDefinition.mobId());
                continue;
            }

            EntityEquipmentHelper.applyScaledAttributes(
                living, this.entityDefinition.attributes(), world.registryAccess(), healthMultiplier, 1.0, 1.0);

            EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.HEAD, entityDefinition.equipment().head, false);
            EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.CHEST, entityDefinition.equipment().chest, false);
            EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.LEGS, entityDefinition.equipment().legs, false);
            EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.FEET, entityDefinition.equipment().feet, false);
            EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.MAINHAND, entityDefinition.equipment().mainHand, false);
            EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.OFFHAND, entityDefinition.equipment().offHand, false);

            living.heal(living.getMaxHealth());

            double spawnX, spawnY, spawnZ;
            if (offsets.isEmpty()) {
                spawnX = worldPosition.getX() + 0.5;
                spawnY = worldPosition.getY() + 1;
                spawnZ = worldPosition.getZ() + 0.5;
            } else {
                BlockPos offset = offsets.get(i % offsets.size());
                spawnX = worldPosition.getX() + offset.getX() + 0.5;
                spawnY = worldPosition.getY() + offset.getY();
                spawnZ = worldPosition.getZ() + offset.getZ() + 0.5;
            }
            living.moveTo(spawnX, spawnY, spawnZ, world.random.nextFloat() * 360.0F, 0.0F);

            if (world.addFreshEntity(living)) {
                addToArenasTeam(world, living);
                result.add(living);
            }
        }
        return result;
    }

    static void addToArenasTeam(ServerLevel world, LivingEntity living) {
        Scoreboard scoreboard = world.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam("arenas_dungeon");
        if (team == null) {
            team = scoreboard.addPlayerTeam("arenas_dungeon");
            team.setAllowFriendlyFire(false);
        }
        scoreboard.addPlayerToTeam(living.getStringUUID(), team);
    }

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

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.arenas_ld.mob_spawner.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new MobSpawnerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public MobSpawnerData getScreenOpeningData(ServerPlayer player) {
        return new MobSpawnerData(worldPosition, entityDefinition.mobId(), entityDefinition.spawnCount(), entityDefinition.spawnOffsets());
    }
}
