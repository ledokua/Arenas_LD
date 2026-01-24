package net.ledok.arenas_ld.networking;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.item.LinkerItem;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.EquipmentProvider;
import net.ledok.arenas_ld.util.LinkerModeDataComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

public class ModPackets {

    public record UpdateBossSpawnerPayload(
            BlockPos pos, String mobId, int respawnTime, int portalTime, String lootTable, String perPlayerLootTable,
            BlockPos exitCoords, BlockPos enterSpawnCoords, BlockPos enterDestCoords,
            int triggerRadius, int battleRadius, int regeneration, int minPlayers, int skillExperiencePerWin, String groupId
    ) implements CustomPacketPayload {
        public static final Type<UpdateBossSpawnerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_boss_spawner"));

        public static final StreamCodec<FriendlyByteBuf, UpdateBossSpawnerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateBossSpawnerPayload::new);

        public UpdateBossSpawnerPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                    buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(mobId);
            buf.writeVarInt(respawnTime);
            buf.writeVarInt(portalTime);
            buf.writeUtf(lootTable);
            buf.writeUtf(perPlayerLootTable);
            buf.writeBlockPos(exitCoords);
            buf.writeBlockPos(enterSpawnCoords);
            buf.writeBlockPos(enterDestCoords);
            buf.writeVarInt(triggerRadius);
            buf.writeVarInt(battleRadius);
            buf.writeVarInt(regeneration);
            buf.writeVarInt(minPlayers);
            buf.writeVarInt(skillExperiencePerWin);
            buf.writeUtf(groupId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateDungeonBossSpawnerPayload(
            BlockPos pos, String mobId, int respawnTime, int dungeonCloseTimer, String lootTable, String perPlayerLootTable,
            BlockPos exitPositionCoords, BlockPos enterSpawnCoords, BlockPos enterDestCoords,
            int triggerRadius, int battleRadius, int regeneration, int skillExperiencePerWin, String groupId
    ) implements CustomPacketPayload {
        public static final Type<UpdateDungeonBossSpawnerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_dungeon_boss_spawner"));

        public static final StreamCodec<FriendlyByteBuf, UpdateDungeonBossSpawnerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateDungeonBossSpawnerPayload::new);

        public UpdateDungeonBossSpawnerPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                    buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(mobId);
            buf.writeVarInt(respawnTime);
            buf.writeVarInt(dungeonCloseTimer);
            buf.writeUtf(lootTable);
            buf.writeUtf(perPlayerLootTable);
            buf.writeBlockPos(exitPositionCoords);
            buf.writeBlockPos(enterSpawnCoords);
            buf.writeBlockPos(enterDestCoords);
            buf.writeVarInt(triggerRadius);
            buf.writeVarInt(battleRadius);
            buf.writeVarInt(regeneration);
            buf.writeVarInt(skillExperiencePerWin);
            buf.writeUtf(groupId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateMobSpawnerPayload(
            BlockPos pos, String mobId, int respawnTime, String lootTable,
            int triggerRadius, int battleRadius, int regeneration, int skillExperience,
            int mobCount, int mobSpread, String groupId
    ) implements CustomPacketPayload {
        public static final Type<UpdateMobSpawnerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_mob_spawner"));

        public static final StreamCodec<FriendlyByteBuf, UpdateMobSpawnerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateMobSpawnerPayload::new);

        public UpdateMobSpawnerPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(), buf.readUtf(), buf.readVarInt(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(mobId);
            buf.writeVarInt(respawnTime);
            buf.writeUtf(lootTable);
            buf.writeVarInt(triggerRadius);
            buf.writeVarInt(battleRadius);
            buf.writeVarInt(regeneration);
            buf.writeVarInt(skillExperience);
            buf.writeVarInt(mobCount);
            buf.writeVarInt(mobSpread);
            buf.writeUtf(groupId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {return TYPE;}
    }

    public record UpdateAttributesPayload(
            BlockPos pos, List<AttributeData> attributes
    ) implements CustomPacketPayload {
        public static final Type<UpdateAttributesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_attributes"));

        public static final StreamCodec<FriendlyByteBuf, UpdateAttributesPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateAttributesPayload::new);

        public UpdateAttributesPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(),
                    buf.readList(b -> new AttributeData(b.readUtf(), b.readDouble()))
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeCollection(attributes, (b, attr) -> {
                b.writeUtf(attr.id());
                b.writeDouble(attr.value());
            });
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateEquipmentPayload(
            BlockPos pos, EquipmentData equipment
    ) implements CustomPacketPayload {
        public static final Type<UpdateEquipmentPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_equipment"));

        public static final StreamCodec<FriendlyByteBuf, UpdateEquipmentPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateEquipmentPayload::new);

        public UpdateEquipmentPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(),
                    new EquipmentData(
                            buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean()
                    )
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(equipment.head);
            buf.writeUtf(equipment.chest);
            buf.writeUtf(equipment.legs);
            buf.writeUtf(equipment.feet);
            buf.writeUtf(equipment.mainHand);
            buf.writeUtf(equipment.offHand);
            buf.writeBoolean(equipment.dropChance);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CycleLinkerModePayload(boolean forward) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CycleLinkerModePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "cycle_linker_mode"));
        public static final StreamCodec<FriendlyByteBuf, CycleLinkerModePayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBoolean(payload.forward()),
                (buf) -> new CycleLinkerModePayload(buf.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerC2SPackets(IEventBus modEventBus) {
        modEventBus.addListener(ModPackets::register);
    }

    private static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ArenasLdMod.MOD_ID);
        registrar.playToServer(UpdateBossSpawnerPayload.TYPE, UpdateBossSpawnerPayload.STREAM_CODEC, ModPackets::handleUpdateBossSpawner);
        registrar.playToServer(UpdateDungeonBossSpawnerPayload.TYPE, UpdateDungeonBossSpawnerPayload.STREAM_CODEC, ModPackets::handleUpdateDungeonBossSpawner);
        registrar.playToServer(UpdateMobSpawnerPayload.TYPE, UpdateMobSpawnerPayload.STREAM_CODEC, ModPackets::handleUpdateMobSpawner);
        registrar.playToServer(UpdateAttributesPayload.TYPE, UpdateAttributesPayload.STREAM_CODEC, ModPackets::handleUpdateAttributes);
        registrar.playToServer(UpdateEquipmentPayload.TYPE, UpdateEquipmentPayload.STREAM_CODEC, ModPackets::handleUpdateEquipment);
        registrar.playToServer(CycleLinkerModePayload.TYPE, CycleLinkerModePayload.CODEC, ModPackets::handleCycleLinkerMode);
    }

