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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

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

    public record UpdateDungeonTierConfigsPayload(
            BlockPos pos, CompoundTag tierConfigs
    ) implements CustomPacketPayload {
        public static final Type<UpdateDungeonTierConfigsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_dungeon_tier_configs"));

        public static final StreamCodec<FriendlyByteBuf, UpdateDungeonTierConfigsPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeNbt(payload.tierConfigs);
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    CompoundTag tierConfigs = buf.readNbt();
                    return new UpdateDungeonTierConfigsPayload(pos, tierConfigs != null ? tierConfigs : new CompoundTag());
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
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

    public record JoinDungeonLobbyPayload(BlockPos pos, String lobbyId) implements CustomPacketPayload {
        public static final Type<JoinDungeonLobbyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "join_dungeon_lobby"));
        public static final StreamCodec<FriendlyByteBuf, JoinDungeonLobbyPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUtf(payload.lobbyId);
                },
                buf -> new JoinDungeonLobbyPayload(buf.readBlockPos(), buf.readUtf())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RespondDungeonLobbyInvitePayload(String lobbyId, boolean accept) implements CustomPacketPayload {
        public static final Type<RespondDungeonLobbyInvitePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "respond_dungeon_lobby_invite"));
        public static final StreamCodec<FriendlyByteBuf, RespondDungeonLobbyInvitePayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeUtf(payload.lobbyId);
                    buf.writeBoolean(payload.accept);
                },
                buf -> new RespondDungeonLobbyInvitePayload(buf.readUtf(), buf.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CreateDungeonLobbyPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<CreateDungeonLobbyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "create_dungeon_lobby"));
        public static final StreamCodec<FriendlyByteBuf, CreateDungeonLobbyPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBlockPos(payload.pos),
                buf -> new CreateDungeonLobbyPayload(buf.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DisbandDungeonLobbyPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<DisbandDungeonLobbyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "disband_dungeon_lobby"));
        public static final StreamCodec<FriendlyByteBuf, DisbandDungeonLobbyPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBlockPos(payload.pos),
                buf -> new DisbandDungeonLobbyPayload(buf.readBlockPos())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record InviteDungeonLobbyPlayerPayload(BlockPos pos, String playerName) implements CustomPacketPayload {
        public static final Type<InviteDungeonLobbyPlayerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "invite_dungeon_lobby_player"));
        public static final StreamCodec<FriendlyByteBuf, InviteDungeonLobbyPlayerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUtf(payload.playerName);
                },
                buf -> new InviteDungeonLobbyPlayerPayload(buf.readBlockPos(), buf.readUtf())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record KickDungeonLobbyPlayerPayload(BlockPos pos, String playerName) implements CustomPacketPayload {
        public static final Type<KickDungeonLobbyPlayerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "kick_dungeon_lobby_player"));
        public static final StreamCodec<FriendlyByteBuf, KickDungeonLobbyPlayerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUtf(payload.playerName);
                },
                buf -> new KickDungeonLobbyPlayerPayload(buf.readBlockPos(), buf.readUtf())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestDungeonControllerInfoPayload(BlockPos pos, String leaderboardTier) implements CustomPacketPayload {
        public static final Type<RequestDungeonControllerInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "request_dungeon_controller_info"));
        public static final StreamCodec<FriendlyByteBuf, RequestDungeonControllerInfoPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUtf(payload.leaderboardTier);
                },
                buf -> new RequestDungeonControllerInfoPayload(buf.readBlockPos(), buf.readUtf())
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

    public record UpdateDungeonControllerSettingsPayload(BlockPos pos, boolean hardcoreEnabled, String selectedTier) implements CustomPacketPayload {
        public static final Type<UpdateDungeonControllerSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_dungeon_controller_settings"));
        public static final StreamCodec<FriendlyByteBuf, UpdateDungeonControllerSettingsPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeBoolean(payload.hardcoreEnabled);
                    buf.writeUtf(payload.selectedTier);
                },
                buf -> new UpdateDungeonControllerSettingsPayload(buf.readBlockPos(), buf.readBoolean(), buf.readUtf())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateDungeonLobbyVisibilityPayload(BlockPos pos, String visibility) implements CustomPacketPayload {
        public static final Type<UpdateDungeonLobbyVisibilityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_dungeon_lobby_visibility"));
        public static final StreamCodec<FriendlyByteBuf, UpdateDungeonLobbyVisibilityPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUtf(payload.visibility);
                },
                buf -> new UpdateDungeonLobbyVisibilityPayload(buf.readBlockPos(), buf.readUtf())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateDungeonControllerAdminSettingsPayload(BlockPos pos, int respawnTimeTicks, int maxPartySize) implements CustomPacketPayload {
        public static final Type<UpdateDungeonControllerAdminSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_dungeon_controller_admin_settings"));
        public static final StreamCodec<FriendlyByteBuf, UpdateDungeonControllerAdminSettingsPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeVarInt(payload.respawnTimeTicks);
                    buf.writeVarInt(payload.maxPartySize);
                },
                buf -> new UpdateDungeonControllerAdminSettingsPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateMobArenaControllerSettingsPayload(BlockPos pos, boolean hardcoreEnabled) implements CustomPacketPayload {
        public static final Type<UpdateMobArenaControllerSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_mob_arena_controller_settings"));
        public static final StreamCodec<FriendlyByteBuf, UpdateMobArenaControllerSettingsPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeBoolean(payload.hardcoreEnabled);
                },
                buf -> new UpdateMobArenaControllerSettingsPayload(buf.readBlockPos(), buf.readBoolean())
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
            boolean hardcoreEnabled,
            String selectedTier,
            boolean inLobby,
            boolean isLobbyOwner,
            String lobbyStatus,
            int queuePosition,
            String currentLobbyVisibility,
            boolean canManageAdmin,
            int controllerRespawnTimeTicks,
            int controllerMaxPartySize,
            boolean controllerLocked,
            List<String> players,
            List<DungeonLeaderboardEntry> leaderboard,
            List<InstanceView> instances,
            List<LobbyView> lobbies
    ) implements CustomPacketPayload {
        public record InstanceView(
                String status,
                int cooldownSeconds
        ) {}

        public record LobbyView(
                String id,
                String ownerName,
                int size,
                int maxSize,
                String visibility,
                String status,
                int queuePosition,
                String tier,
                boolean hardcore,
                boolean invited
        ) {}

        public static final Type<DungeonControllerInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_controller_info"));

        public static final StreamCodec<FriendlyByteBuf, DungeonControllerInfoPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeVarInt(payload.remainingDungeonTimeSeconds);
                    buf.writeVarInt(payload.dungeonCooldownSeconds);
                    buf.writeBoolean(payload.hardcoreEnabled);
                    buf.writeUtf(payload.selectedTier);
                    buf.writeBoolean(payload.inLobby);
                    buf.writeBoolean(payload.isLobbyOwner);
                    buf.writeUtf(payload.lobbyStatus);
                    buf.writeVarInt(payload.queuePosition);
                    buf.writeUtf(payload.currentLobbyVisibility);
                    buf.writeBoolean(payload.canManageAdmin);
                    buf.writeVarInt(payload.controllerRespawnTimeTicks);
                    buf.writeVarInt(payload.controllerMaxPartySize);
                    buf.writeBoolean(payload.controllerLocked);
                    buf.writeVarInt(payload.players.size());
                    for (String name : payload.players) {
                        buf.writeUtf(name);
                    }
                    buf.writeVarInt(payload.leaderboard.size());
                    for (DungeonLeaderboardEntry entry : payload.leaderboard) {
                        buf.writeUtf(entry.playerName);
                        buf.writeVarInt(entry.timeSeconds);
                    }
                    buf.writeVarInt(payload.instances.size());
                    for (InstanceView instance : payload.instances) {
                        buf.writeUtf(instance.status);
                        buf.writeVarInt(instance.cooldownSeconds);
                    }
                    buf.writeVarInt(payload.lobbies.size());
                    for (LobbyView lobby : payload.lobbies) {
                        buf.writeUtf(lobby.id);
                        buf.writeUtf(lobby.ownerName);
                        buf.writeVarInt(lobby.size);
                        buf.writeVarInt(lobby.maxSize);
                        buf.writeUtf(lobby.visibility);
                        buf.writeUtf(lobby.status);
                        buf.writeVarInt(lobby.queuePosition);
                        buf.writeUtf(lobby.tier);
                        buf.writeBoolean(lobby.hardcore);
                        buf.writeBoolean(lobby.invited);
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    int remaining = buf.readVarInt();
                    int cooldown = buf.readVarInt();
                    boolean hardcoreEnabled = buf.readBoolean();
                    String selectedTier = buf.readUtf();
                    boolean inLobby = buf.readBoolean();
                    boolean isLobbyOwner = buf.readBoolean();
                    String lobbyStatus = buf.readUtf();
                    int queuePosition = buf.readVarInt();
                    String currentLobbyVisibility = buf.readUtf();
                    boolean canManageAdmin = buf.readBoolean();
                    int controllerRespawnTimeTicks = buf.readVarInt();
                    int controllerMaxPartySize = buf.readVarInt();
                    boolean controllerLocked = buf.readBoolean();
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
                    int instanceCount = buf.readVarInt();
                    List<InstanceView> instances = new ArrayList<>();
                    for (int i = 0; i < instanceCount; i++) {
                        instances.add(new InstanceView(buf.readUtf(), buf.readVarInt()));
                    }
                    int lobbyCount = buf.readVarInt();
                    List<LobbyView> lobbies = new ArrayList<>();
                    for (int i = 0; i < lobbyCount; i++) {
                        lobbies.add(new LobbyView(
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readUtf(),
                                buf.readBoolean(),
                                buf.readBoolean()
                        ));
                    }
                    return new DungeonControllerInfoPayload(
                            pos, remaining, cooldown, hardcoreEnabled, selectedTier,
                            inLobby, isLobbyOwner, lobbyStatus, queuePosition, currentLobbyVisibility,
                            canManageAdmin, controllerRespawnTimeTicks, controllerMaxPartySize, controllerLocked,
                            players, leaderboard, instances, lobbies
                    );
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
            boolean hardcoreEnabled,
            List<String> players,
            List<LeaderboardEntry> leaderboard
    ) implements CustomPacketPayload {
        public static final Type<MobArenaControllerInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "mob_arena_controller_info"));

        public static final StreamCodec<FriendlyByteBuf, MobArenaControllerInfoPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeVarInt(payload.currentWave);
                    buf.writeBoolean(payload.hardcoreEnabled);
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
                    boolean hardcoreEnabled = buf.readBoolean();
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
                    return new MobArenaControllerInfoPayload(pos, currentWave, hardcoreEnabled, players, leaderboard);
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
        PayloadTypeRegistry.playC2S().register(UpdateDungeonTierConfigsPayload.TYPE, UpdateDungeonTierConfigsPayload.STREAM_CODEC);
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
        PayloadTypeRegistry.playC2S().register(JoinDungeonLobbyPayload.TYPE, JoinDungeonLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RespondDungeonLobbyInvitePayload.TYPE, RespondDungeonLobbyInvitePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CreateDungeonLobbyPayload.TYPE, CreateDungeonLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DisbandDungeonLobbyPayload.TYPE, DisbandDungeonLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(InviteDungeonLobbyPlayerPayload.TYPE, InviteDungeonLobbyPlayerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(KickDungeonLobbyPlayerPayload.TYPE, KickDungeonLobbyPlayerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestDungeonControllerInfoPayload.TYPE, RequestDungeonControllerInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestMobArenaControllerInfoPayload.TYPE, RequestMobArenaControllerInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDungeonControllerSettingsPayload.TYPE, UpdateDungeonControllerSettingsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDungeonLobbyVisibilityPayload.TYPE, UpdateDungeonLobbyVisibilityPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDungeonControllerAdminSettingsPayload.TYPE, UpdateDungeonControllerAdminSettingsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobArenaControllerSettingsPayload.TYPE, UpdateMobArenaControllerSettingsPayload.STREAM_CODEC);

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
                    blockEntity.applyConfig(
                            payload.mobId(),
                            payload.respawnTime(),
                            payload.dungeonCloseTimer(),
                            payload.dungeonTime(),
                            payload.lootTable(),
                            payload.perPlayerLootTable(),
                            payload.exitPositionCoords(),
                            ResourceKey.create(Registries.DIMENSION, payload.exitDimension()),
                            payload.triggerRadius(),
                            payload.battleRadius(),
                            payload.regeneration(),
                            payload.skillExperiencePerWin(),
                            payload.groupId()
                    );
                    if (world instanceof ServerLevel serverLevel) {
                        DungeonControllerBlockEntity.updateControllerRespawnTimeForSpawner(
                                serverLevel.getServer(),
                                new DungeonInstanceRef(payload.pos(), serverLevel.dimension()),
                                payload.respawnTime()
                        );
                    }
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonTierConfigsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonBossSpawnerBlockEntity blockEntity) {
                    CompoundTag tierConfigsTag = payload.tierConfigs();
                    blockEntity.getTierConfigs().clear();
                    for (DifficultyTier tier : DifficultyTier.values()) {
                        TierConfig config = new TierConfig();
                        if (tierConfigsTag.contains(tier.name(), net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                            config = TierConfig.fromNbt(tierConfigsTag.getCompound(tier.name()));
                        }
                        blockEntity.getTierConfigs().put(tier, config);
                    }
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
                    blockEntity.applyConfig(
                            payload.triggerRadius(),
                            payload.battleRadius(),
                            payload.spawnDistance(),
                            payload.waveTimer(),
                            payload.additionalTime(),
                            payload.timeBetweenWaves(),
                            payload.attributeScale(),
                            payload.prepareTime(),
                            payload.exitPosition(),
                            ResourceKey.create(Registries.DIMENSION, payload.exitDimension()),
                            payload.arenaEntrancePosition(),
                            ResourceKey.create(Registries.DIMENSION, payload.arenaEntranceDimension()),
                            payload.groupId(),
                            payload.bossWaveAdditionalTime(),
                            payload.entityHighlightTime()
                    );
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaMobsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.setMobs(payload.mobs());
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });
        
        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaRewardsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.setRewards(payload.rewards());
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
                                        spawner.setHardcoreEnabled(controller.hardcoreEnabled);
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
                            if (!controller.isLocked) {
                                Lobby lobby = controller.getLobbyByMember(player.getUUID());
                                if (lobby == null) {
                                    player.sendSystemMessage(Component.translatable("message.arenas_ld.not_in_lobby"));
                                    return;
                                }
                                if (!lobby.ownerUuid.equals(player.getUUID())) {
                                    player.sendSystemMessage(Component.translatable("message.arenas_ld.only_owner_can_start_dungeon"));
                                    return;
                                }
                                DungeonInstanceRef instanceRef = controller.reserveFreeInstance();
                                if (instanceRef == null) {
                                    int cooldownSeconds = controller.getNextAvailableCooldownSeconds();
                                    if (cooldownSeconds > 0) {
                                        context.player().sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_cooldown", formatSeconds(cooldownSeconds)));
                                    }
                                    lobby.status = LobbyStatus.QUEUED;
                                    controller.setChanged();
                                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                                    return;
                                }
                                ServerLevel spawnerLevel = world.getServer().getLevel(instanceRef.dimension());
                                if (spawnerLevel == null || !(spawnerLevel.getBlockEntity(instanceRef.spawnerPos()) instanceof DungeonBossSpawnerBlockEntity spawner)) {
                                    controller.releaseReservedInstance(instanceRef);
                                    return;
                                }
                                HashSet<UUID> onlinePlayers = new HashSet<>();
                                if (world.getServer().getPlayerList().getPlayer(lobby.ownerUuid) != null) {
                                    onlinePlayers.add(lobby.ownerUuid);
                                }
                                for (UUID uuid : lobby.members) {
                                    if (world.getServer().getPlayerList().getPlayer(uuid) != null) {
                                        onlinePlayers.add(uuid);
                                    }
                                }
                                if (onlinePlayers.isEmpty()) {
                                    controller.releaseReservedInstance(instanceRef);
                                    return;
                                }
                                spawner.setHardcoreEnabled(lobby.hardcoreEnabled);
                                if (spawner.startDungeon(onlinePlayers, payload.pos(), world.dimension(), lobby.selectedTier)) {
                                    lobby.status = LobbyStatus.IN_DUNGEON;
                                    controller.assignLobbyToInstance(lobby.id, instanceRef);
                                    controller.isLocked = true;
                                    controller.setChanged();
                                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                                } else {
                                    controller.releaseReservedInstance(instanceRef);
                                    lobby.status = LobbyStatus.OPEN;
                                    controller.setChanged();
                                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
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
                                if (controller.getLobbyByMember(player.getUUID()) != null) {
                                    return;
                                }
                                Lobby openLobby = null;
                                for (Lobby candidate : controller.lobbies) {
                                    if (candidate.status == LobbyStatus.OPEN
                                            && candidate.visibility == LobbyVisibility.OPEN
                                            && (1 + candidate.members.size()) < controller.getMaxPartySize()) {
                                        openLobby = candidate;
                                        break;
                                    }
                                }
                                if (openLobby == null) {
                                    Lobby created = controller.createLobby(player.getUUID(), player.getGameProfile().getName());
                                    if (created == null) {
                                        return;
                                    }
                                } else {
                                    openLobby.members.add(player.getUUID());
                                    controller.setChanged();
                                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                                }
                            }
                            break;
                        case 2: // Leave Party
                            if (!controller.isLocked) {
                                controller.leaveLobby(player.getUUID());
                            }
                            break;
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(JoinDungeonLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller) || controller.isLocked) {
                    return;
                }
                if (isPlayerInActiveGame(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                    return;
                }
                removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                if (controller.getLobbyByMember(player.getUUID()) != null) {
                    return;
                }
                UUID lobbyId;
                try {
                    lobbyId = UUID.fromString(payload.lobbyId());
                } catch (IllegalArgumentException e) {
                    return;
                }
                Lobby lobby = controller.getLobbyById(lobbyId);
                if (lobby == null || lobby.status != LobbyStatus.OPEN) {
                    return;
                }
                if (lobby.visibility == LobbyVisibility.INVITE_ONLY) {
                    long nowTick = player.serverLevel().getGameTime();
                    boolean accepted = controller.acceptInvite(lobby.id, player.getUUID(), player.getGameProfile().getName(), nowTick);
                    if (!accepted) {
                        return;
                    }
                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                    return;
                }
                int currentSize = 1 + lobby.members.size();
                if (currentSize >= controller.getMaxPartySize()) {
                    return;
                }
                lobby.members.add(player.getUUID());
                controller.setChanged();
                world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RespondDungeonLobbyInvitePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                UUID lobbyId;
                try {
                    lobbyId = UUID.fromString(payload.lobbyId());
                } catch (IllegalArgumentException e) {
                    return;
                }
                DungeonControllerBlockEntity controller = findDungeonControllerByLobbyId(player.server, lobbyId);
                if (controller == null) {
                    return;
                }
                if (payload.accept()) {
                    if (isPlayerInActiveGame(player)) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                        return;
                    }
                    removePlayerFromOtherLobbies(player, controller.getBlockPos(), controller.getLevel() != null ? controller.getLevel().dimension() : Level.OVERWORLD);
                    long nowTick = player.serverLevel().getGameTime();
                    controller.acceptInvite(lobbyId, player.getUUID(), player.getGameProfile().getName(), nowTick);
                } else {
                    controller.declineInvite(lobbyId, player.getUUID());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CreateDungeonLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller) || controller.isLocked) {
                    return;
                }
                if (isPlayerInActiveGame(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                    return;
                }
                removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                if (controller.getLobbyByMember(player.getUUID()) != null) {
                    return;
                }
                controller.createLobby(player.getUUID(), player.getGameProfile().getName());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DisbandDungeonLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller) || controller.isLocked) {
                    return;
                }
                controller.disbandLobby(player.getUUID());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(InviteDungeonLobbyPlayerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer owner = context.player();
                Level world = owner.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller) || controller.isLocked) {
                    return;
                }
                Lobby lobby = controller.getLobbyByMember(owner.getUUID());
                if (lobby == null || !lobby.ownerUuid.equals(owner.getUUID())) {
                    return;
                }
                ServerPlayer target = owner.server.getPlayerList().getPlayerByName(payload.playerName());
                if (target == null || target.getUUID().equals(owner.getUUID())) {
                    return;
                }
                long nowTick = owner.serverLevel().getGameTime();
                long expireAt = nowTick + (5L * 60L * 20L);
                if (!controller.invitePlayer(owner.getUUID(), target.getUUID(), expireAt)) {
                    return;
                }
                sendClickableLobbyInvite(target, owner.getGameProfile().getName(), lobby.id);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(KickDungeonLobbyPlayerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer owner = context.player();
                Level world = owner.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller) || controller.isLocked) {
                    return;
                }
                ServerPlayer target = owner.server.getPlayerList().getPlayerByName(payload.playerName());
                if (target == null || target.getUUID().equals(owner.getUUID())) {
                    return;
                }
                controller.kickFromLobby(owner.getUUID(), target.getUUID());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestDungeonControllerInfoPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonControllerBlockEntity controller) {
                    DifficultyTier requestedLeaderboardTier = DifficultyTier.fromNameOrDefault(payload.leaderboardTier(), DifficultyTier.NORMAL);
                    Lobby lobby = controller.getLobbyByMember(player.getUUID());
                    List<String> players = new ArrayList<>();
                    List<DungeonControllerInfoPayload.InstanceView> instances = new ArrayList<>();
                    List<DungeonControllerInfoPayload.LobbyView> lobbies = new ArrayList<>();
                    boolean hardcoreEnabled = controller.hardcoreEnabled;
                    String selectedTier = controller.selectedTier.name();
                    boolean inLobby = lobby != null;
                    boolean isLobbyOwner = false;
                    String lobbyStatus = LobbyStatus.OPEN.name();
                    int queuePosition = 0;
                    String currentLobbyVisibility = LobbyVisibility.OPEN.name();
                    boolean canManageAdmin = player.hasPermissions(2);
                    int controllerRespawnTimeTicks = controller.getRespawnTimeTicks();
                    int controllerMaxPartySize = controller.getMaxPartySize();
                    if (lobby != null) {
                        isLobbyOwner = lobby.ownerUuid.equals(player.getUUID());
                        lobbyStatus = lobby.status.name();
                        currentLobbyVisibility = lobby.visibility.name();
                        if (lobby.status == LobbyStatus.QUEUED) {
                            int pos = 1;
                            for (Lobby candidate : controller.lobbies) {
                                if (candidate.status != LobbyStatus.QUEUED) {
                                    continue;
                                }
                                if (candidate.id.equals(lobby.id)) {
                                    queuePosition = pos;
                                    break;
                                }
                                pos++;
                            }
                        }
                        ServerPlayer owner = player.server.getPlayerList().getPlayer(lobby.ownerUuid);
                        String ownerName = owner != null ? owner.getGameProfile().getName() : lobby.ownerName;
                        if (ownerName == null || ownerName.isEmpty()) {
                            ownerName = "Unknown";
                        }
                        players.add(ownerName);
                        for (UUID uuid : lobby.members) {
                            ServerPlayer partyPlayer = player.server.getPlayerList().getPlayer(uuid);
                            players.add(partyPlayer != null ? partyPlayer.getGameProfile().getName() : "Unknown");
                        }
                        hardcoreEnabled = lobby.hardcoreEnabled;
                        selectedTier = lobby.selectedTier.name();
                    }
                    int queuedPosCounter = 0;
                    for (Lobby candidate : controller.lobbies) {
                        int candidateQueuePosition = 0;
                        if (candidate.status == LobbyStatus.QUEUED) {
                            queuedPosCounter++;
                            candidateQueuePosition = queuedPosCounter;
                        }
                        String ownerName = candidate.ownerName != null && !candidate.ownerName.isEmpty() ? candidate.ownerName : "Unknown";
                        lobbies.add(new DungeonControllerInfoPayload.LobbyView(
                                candidate.id.toString(),
                                ownerName,
                                1 + candidate.members.size(),
                                controller.getMaxPartySize(),
                                candidate.visibility.name(),
                                candidate.status.name(),
                                candidateQueuePosition,
                                candidate.selectedTier.name(),
                                candidate.hardcoreEnabled,
                                candidate.pendingInvites.containsKey(player.getUUID())
                        ));
                    }
                    for (InstanceState instance : controller.instances) {
                        int cooldownSeconds = instance.status() == InstanceStatus.COOLDOWN
                                ? (Math.max(0, instance.cooldownTicksRemaining()) + 19) / 20
                                : 0;
                        instances.add(new DungeonControllerInfoPayload.InstanceView(instance.status().name(), cooldownSeconds));
                    }
                    ServerPlayNetworking.send(player, new DungeonControllerInfoPayload(
                            payload.pos(),
                            controller.remainingDungeonTimeSeconds,
                            controller.dungeonCooldownSeconds,
                            hardcoreEnabled,
                            selectedTier,
                            inLobby,
                            isLobbyOwner,
                            lobbyStatus,
                            queuePosition,
                            currentLobbyVisibility,
                                canManageAdmin,
                                controllerRespawnTimeTicks,
                                controllerMaxPartySize,
                                controller.isLocked,
                                players,
                                resolveLeaderboardForTier(controller, requestedLeaderboardTier, player.server),
                                instances,
                                lobbies
                    ));
                } else {
                    ServerPlayNetworking.send(player, new DungeonControllerInfoPayload(
                            payload.pos(), 0, 0, false, DifficultyTier.NORMAL.name(),
                            false, false, LobbyStatus.OPEN.name(), 0, LobbyVisibility.OPEN.name(), false, 6000, 4, false,
                            List.of(), List.of(), List.of(), List.of()
                    ));
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
                            controller.hardcoreEnabled,
                            players,
                            new ArrayList<>(controller.leaderboard)
                    ));
                } else {
                    ServerPlayNetworking.send(player, new MobArenaControllerInfoPayload(payload.pos(), 0, false, List.of(), List.of()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonControllerSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonControllerBlockEntity controller) {
                    if (controller.isLocked) return;
                    DifficultyTier tier = DifficultyTier.fromNameOrDefault(payload.selectedTier(), DifficultyTier.NORMAL);
                    Lobby lobby = controller.getLobbyByMember(player.getUUID());
                    if (lobby != null && lobby.ownerUuid.equals(player.getUUID())) {
                        lobby.hardcoreEnabled = payload.hardcoreEnabled();
                        lobby.selectedTier = tier;
                        controller.setChanged();
                        world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                    } else if (lobby == null) {
                        // Default values used when creating a new lobby.
                        controller.setHardcoreEnabled(payload.hardcoreEnabled());
                        controller.setSelectedTier(tier);
                        world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonLobbyVisibilityPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller) || controller.isLocked) {
                    return;
                }
                Lobby lobby = controller.getLobbyByMember(player.getUUID());
                if (lobby == null || !lobby.ownerUuid.equals(player.getUUID())) {
                    return;
                }
                LobbyVisibility visibility;
                try {
                    visibility = LobbyVisibility.valueOf(payload.visibility().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return;
                }
                if (controller.setLobbyVisibility(player.getUUID(), visibility)) {
                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonControllerAdminSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller) || controller.isLocked) {
                    return;
                }
                controller.setRespawnTimeTicks(payload.respawnTimeTicks());
                controller.setMaxPartySize(payload.maxPartySize());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaControllerSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof MobArenaControllerBlockEntity controller) {
                    if (controller.isLocked) return;
                    controller.hardcoreEnabled = payload.hardcoreEnabled();
                    controller.setChanged();
                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            });
        });
    }

    private static boolean isPlayerInActiveGame(ServerPlayer player) {
        if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) return true;
        if (ArenasLdMod.DUNGEON_BOSS_MANAGER.getSpawnerForPlayer(player) != null) return true;
        return false;
    }

    private static List<DungeonLeaderboardEntry> resolveLeaderboardForTier(
            DungeonControllerBlockEntity controller,
            DifficultyTier tier,
            MinecraftServer server
    ) {
        if (controller == null || tier == null || server == null) {
            return List.of();
        }
        return new ArrayList<>(controller.getLeaderboardForTier(tier));
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
                if (level.dimension().equals(currentControllerDim) && controller.getBlockPos().equals(currentControllerPos)) {
                    continue;
                }
                if (controller.getLobbyByMember(player.getUUID()) != null) {
                    controller.leaveLobby(player.getUUID());
                }
            }
        }
    }

    private static DungeonControllerBlockEntity findDungeonControllerByLobbyId(net.minecraft.server.MinecraftServer server, UUID lobbyId) {
        for (DungeonControllerBlockEntity.ControllerKey key : DungeonControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(key.pos());
            if (be instanceof DungeonControllerBlockEntity controller && controller.getLobbyById(lobbyId) != null) {
                return controller;
            }
        }
        return null;
    }

    private static void sendClickableLobbyInvite(ServerPlayer target, String ownerName, UUID lobbyId) {
        String acceptCommand = "/arenasld lobby accept " + lobbyId;
        String declineCommand = "/arenasld lobby decline " + lobbyId;
        MutableComponent header = Component.translatable("message.arenas_ld.lobby_invite_from", ownerName);
        MutableComponent accept = Component.translatable("message.arenas_ld.lobby_invite_accept")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, acceptCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(acceptCommand))));
        MutableComponent decline = Component.translatable("message.arenas_ld.lobby_invite_decline")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, declineCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(declineCommand))));
        target.sendSystemMessage(header.append(accept).append(Component.literal(" ")).append(decline));
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
