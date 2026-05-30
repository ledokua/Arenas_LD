package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.run.RaidDifficulty;
import net.ledok.arenas_ld.raid.run.RaidLeaderboardEntry;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.dungeon.run.DungeonLeaderboardEntry;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.arenas_ld.util.LeaderboardEntry;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.ledok.arenas_ld.util.MobArenaRewardData;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModPackets {

    public record UpdateBossSpawnerPayload(
            BlockPos pos, String mobId
    ) implements CustomPacketPayload {
        public static final Type<UpdateBossSpawnerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_boss_spawner"));

        public static final StreamCodec<FriendlyByteBuf, UpdateBossSpawnerPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf), UpdateBossSpawnerPayload::new);

        public UpdateBossSpawnerPayload(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), buf.readUtf());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(mobId);
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


    public record RaidControllerInfoPayload(
            BlockPos pos,
            boolean inLobby,
            boolean isLobbyOwner,
            String lobbyStatus,
            int queuePosition,
            String selectedDifficulty,
            boolean hardcoreEnabled,
            String currentLobbyVisibility,
            boolean canManageAdmin,
            int controllerRespawnTimeTicks,
            int controllerMaxPartySize,
            List<String> players,
            List<RaidLeaderboardEntry> leaderboard,
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
                String difficulty,
                boolean invited
        ) {}

        public static final Type<RaidControllerInfoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_controller_info"));

        public static final StreamCodec<FriendlyByteBuf, RaidControllerInfoPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeBoolean(payload.inLobby);
                    buf.writeBoolean(payload.isLobbyOwner);
                    buf.writeUtf(payload.lobbyStatus);
                    buf.writeVarInt(payload.queuePosition);
                    buf.writeUtf(payload.selectedDifficulty);
                    buf.writeBoolean(payload.hardcoreEnabled);
                    buf.writeUtf(payload.currentLobbyVisibility);
                    buf.writeBoolean(payload.canManageAdmin);
                    buf.writeVarInt(payload.controllerRespawnTimeTicks);
                    buf.writeVarInt(payload.controllerMaxPartySize);
                    buf.writeVarInt(payload.players.size());
                    for (String name : payload.players) {
                        buf.writeUtf(name);
                    }
                    buf.writeVarInt(payload.leaderboard.size());
                    for (RaidLeaderboardEntry entry : payload.leaderboard) {
                        buf.writeUtf(entry.playerName());
                        buf.writeVarInt(entry.timeSeconds());
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
                        buf.writeUtf(lobby.difficulty);
                        buf.writeBoolean(lobby.invited);
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean inLobby = buf.readBoolean();
                    boolean isLobbyOwner = buf.readBoolean();
                    String lobbyStatus = buf.readUtf();
                    int queuePosition = buf.readVarInt();
                    String selectedDifficulty = buf.readUtf();
                    boolean hardcoreEnabled = buf.readBoolean();
                    String currentLobbyVisibility = buf.readUtf();
                    boolean canManageAdmin = buf.readBoolean();
                    int controllerRespawnTimeTicks = buf.readVarInt();
                    int controllerMaxPartySize = buf.readVarInt();
                    int playerCount = buf.readVarInt();
                    List<String> players = new ArrayList<>();
                    for (int i = 0; i < playerCount; i++) {
                        players.add(buf.readUtf());
                    }
                    int leaderboardCount = buf.readVarInt();
                    List<RaidLeaderboardEntry> leaderboard = new ArrayList<>();
                    for (int i = 0; i < leaderboardCount; i++) {
                        leaderboard.add(new RaidLeaderboardEntry(buf.readUtf(), buf.readVarInt()));
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
                                buf.readBoolean()
                        ));
                    }
                    return new RaidControllerInfoPayload(
                            pos,
                            inLobby,
                            isLobbyOwner,
                            lobbyStatus,
                            queuePosition,
                            selectedDifficulty,
                            hardcoreEnabled,
                            currentLobbyVisibility,
                            canManageAdmin,
                            controllerRespawnTimeTicks,
                            controllerMaxPartySize,
                            players,
                            leaderboard,
                            instances,
                            lobbies
                    );
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerC2SPackets() {
        ModPacketTypeRegistry.registerC2STypes();
        net.ledok.arenas_ld.raid.RaidPacketHandlers.register();
        SpawnerPacketHandlers.register();
        net.ledok.arenas_ld.arena.ArenaPacketHandlers.register();

    }

    static boolean isPlayerInActiveGame(ServerPlayer player) {
        return false;
    }

    public static boolean isPlayerBusy(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (BusyStateCompat.isBusy(player.getUUID())) {
            return true;
        }
        return isPlayerInActiveGame(player);
    }

    public static List<RaidLeaderboardEntry> resolveRaidLeaderboardForDifficulty(
            RaidControllerBlockEntity controller,
            RaidDifficulty difficulty
    ) {
        if (controller == null || difficulty == null) {
            return List.of();
        }
        return new ArrayList<>(controller.getLeaderboardForDifficulty(difficulty));
    }

    static void removePlayerFromOtherLobbies(ServerPlayer player, BlockPos currentControllerPos, ResourceKey<Level> currentControllerDim) {
        var server = player.server;
        if (server == null) return;
        for (RaidControllerBlockEntity.ControllerKey key : RaidControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            BlockEntity be = level.getBlockEntity(key.pos());
            if (be instanceof RaidControllerBlockEntity controller) {
                if (level.dimension().equals(currentControllerDim) && controller.getBlockPos().equals(currentControllerPos)) {
                    continue;
                }
                if (controller.getLobbyByMember(player.getUUID()) != null) {
                    controller.leaveLobby(player.getUUID());
                }
            }
        }
    }

    public static RaidControllerBlockEntity findRaidControllerByLobbyId(net.minecraft.server.MinecraftServer server, UUID lobbyId) {
        for (RaidControllerBlockEntity.ControllerKey key : RaidControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(key.pos());
            if (be instanceof RaidControllerBlockEntity controller && controller.getLobbyById(lobbyId) != null) {
                return controller;
            }
        }
        return null;
    }

    static void sendClickableLobbyInvite(ServerPlayer target, String ownerName, UUID lobbyId) {
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
        ModPacketTypeRegistry.registerS2CTypes();
    }

    static String formatSeconds(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
