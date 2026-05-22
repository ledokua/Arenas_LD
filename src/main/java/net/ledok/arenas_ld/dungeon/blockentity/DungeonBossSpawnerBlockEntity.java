package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerData;
import net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreenHandler;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.ledok.arenas_ld.util.EntityEquipmentHelper;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.EquipmentProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DungeonBossSpawnerBlockEntity extends BlockEntity implements AttributeProvider, EquipmentProvider, ExtendedScreenHandlerFactory<DungeonBossSpawnerData> {

    private EntityDefinition entityDefinition = EntityDefinition.DEFAULT.withMobId("minecraft:zombie");
    private BlockPos entrancePos = BlockPos.ZERO;
    private ResourceKey<Level> entranceDimension = Level.OVERWORLD;
    private final List<BlockPos> rooms = new ArrayList<>();

    public DungeonBossSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_BOSS_SPAWNER_V2_BLOCK_ENTITY, pos, state);
    }

    public EntityDefinition getEntityDefinition() { return entityDefinition; }

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

    public BlockPos getEntrancePos() { return entrancePos; }

    public ResourceKey<Level> getEntranceDimension() { return entranceDimension; }

    public void setEntrance(BlockPos pos, ResourceKey<Level> dim) {
        this.entrancePos = pos;
        this.entranceDimension = dim;
        setChanged();
    }

    public List<BlockPos> getRooms() { return Collections.unmodifiableList(rooms); }

    public boolean addRoom(BlockPos pos) {
        if (rooms.contains(pos)) return false;
        rooms.add(pos);
        setChanged();
        return true;
    }

    public boolean removeRoom(BlockPos pos) {
        boolean removed = rooms.remove(pos);
        if (removed) setChanged();
        return removed;
    }

    public boolean moveRoom(int from, int to) {
        if (from < 0 || from >= rooms.size() || to < 0 || to >= rooms.size()) return false;
        if (from == to) return false;
        BlockPos moved = rooms.remove(from);
        rooms.add(to, moved);
        setChanged();
        return true;
    }

    public void clearRooms() {
        if (!rooms.isEmpty()) {
            rooms.clear();
            setChanged();
        }
    }

    @Nullable
    public LivingEntity spawnSingleScaled(ServerLevel world, double healthMultiplier) {
        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(this.entityDefinition.mobId());
        if (entityTypeOpt.isEmpty()) {
            ArenasLdMod.LOGGER.warn("DungeonBossSpawner (v4.0): invalid mob ID {} at {}", entityDefinition.mobId(), worldPosition);
            return null;
        }

        Entity mob = entityTypeOpt.get().create(world);
        if (!(mob instanceof LivingEntity living)) {
            ArenasLdMod.LOGGER.warn("DungeonBossSpawner (v4.0): not a LivingEntity: {}", entityDefinition.mobId());
            return null;
        }

        for (AttributeData attr : this.entityDefinition.attributes()) {
            ResourceLocation attrLoc = ResourceLocation.tryParse(attr.id());
            if (attrLoc == null) continue;
            var attrRegistry = world.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
            ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, attrLoc);
            attrRegistry.getHolder(key).ifPresent(holder -> {
                AttributeInstance inst = living.getAttribute(holder);
                if (inst != null) {
                    double value = attr.value();
                    if ("minecraft:generic.max_health".equals(attr.id())) {
                        value *= healthMultiplier;
                    }
                    inst.setBaseValue(value);
                }
            });
        }

        EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.HEAD, entityDefinition.equipment().head, entityDefinition.equipment().dropChance);
        EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.CHEST, entityDefinition.equipment().chest, entityDefinition.equipment().dropChance);
        EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.LEGS, entityDefinition.equipment().legs, entityDefinition.equipment().dropChance);
        EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.FEET, entityDefinition.equipment().feet, entityDefinition.equipment().dropChance);
        EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.MAINHAND, entityDefinition.equipment().mainHand, entityDefinition.equipment().dropChance);
        EntityEquipmentHelper.applyEquipment(living, EquipmentSlot.OFFHAND, entityDefinition.equipment().offHand, entityDefinition.equipment().dropChance);

        living.heal(living.getMaxHealth());

        living.moveTo(
            worldPosition.getX() + 0.5,
            worldPosition.getY() + 1,
            worldPosition.getZ() + 0.5,
            world.random.nextFloat() * 360.0F,
            0.0F
        );

        if (!world.addFreshEntity(living)) {
            return null;
        }
        return living;
    }

    private record State(
        EntityDefinition entity,
        BlockPos entrancePos,
        ResourceKey<Level> entranceDim,
        List<BlockPos> rooms
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityDefinition.CODEC.fieldOf("entity").forGetter(State::entity),
            BlockPos.CODEC.fieldOf("entrancePos").forGetter(State::entrancePos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("entranceDim").forGetter(State::entranceDim),
            BlockPos.CODEC.listOf().fieldOf("rooms").forGetter(State::rooms)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        State.CODEC.encodeStart(NbtOps.INSTANCE, new State(entityDefinition, entrancePos, entranceDimension, rooms))
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                "Failed to save DungeonBossSpawner at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            State.CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                    "Failed to load DungeonBossSpawner at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    this.entityDefinition = state.entity();
                    this.entrancePos = state.entrancePos();
                    this.entranceDimension = state.entranceDim();
                    this.rooms.clear();
                    this.rooms.addAll(state.rooms());
                });
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.arenas_ld.dungeon_boss_spawner_v2.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new DungeonBossSpawnerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public DungeonBossSpawnerData getScreenOpeningData(ServerPlayer player) {
        return new DungeonBossSpawnerData(worldPosition, entityDefinition.mobId(), entrancePos, entranceDimension.location().toString(), List.copyOf(rooms));
    }
}
