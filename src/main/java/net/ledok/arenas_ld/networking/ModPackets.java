package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.*;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.item.LinkerItem;
import net.ledok.arenas_ld.item.SpawnerConfiguratorItem;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class ModPackets {

    public record UpdateBossSpawnerPayload(
            BlockPos pos, String mobId, int respawnTime, int portalTime, String lootTable, String perPlayerLootTable,
            BlockPos exitPortalCoords, ResourceLocation exitDimension,
            BlockPos enterPortalSpawnCoords, ResourceLocation enterPortalSpawnDimension,
            BlockPos enterPortalDestCoords, ResourceLocation enterPortalDestDimension,
            int triggerRadius, int battleRadius, int regeneration, int minPlayers, int skillExperiencePerWin, String groupId
    ) implements CustomPacketPayload {
        public static final Type<UpdateBossSpawnerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_boss_spawner"));

        public static final StreamCodec<FriendlyByteBuf, UpdateBossSpawnerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateBossSpawnerPayload::new);

        public UpdateBossSpawnerPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                    buf.readBlockPos(), buf.readResourceLocation(),
                    buf.readBlockPos(), buf.readResourceLocation(),
                    buf.readBlockPos(), buf.readResourceLocation(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(mobId);
            buf.writeVarInt(respawnTime);
            buf.writeVarInt(portalTime);
            buf.writeUtf(lootTable);
            buf.writeUtf(perPlayerLootTable);
            buf.writeBlockPos(exitPortalCoords);
            buf.writeResourceLocation(exitDimension);
            buf.writeBlockPos(enterPortalSpawnCoords);
            buf.writeResourceLocation(enterPortalSpawnDimension);
            buf.writeBlockPos(enterPortalDestCoords);
            buf.writeResourceLocation(enterPortalDestDimension);
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
            BlockPos pos, String mobId, int respawnTime, int dungeonCloseTimer, int dungeonTime, String lootTable, String perPlayerLootTable,
            BlockPos exitPositionCoords, ResourceLocation exitDimension,
            int triggerRadius, int battleRadius, int regeneration, int skillExperiencePerWin, String groupId
    ) implements CustomPacketPayload {
        public static final Type<UpdateDungeonBossSpawnerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_dungeon_boss_spawner"));

        public static final StreamCodec<FriendlyByteBuf, UpdateDungeonBossSpawnerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateDungeonBossSpawnerPayload::new);

        public UpdateDungeonBossSpawnerPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                    buf.readBlockPos(), buf.readResourceLocation(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(mobId);
            buf.writeVarInt(respawnTime);
            buf.writeVarInt(dungeonCloseTimer);
            buf.writeVarInt(dungeonTime);
            buf.writeUtf(lootTable);
            buf.writeUtf(perPlayerLootTable);
            buf.writeBlockPos(exitPositionCoords);
            buf.writeResourceLocation(exitDimension);
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

    public record UpdateMobArenaSpawnerPayload(
            BlockPos pos, int triggerRadius, int battleRadius, int spawnDistance,
            int waveTimer, int additionalTime, int timeBetweenWaves, double attributeScale, int prepareTime,
            BlockPos exitPosition, ResourceLocation exitDimension,
            BlockPos arenaEntrancePosition, ResourceLocation arenaEntranceDimension,
            String groupId, int bossWaveAdditionalTime, int entityHighlightTime
    ) implements CustomPacketPayload {
        public static final Type<UpdateMobArenaSpawnerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_mob_arena_spawner"));

        public static final StreamCodec<FriendlyByteBuf, UpdateMobArenaSpawnerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateMobArenaSpawnerPayload::new);

        public UpdateMobArenaSpawnerPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readVarInt(),
                    buf.readBlockPos(), buf.readResourceLocation(),
                    buf.readBlockPos(), buf.readResourceLocation(),
                    buf.readUtf(), buf.readVarInt(), buf.readVarInt()
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(triggerRadius);
            buf.writeVarInt(battleRadius);
            buf.writeVarInt(spawnDistance);
            buf.writeVarInt(waveTimer);
            buf.writeVarInt(additionalTime);
            buf.writeVarInt(timeBetweenWaves);
            buf.writeDouble(attributeScale);
            buf.writeVarInt(prepareTime);
            buf.writeBlockPos(exitPosition);
            buf.writeResourceLocation(exitDimension);
            buf.writeBlockPos(arenaEntrancePosition);
            buf.writeResourceLocation(arenaEntranceDimension);
            buf.writeUtf(groupId);
            buf.writeVarInt(bossWaveAdditionalTime);
            buf.writeVarInt(entityHighlightTime);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateMobArenaMobsPayload(
            BlockPos pos, List<MobArenaMobData> mobs
    ) implements CustomPacketPayload {
        public static final Type<UpdateMobArenaMobsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_mob_arena_mobs"));

        public static final StreamCodec<FriendlyByteBuf, UpdateMobArenaMobsPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateMobArenaMobsPayload::new);

        public UpdateMobArenaMobsPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(),
                    readMobs(buf)
            );
        }

        private static List<MobArenaMobData> readMobs(FriendlyByteBuf buf) {
            List<MobArenaMobData> mobs = new ArrayList<>();
            int size = buf.readVarInt();
            for (int i = 0; i < size; i++) {
                CompoundTag tag = buf.readNbt();
                if (tag != null) {
                    mobs.add(MobArenaMobData.fromNbt(tag));
                }
            }
            return mobs;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(mobs.size());
            for (MobArenaMobData mob : mobs) {
                buf.writeNbt(mob.toNbt());
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    
    public record UpdateMobArenaRewardsPayload(
            BlockPos pos, List<MobArenaRewardData> rewards
    ) implements CustomPacketPayload {
        public static final Type<UpdateMobArenaRewardsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_mob_arena_rewards"));

        public static final StreamCodec<FriendlyByteBuf, UpdateMobArenaRewardsPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateMobArenaRewardsPayload::new);

        public UpdateMobArenaRewardsPayload(FriendlyByteBuf buf) {
            this(
                    buf.readBlockPos(),
                    readRewards(buf)
            );
        }

        private static List<MobArenaRewardData> readRewards(FriendlyByteBuf buf) {
            List<MobArenaRewardData> rewards = new ArrayList<>();
            int size = buf.readVarInt();
            for (int i = 0; i < size; i++) {
                CompoundTag tag = buf.readNbt();
                if (tag != null) {
                    rewards.add(MobArenaRewardData.fromNbt(tag));
                }
            }
            return rewards;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(rewards.size());
            for (MobArenaRewardData reward : rewards) {
                buf.writeNbt(reward.toNbt());
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
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
                    buf.readList(b -> new AttributeData(b.readUtf(), b.readDouble(), b.readDouble()))
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeCollection(attributes, (b, attr) -> {
                b.writeUtf(attr.id());
                b.writeDouble(attr.value());
                b.writeDouble(attr.maxValue());
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

    public record CycleConfiguratorModePayload(boolean forward) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CycleConfiguratorModePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "cycle_configurator_mode"));
        public static final StreamCodec<FriendlyByteBuf, CycleConfiguratorModePayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBoolean(payload.forward()),
                (buf) -> new CycleConfiguratorModePayload(buf.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MobArenaControllerActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
        public static final Type<MobArenaControllerActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "mob_arena_controller_action"));
        public static final StreamCodec<FriendlyByteBuf, MobArenaControllerActionPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeVarInt(payload.action);
                },
                buf -> new MobArenaControllerActionPayload(buf.readBlockPos(), buf.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DungeonControllerActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
        public static final Type<DungeonControllerActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_controller_action"));
        public static final StreamCodec<FriendlyByteBuf, DungeonControllerActionPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeVarInt(payload.action);
                },
                buf -> new DungeonControllerActionPayload(buf.readBlockPos(), buf.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestDungeonControllerInfoPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<RequestDungeonControllerInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "request_dungeon_controller_info"));
        public static final StreamCodec<FriendlyByteBuf, RequestDungeonControllerInfoPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBlockPos(payload.pos),
                buf -> new RequestDungeonControllerInfoPayload(buf.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestMobArenaControllerInfoPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<RequestMobArenaControllerInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "request_mob_arena_controller_info"));
        public static final StreamCodec<FriendlyByteBuf, RequestMobArenaControllerInfoPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBlockPos(payload.pos),
                buf -> new RequestMobArenaControllerInfoPayload(buf.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DungeonControllerInfoPayload(
            BlockPos pos,
            int remainingDungeonTimeSeconds,
            int dungeonCooldownSeconds,
            List<String> players,
            List<DungeonLeaderboardEntry> leaderboard
    ) implements CustomPacketPayload {
        public static final Type<DungeonControllerInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_controller_info"));

        public static final StreamCodec<FriendlyByteBuf, DungeonControllerInfoPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeVarInt(payload.remainingDungeonTimeSeconds);
                    buf.writeVarInt(payload.dungeonCooldownSeconds);
                    buf.writeVarInt(payload.players.size());
                    for (String name : payload.players) {
                        buf.writeUtf(name);
                    }
                    buf.writeVarInt(payload.leaderboard.size());
                    for (DungeonLeaderboardEntry entry : payload.leaderboard) {
                        buf.writeUtf(entry.playerName);
                        buf.writeVarInt(entry.timeSeconds);
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    int remaining = buf.readVarInt();
                    int cooldown = buf.readVarInt();
                    int playerCount = buf.readVarInt();
                    List<String> players = new ArrayList<>();
                    for (int i = 0; i < playerCount; i++) {
                        players.add(buf.readUtf());
                    }
                    int leaderboardCount = buf.readVarInt();
                    List<DungeonLeaderboardEntry> leaderboard = new ArrayList<>();
                    for (int i = 0; i < leaderboardCount; i++) {
                        leaderboard.add(new DungeonLeaderboardEntry(buf.readUtf(), buf.readVarInt()));
                    }
                    return new DungeonControllerInfoPayload(pos, remaining, cooldown, players, leaderboard);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MobArenaControllerInfoPayload(
            BlockPos pos,
            int currentWave,
            List<String> players,
            List<LeaderboardEntry> leaderboard
    ) implements CustomPacketPayload {
        public static final Type<MobArenaControllerInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "mob_arena_controller_info"));

        public static final StreamCodec<FriendlyByteBuf, MobArenaControllerInfoPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeVarInt(payload.currentWave);
                    buf.writeVarInt(payload.players.size());
                    for (String name : payload.players) {
                        buf.writeUtf(name);
                    }
                    buf.writeVarInt(payload.leaderboard.size());
                    for (LeaderboardEntry entry : payload.leaderboard) {
                        buf.writeUtf(entry.playerName);
                        buf.writeVarInt(entry.wave);
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    int currentWave = buf.readVarInt();
                    int playerCount = buf.readVarInt();
                    List<String> players = new ArrayList<>();
                    for (int i = 0; i < playerCount; i++) {
                        players.add(buf.readUtf());
                    }
                    int leaderboardCount = buf.readVarInt();
                    List<LeaderboardEntry> leaderboard = new ArrayList<>();
                    for (int i = 0; i < leaderboardCount; i++) {
                        leaderboard.add(new LeaderboardEntry(buf.readUtf(), buf.readVarInt()));
                    }
                    return new MobArenaControllerInfoPayload(pos, currentWave, players, leaderboard);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerC2SPackets() {
        PayloadTypeRegistry.playC2S().register(UpdateBossSpawnerPayload.TYPE, UpdateBossSpawnerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDungeonBossSpawnerPayload.TYPE, UpdateDungeonBossSpawnerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobSpawnerPayload.TYPE, UpdateMobSpawnerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobArenaSpawnerPayload.TYPE, UpdateMobArenaSpawnerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobArenaMobsPayload.TYPE, UpdateMobArenaMobsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobArenaRewardsPayload.TYPE, UpdateMobArenaRewardsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateAttributesPayload.TYPE, UpdateAttributesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateEquipmentPayload.TYPE, UpdateEquipmentPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CycleLinkerModePayload.TYPE, CycleLinkerModePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CycleConfiguratorModePayload.TYPE, CycleConfiguratorModePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MobArenaControllerActionPayload.TYPE, MobArenaControllerActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DungeonControllerActionPayload.TYPE, DungeonControllerActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestDungeonControllerInfoPayload.TYPE, RequestDungeonControllerInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestMobArenaControllerInfoPayload.TYPE, RequestMobArenaControllerInfoPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(UpdateBossSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof BossSpawnerBlockEntity blockEntity) {
                    blockEntity.mobId = payload.mobId();
                    blockEntity.respawnTime = payload.respawnTime();
                    blockEntity.portalActiveTime = payload.portalTime();
                    blockEntity.lootTableId = payload.lootTable();
                    blockEntity.perPlayerLootTableId = payload.perPlayerLootTable();
                    blockEntity.setExitPortalCoords(payload.exitPortalCoords(), ResourceKey.create(Registries.DIMENSION, payload.exitDimension()));
                    blockEntity.setEnterPortalSpawnCoords(payload.enterPortalSpawnCoords(), ResourceKey.create(Registries.DIMENSION, payload.enterPortalSpawnDimension()));
                    blockEntity.setEnterPortalDestCoords(payload.enterPortalDestCoords(), ResourceKey.create(Registries.DIMENSION, payload.enterPortalDestDimension()));
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
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonBossSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonBossSpawnerBlockEntity blockEntity) {
                    blockEntity.mobId = payload.mobId();
                    blockEntity.respawnTime = payload.respawnTime();
                    blockEntity.dungeonCloseTimer = payload.dungeonCloseTimer();
                    blockEntity.dungeonTime = payload.dungeonTime();
                    blockEntity.lootTableId = payload.lootTable();
                    blockEntity.perPlayerLootTableId = payload.perPlayerLootTable();
                    blockEntity.setExitPositionCoords(payload.exitPositionCoords(), ResourceKey.create(Registries.DIMENSION, payload.exitDimension()));
                    blockEntity.triggerRadius = payload.triggerRadius();
                    blockEntity.battleRadius = payload.battleRadius();
                    blockEntity.regeneration = payload.regeneration();
                    blockEntity.skillExperiencePerWin = payload.skillExperiencePerWin();
                    blockEntity.groupId = payload.groupId();
                    blockEntity.setChanged();
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
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
                    blockEntity.groupId = payload.groupId();
                    blockEntity.setChanged();
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.triggerRadius = payload.triggerRadius();
                    blockEntity.battleRadius = payload.battleRadius();
                    blockEntity.spawnDistance = payload.spawnDistance();
                    blockEntity.waveTimer = payload.waveTimer();
                    blockEntity.additionalTime = payload.additionalTime();
                    blockEntity.timeBetweenWaves = payload.timeBetweenWaves();
                    blockEntity.attributeScale = payload.attributeScale();
                    blockEntity.prepareTime = payload.prepareTime();
                    blockEntity.exitPosition = payload.exitPosition();
                    blockEntity.exitDimension = ResourceKey.create(Registries.DIMENSION, payload.exitDimension());
                    blockEntity.arenaEntrancePosition = payload.arenaEntrancePosition();
                    blockEntity.arenaEntranceDimension = ResourceKey.create(Registries.DIMENSION, payload.arenaEntranceDimension());
                    blockEntity.groupId = payload.groupId();
                    blockEntity.bossWaveAdditionalTime = payload.bossWaveAdditionalTime();
                    blockEntity.entityHighlightTime = payload.entityHighlightTime();
                    blockEntity.setChanged();
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaMobsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.mobs = payload.mobs();
                    blockEntity.setChanged();
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });
        
        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaRewardsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.rewards = payload.rewards();
                    blockEntity.setChanged();
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateAttributesPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof AttributeProvider provider) {
                    provider.setAttributes(payload.attributes());
                    be.setChanged();
                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateEquipmentPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof EquipmentProvider provider) {
                    provider.setEquipment(payload.equipment());
                    be.setChanged();
                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CycleLinkerModePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ItemStack stack = context.player().getMainHandItem();
                if (stack.getItem() instanceof LinkerItem) {
                    LinkerModeDataComponent data = stack.getOrDefault(DataComponentRegistry.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
                    int currentMode = data.mode();
                    int newMode = (currentMode + (payload.forward() ? 1 : -1) + LinkerItem.Mode.values().length) % LinkerItem.Mode.values().length;
                    stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(newMode, data.mainSpawnerPos(), data.mainSpawnerDimension()));
                    
                    LinkerItem.Mode mode = LinkerItem.Mode.values()[newMode];
                    context.player().sendSystemMessage(Component.translatable("message.arenas_ld.linker.mode_changed", mode.getName()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CycleConfiguratorModePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ItemStack stack = context.player().getMainHandItem();
                if (stack.getItem() instanceof SpawnerConfiguratorItem) {
                    SpawnerSelectionDataComponent data = stack.getOrDefault(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
                    int currentMode = data.mode();
                    int newMode = (currentMode + (payload.forward() ? 1 : -1) + SpawnerConfiguratorItem.Mode.values().length) % SpawnerConfiguratorItem.Mode.values().length;
                    stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, new SpawnerSelectionDataComponent(newMode, data.selectedSpawnerPos(), data.selectedSpawnerDimension()));

                    SpawnerConfiguratorItem.Mode mode = SpawnerConfiguratorItem.Mode.values()[newMode];
                    context.player().sendSystemMessage(Component.translatable("message.arenas_ld.configurator.mode_changed", mode.getName()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MobArenaControllerActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof MobArenaControllerBlockEntity controller) {
                    switch (payload.action()) {
                        case 0: // Start Arena
                            if (!controller.isLocked && controller.partyMembers.contains(player.getUUID())) {
                                if (controller.arenaSpawnerPos != BlockPos.ZERO) {
                                    ServerLevel spawnerLevel = world.getServer().getLevel(controller.arenaSpawnerDimension);
                                    if (spawnerLevel != null && spawnerLevel.getBlockEntity(controller.arenaSpawnerPos) instanceof MobArenaSpawnerBlockEntity spawner) {
                                        java.util.Set<java.util.UUID> onlinePlayers = new java.util.HashSet<>();
                                        for (java.util.UUID uuid : controller.partyMembers) {
                                            if (world.getServer().getPlayerList().getPlayer(uuid) != null) {
                                                onlinePlayers.add(uuid);
                                            }
                                        }
                                        if (onlinePlayers.isEmpty()) {
                                            return;
                                        }
                                        controller.partyMembers.clear();
                                        controller.partyMembers.addAll(onlinePlayers);
                                        spawner.startArena(onlinePlayers, payload.pos(), world.dimension());
                                        controller.isLocked = true;
                                        controller.setChanged();
                                        world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                                    }
                                }
                            }
                            break;
                        case 1: // Join Party
                            if (!controller.isLocked) {
                                if (isPlayerInActiveGame(player)) {
                                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                                    return;
                                }
                                removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                                controller.partyMembers.add(player.getUUID());
                                controller.setChanged();
                                world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                            }
                            break;
                        case 2: // Leave Party
                            if (!controller.isLocked) {
                                controller.partyMembers.remove(player.getUUID());
                                controller.setChanged();
                                world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                            }
                            break;
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DungeonControllerActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonControllerBlockEntity controller) {
                    switch (payload.action()) {
                        case 0: // Start Dungeon
                            if (!controller.isLocked && controller.partyMembers.contains(player.getUUID())) {
                                if (controller.dungeonSpawnerPos != BlockPos.ZERO) {
                                    ServerLevel spawnerLevel = world.getServer().getLevel(controller.dungeonSpawnerDimension);
                                    if (spawnerLevel != null && spawnerLevel.getBlockEntity(controller.dungeonSpawnerPos) instanceof DungeonBossSpawnerBlockEntity spawner) {
                                        java.util.Set<java.util.UUID> onlinePlayers = new java.util.HashSet<>();
                                        for (java.util.UUID uuid : controller.partyMembers) {
                                            if (world.getServer().getPlayerList().getPlayer(uuid) != null) {
                                                onlinePlayers.add(uuid);
                                            }
                                        }
                                        if (onlinePlayers.isEmpty()) {
                                            return;
                                        }
                                        controller.partyMembers.clear();
                                        controller.partyMembers.addAll(onlinePlayers);
                                        if (spawner.startDungeon(onlinePlayers, payload.pos(), world.dimension())) {
                                            controller.isLocked = true;
                                            controller.setChanged();
                                            world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                                        } else if (spawner.getRespawnCooldownSeconds() > 0) {
                                            int cooldownSeconds = spawner.getRespawnCooldownSeconds();
                                            context.player().sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_cooldown", formatSeconds(cooldownSeconds)));
                                        }
                                    }
                                }
                            }
                            break;
                        case 1: // Join Party
                            if (!controller.isLocked) {
                                if (isPlayerInActiveGame(player)) {
                                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                                    return;
                                }
                                removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                                controller.partyMembers.add(player.getUUID());
                                controller.setChanged();
                                world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                            }
                            break;
                        case 2: // Leave Party
                            if (!controller.isLocked) {
                                controller.partyMembers.remove(player.getUUID());
                                controller.setChanged();
                                world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                            }
                            break;
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestDungeonControllerInfoPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonControllerBlockEntity controller) {
                    List<String> players = new ArrayList<>();
                    for (java.util.UUID uuid : controller.partyMembers) {
                        ServerPlayer partyPlayer = player.server.getPlayerList().getPlayer(uuid);
                        players.add(partyPlayer != null ? partyPlayer.getGameProfile().getName() : "Unknown");
                    }
                    ServerPlayNetworking.send(player, new DungeonControllerInfoPayload(
                            payload.pos(),
                            controller.remainingDungeonTimeSeconds,
                            controller.dungeonCooldownSeconds,
                            players,
                            new ArrayList<>(controller.leaderboard)
                    ));
                } else {
                    ServerPlayNetworking.send(player, new DungeonControllerInfoPayload(payload.pos(), 0, 0, List.of(), List.of()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestMobArenaControllerInfoPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof MobArenaControllerBlockEntity controller) {
                    List<String> players = new ArrayList<>();
                    for (java.util.UUID uuid : controller.partyMembers) {
                        ServerPlayer partyPlayer = player.server.getPlayerList().getPlayer(uuid);
                        players.add(partyPlayer != null ? partyPlayer.getGameProfile().getName() : "Unknown");
                    }
                    ServerPlayNetworking.send(player, new MobArenaControllerInfoPayload(
                            payload.pos(),
                            controller.currentWave,
                            players,
                            new ArrayList<>(controller.leaderboard)
                    ));
                } else {
                    ServerPlayNetworking.send(player, new MobArenaControllerInfoPayload(payload.pos(), 0, List.of(), List.of()));
                }
            });
        });
    }

    private static boolean isPlayerInActiveGame(ServerPlayer player) {
        if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) return true;
        if (ArenasLdMod.DUNGEON_BOSS_MANAGER.getSpawnerForPlayer(player) != null) return true;
        return false;
    }

    private static void removePlayerFromOtherLobbies(ServerPlayer player, BlockPos currentControllerPos, ResourceKey<Level> currentControllerDim) {
        var server = player.server;
        if (server == null) return;
        for (MobArenaControllerBlockEntity.ControllerKey key : MobArenaControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            BlockEntity be = level.getBlockEntity(key.pos());
            if (be instanceof MobArenaControllerBlockEntity controller) {
                if (controller.partyMembers.contains(player.getUUID())) {
                    if (!(level.dimension().equals(currentControllerDim) && controller.getBlockPos().equals(currentControllerPos))) {
                        controller.partyMembers.remove(player.getUUID());
                        controller.setChanged();
                        level.sendBlockUpdated(controller.getBlockPos(), controller.getBlockState(), controller.getBlockState(), 3);
                    }
                }
            }
        }
        for (DungeonControllerBlockEntity.ControllerKey key : DungeonControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            BlockEntity be = level.getBlockEntity(key.pos());
            if (be instanceof DungeonControllerBlockEntity controller) {
                if (controller.partyMembers.contains(player.getUUID())) {
                    if (!(level.dimension().equals(currentControllerDim) && controller.getBlockPos().equals(currentControllerPos))) {
                        controller.partyMembers.remove(player.getUUID());
                        controller.setChanged();
                        level.sendBlockUpdated(controller.getBlockPos(), controller.getBlockState(), controller.getBlockState(), 3);
                    }
                }
            }
        }
    }

    public static void registerS2CPackets() {
        PayloadTypeRegistry.playS2C().register(DungeonControllerInfoPayload.TYPE, DungeonControllerInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(MobArenaControllerInfoPayload.TYPE, MobArenaControllerInfoPayload.STREAM_CODEC);
    }

    private static String formatSeconds(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