    private static void handleUpdateBossSpawner(final UpdateBossSpawnerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level world = context.player().level();
            BlockEntity be = world.getBlockEntity(payload.pos());
            if (be instanceof BossSpawnerBlockEntity blockEntity) {
                blockEntity.mobId = payload.mobId();
                blockEntity.respawnTime = payload.respawnTime();
                blockEntity.portalActiveTime = payload.portalTime();
                blockEntity.lootTableId = payload.lootTable();
                blockEntity.perPlayerLootTableId = payload.perPlayerLootTable();
                blockEntity.exitPortalCoords = payload.exitCoords();
                blockEntity.enterPortalSpawnCoords = payload.enterSpawnCoords();
                blockEntity.enterPortalDestCoords = payload.enterDestCoords();
                blockEntity.triggerRadius = payload.triggerRadius();
                blockEntity.battleRadius = payload.battleRadius();
                blockEntity.regeneration = payload.regeneration();
                blockEntity.minPlayers = payload.minPlayers();
                blockEntity.skillExperiencePerWin = payload.skillExperiencePerWin();
                blockEntity.groupId = payload.groupId();
                blockEntity.setChanged();
                world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
        });
    }

    private static void handleUpdateDungeonBossSpawner(final UpdateDungeonBossSpawnerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level world = context.player().level();
            BlockEntity be = world.getBlockEntity(payload.pos());
            if (be instanceof DungeonBossSpawnerBlockEntity blockEntity) {
                blockEntity.mobId = payload.mobId();
                blockEntity.respawnTime = payload.respawnTime();
                blockEntity.dungeonCloseTimer = payload.dungeonCloseTimer();
                blockEntity.lootTableId = payload.lootTable();
                blockEntity.perPlayerLootTableId = payload.perPlayerLootTable();
                blockEntity.exitPositionCoords = payload.exitPositionCoords();
                blockEntity.enterPortalSpawnCoords = payload.enterSpawnCoords();
                blockEntity.enterPortalDestCoords = payload.enterDestCoords();
                blockEntity.triggerRadius = payload.triggerRadius();
                blockEntity.battleRadius = payload.battleRadius();
                blockEntity.regeneration = payload.regeneration();
                blockEntity.skillExperiencePerWin = payload.skillExperiencePerWin();
                blockEntity.groupId = payload.groupId();
                blockEntity.setChanged();
                world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
        });
    }

    private static void handleUpdateMobSpawner(final UpdateMobSpawnerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level world = context.player().level();
            if (world.getBlockEntity(payload.pos()) instanceof MobSpawnerBlockEntity blockEntity) {
                blockEntity.mobId = payload.mobId();
                blockEntity.respawnTime = payload.respawnTime();
                blockEntity.lootTableId = payload.lootTable();
                blockEntity.triggerRadius = payload.triggerRadius();
                blockEntity.battleRadius = payload.battleRadius();
                blockEntity.regeneration = payload.regeneration();
                blockEntity.skillExperiencePerWin = payload.skillExperience();
                blockEntity.mobCount = payload.mobCount();
                blockEntity.mobSpread = payload.mobSpread();

                if (!blockEntity.groupId.equals(payload.groupId())) {
                    ArenasLdMod.PHASE_BLOCK_MANAGER.unregisterSpawner(blockEntity);
                    blockEntity.groupId = payload.groupId();
                    ArenasLdMod.PHASE_BLOCK_MANAGER.registerSpawner(blockEntity);
                }

                blockEntity.setChanged();
                world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
        });
    }

    private static void handleUpdateAttributes(final UpdateAttributesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level world = context.player().level();
            BlockEntity be = world.getBlockEntity(payload.pos());
            if (be instanceof AttributeProvider provider) {
                provider.setAttributes(payload.attributes());
                be.setChanged();
                world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }

    private static void handleUpdateEquipment(final UpdateEquipmentPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level world = context.player().level();
            BlockEntity be = world.getBlockEntity(payload.pos());
            if (be instanceof EquipmentProvider provider) {
                provider.setEquipment(payload.equipment());
                be.setChanged();
                world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }

    private static void handleCycleLinkerMode(final CycleLinkerModePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = context.player().getMainHandItem();
            if (stack.getItem() instanceof LinkerItem) {
                LinkerModeDataComponent data = stack.getOrDefault(LinkerModeDataComponent.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
                int currentMode = data.mode();
                int newMode = (currentMode + (payload.forward() ? 1 : -1) + LinkerItem.Mode.values().length) % LinkerItem.Mode.values().length;
                stack.set(LinkerModeDataComponent.LINKER_MODE_DATA, new LinkerModeDataComponent(newMode, data.mainSpawnerPos()));

                LinkerItem.Mode mode = LinkerItem.Mode.values()[newMode];
                ChatFormatting color = mode == LinkerItem.Mode.SPAWNER_LINKING ? ChatFormatting.BLUE : ChatFormatting.YELLOW;
                context.player().sendSystemMessage(Component.literal("Mode: " + mode.getName()).withStyle(color));
            }
        });
    }
}
