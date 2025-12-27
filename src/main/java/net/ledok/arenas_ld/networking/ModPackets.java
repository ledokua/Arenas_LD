package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.manager.DungeonManager;
import net.ledok.arenas_ld.manager.LobbyManager;
import net.ledok.arenas_ld.screen.*;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.EquipmentData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ModPackets {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> buf.writeUUID(uuid),
            buf -> buf.readUUID()
    );

    // --- Existing Payloads ---
    public record UpdateMobSpawnerPayload(
            BlockPos pos, String mobId, int respawnTime, int triggerRadius, int mobCount, int mobSpread, String groupId
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UpdateMobSpawnerPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_mob_spawner"));
        public static final StreamCodec<FriendlyByteBuf, UpdateMobSpawnerPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos);
                    buf.writeUtf(payload.mobId);
                    buf.writeInt(payload.respawnTime);
                    buf.writeInt(payload.triggerRadius);
                    buf.writeInt(payload.mobCount);
                    buf.writeInt(payload.mobSpread);
                    buf.writeUtf(payload.groupId);
                },
                (buf) -> new UpdateMobSpawnerPayload(
                        buf.readBlockPos(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf()
                )
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateBossSpawnerPayload(
            BlockPos pos, String mobId, int respawnTime, int portalActiveTime,
            String lootTableId, String perPlayerLootTableId, BlockPos exitPortalCoords,
            BlockPos enterPortalSpawnCoords, BlockPos enterPortalDestCoords, int triggerRadius,
            int battleRadius, int regeneration, int minPlayers, int skillExperiencePerWin, String groupId
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UpdateBossSpawnerPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_boss_spawner"));
        public static final StreamCodec<FriendlyByteBuf, UpdateBossSpawnerPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos); buf.writeUtf(payload.mobId); buf.writeInt(payload.respawnTime);
                    buf.writeInt(payload.portalActiveTime); buf.writeUtf(payload.lootTableId); buf.writeUtf(payload.perPlayerLootTableId);
                    buf.writeBlockPos(payload.exitPortalCoords); buf.writeBlockPos(payload.enterPortalSpawnCoords);
                    buf.writeBlockPos(payload.enterPortalDestCoords); buf.writeInt(payload.triggerRadius); buf.writeInt(payload.battleRadius);
                    buf.writeInt(payload.regeneration); buf.writeInt(payload.minPlayers); buf.writeInt(payload.skillExperiencePerWin);
                    buf.writeUtf(payload.groupId);
                },
                (buf) -> new UpdateBossSpawnerPayload(
                        buf.readBlockPos(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readUtf(), buf.readUtf(),
                        buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf()
                )
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateDungeonBossSpawnerPayload(
            BlockPos pos, String mobId, int respawnTime, int dungeonCloseTimer,
            String lootTableId, String perPlayerLootTableId, BlockPos exitPositionCoords,
            BlockPos enterPortalSpawnCoords, BlockPos enterPortalDestCoords, int triggerRadius,
            int battleRadius, int regeneration, int skillExperiencePerWin, String groupId
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UpdateDungeonBossSpawnerPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_dungeon_boss_spawner"));
        public static final StreamCodec<FriendlyByteBuf, UpdateDungeonBossSpawnerPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeBlockPos(payload.pos); buf.writeUtf(payload.mobId); buf.writeInt(payload.respawnTime);
                    buf.writeInt(payload.dungeonCloseTimer); buf.writeUtf(payload.lootTableId); buf.writeUtf(payload.perPlayerLootTableId);
                    buf.writeBlockPos(payload.exitPositionCoords); buf.writeBlockPos(payload.enterPortalSpawnCoords);
                    buf.writeBlockPos(payload.enterPortalDestCoords); buf.writeInt(payload.triggerRadius); buf.writeInt(payload.battleRadius);
                    buf.writeInt(payload.regeneration); buf.writeInt(payload.skillExperiencePerWin); buf.writeUtf(payload.groupId);
                },
                (buf) -> new UpdateDungeonBossSpawnerPayload(
                        buf.readBlockPos(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readUtf(), buf.readUtf(),
                        buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readUtf()
                )
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateAttributesPayload(BlockPos pos, List<AttributeData> attributes) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UpdateAttributesPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_attributes"));
        public static final StreamCodec<FriendlyByteBuf, UpdateAttributesPayload> CODEC = StreamCodec.of(
                (buf, payload) -> { buf.writeBlockPos(payload.pos); buf.writeCollection(payload.attributes, (b, a) -> a.write(b)); },
                (buf) -> new UpdateAttributesPayload(buf.readBlockPos(), buf.readList(AttributeData::read))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateEquipmentPayload(BlockPos pos, EquipmentData equipment) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UpdateEquipmentPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_equipment"));
        public static final StreamCodec<FriendlyByteBuf, UpdateEquipmentPayload> CODEC = StreamCodec.of(
                (buf, payload) -> { buf.writeBlockPos(payload.pos); payload.equipment.write(buf); },
                (buf) -> new UpdateEquipmentPayload(buf.readBlockPos(), EquipmentData.read(buf))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenCreateLobbyScreenPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenCreateLobbyScreenPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "open_create_lobby_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenCreateLobbyScreenPayload> CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new OpenCreateLobbyScreenPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // --- Lobby Payloads ---
    public record CreateLobbyRequestPayload(String dungeonName) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CreateLobbyRequestPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "create_lobby_request"));
        public static final StreamCodec<FriendlyByteBuf, CreateLobbyRequestPayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeUtf(payload.dungeonName()),
                (buf) -> new CreateLobbyRequestPayload(buf.readUtf())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record DisbandLobbyRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisbandLobbyRequestPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "disband_lobby_request"));
        public static final StreamCodec<FriendlyByteBuf, DisbandLobbyRequestPayload> CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new DisbandLobbyRequestPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StartLobbyRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StartLobbyRequestPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "start_lobby_request"));
        public static final StreamCodec<FriendlyByteBuf, StartLobbyRequestPayload> CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new StartLobbyRequestPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    
    public record OpenDungeonLobbyScreenPayload(UUID lobbyId, UUID ownerId, List<String> playerNames) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenDungeonLobbyScreenPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "open_dungeon_lobby_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenDungeonLobbyScreenPayload> CODEC = StreamCodec.composite(
                UUID_CODEC, OpenDungeonLobbyScreenPayload::lobbyId,
                UUID_CODEC, OpenDungeonLobbyScreenPayload::ownerId,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenDungeonLobbyScreenPayload::playerNames,
                OpenDungeonLobbyScreenPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateLobbyStatePayload(List<String> playerNames) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<UpdateLobbyStatePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "update_lobby_state"));
        public static final StreamCodec<FriendlyByteBuf, UpdateLobbyStatePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), UpdateLobbyStatePayload::playerNames,
                UpdateLobbyStatePayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CloseLobbyScreenPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CloseLobbyScreenPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "close_lobby_screen"));
        public static final StreamCodec<FriendlyByteBuf, CloseLobbyScreenPayload> CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new CloseLobbyScreenPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RequestLobbyListPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestLobbyListPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "request_lobby_list"));
        public static final StreamCodec<FriendlyByteBuf, RequestLobbyListPayload> CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new RequestLobbyListPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LobbyListPayload(List<JoinLobbyData.LobbyInfo> lobbies) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LobbyListPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "lobby_list"));
        public static final StreamCodec<FriendlyByteBuf, LobbyListPayload> CODEC = StreamCodec.composite(
                JoinLobbyData.LobbyInfo.CODEC.apply(ByteBufCodecs.list()), LobbyListPayload::lobbies,
                LobbyListPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record JoinLobbyRequestPayload(UUID lobbyId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<JoinLobbyRequestPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "join_lobby_request"));
        public static final StreamCodec<FriendlyByteBuf, JoinLobbyRequestPayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeUUID(payload.lobbyId()),
                (buf) -> new JoinLobbyRequestPayload(buf.readUUID())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LeaveLobbyRequestPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LeaveLobbyRequestPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "leave_lobby_request"));
        public static final StreamCodec<FriendlyByteBuf, LeaveLobbyRequestPayload> CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new LeaveLobbyRequestPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StartLobbyCountdownPayload(int durationTicks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StartLobbyCountdownPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "start_lobby_countdown"));
        public static final StreamCodec<FriendlyByteBuf, StartLobbyCountdownPayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeInt(payload.durationTicks()),
                (buf) -> new StartLobbyCountdownPayload(buf.readInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }


    public static void registerC2SPackets() {
        // Existing
        PayloadTypeRegistry.playC2S().register(UpdateMobSpawnerPayload.TYPE, UpdateMobSpawnerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateBossSpawnerPayload.TYPE, UpdateBossSpawnerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDungeonBossSpawnerPayload.TYPE, UpdateDungeonBossSpawnerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateAttributesPayload.TYPE, UpdateAttributesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateEquipmentPayload.TYPE, UpdateEquipmentPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenCreateLobbyScreenPayload.TYPE, OpenCreateLobbyScreenPayload.CODEC);
        
        // Lobby
        PayloadTypeRegistry.playC2S().register(CreateLobbyRequestPayload.TYPE, CreateLobbyRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DisbandLobbyRequestPayload.TYPE, DisbandLobbyRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StartLobbyRequestPayload.TYPE, StartLobbyRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestLobbyListPayload.TYPE, RequestLobbyListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(JoinLobbyRequestPayload.TYPE, JoinLobbyRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LeaveLobbyRequestPayload.TYPE, LeaveLobbyRequestPayload.CODEC);

        // --- Receivers ---
        ServerPlayNetworking.registerGlobalReceiver(UpdateMobSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos) instanceof MobSpawnerBlockEntity spawner) {
                    spawner.mobId = payload.mobId; spawner.respawnTime = payload.respawnTime;
                    spawner.triggerRadius = payload.triggerRadius; spawner.mobCount = payload.mobCount;
                    spawner.mobSpread = payload.mobSpread; spawner.groupId = payload.groupId;
                    spawner.setChanged();
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateBossSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos) instanceof BossSpawnerBlockEntity spawner) {
                    spawner.mobId = payload.mobId; spawner.respawnTime = payload.respawnTime;
                    spawner.portalActiveTime = payload.portalActiveTime; spawner.lootTableId = payload.lootTableId;
                    spawner.perPlayerLootTableId = payload.perPlayerLootTableId; spawner.exitPortalCoords = payload.exitPortalCoords;
                    spawner.enterPortalSpawnCoords = payload.enterPortalSpawnCoords; spawner.enterPortalDestCoords = payload.enterPortalDestCoords;
                    spawner.triggerRadius = payload.triggerRadius; spawner.battleRadius = payload.battleRadius;
                    spawner.regeneration = payload.regeneration; spawner.minPlayers = payload.minPlayers;
                    spawner.skillExperiencePerWin = payload.skillExperiencePerWin; spawner.groupId = payload.groupId;
                    spawner.setChanged();
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonBossSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos) instanceof DungeonBossSpawnerBlockEntity spawner) {
                    spawner.mobId = payload.mobId; spawner.respawnTime = payload.respawnTime;
                    spawner.dungeonCloseTimer = payload.dungeonCloseTimer; spawner.lootTableId = payload.lootTableId;
                    spawner.perPlayerLootTableId = payload.perPlayerLootTableId;
                    spawner.exitPositionCoords = payload.exitPositionCoords;
                    spawner.enterPortalSpawnCoords = payload.enterPortalSpawnCoords;
                    spawner.enterPortalDestCoords = payload.enterPortalDestCoords;
                    spawner.triggerRadius = payload.triggerRadius;
                    spawner.battleRadius = payload.battleRadius;
                    spawner.regeneration = payload.regeneration;
                    spawner.skillExperiencePerWin = payload.skillExperiencePerWin;
                    spawner.groupId = payload.groupId;
                    spawner.setChanged();
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateAttributesPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                BlockEntity be = context.player().level().getBlockEntity(payload.pos);
                if (be instanceof DungeonBossSpawnerBlockEntity spawner) spawner.setAttributes(payload.attributes);
                else if (be instanceof BossSpawnerBlockEntity spawner) spawner.setAttributes(payload.attributes);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateEquipmentPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                BlockEntity be = context.player().level().getBlockEntity(payload.pos);
                if (be instanceof DungeonBossSpawnerBlockEntity spawner) spawner.setEquipment(payload.equipment);
                else if (be instanceof BossSpawnerBlockEntity spawner) spawner.setEquipment(payload.equipment);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenCreateLobbyScreenPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                player.openMenu(new ExtendedScreenHandlerFactory<CreateLobbyData>() {
                    @Override public CreateLobbyData getScreenOpeningData(ServerPlayer player) {
                        List<CreateLobbyData.DungeonStatus> statuses = new ArrayList<>();
                        for (DungeonManager.DungeonEntry dungeon : DungeonManager.getDungeons()) {
                            BlockEntity be = player.level().getBlockEntity(dungeon.pos());
                            int cooldown = 0;
                            if (be instanceof DungeonBossSpawnerBlockEntity spawner) {
                                cooldown = spawner.respawnCooldown;
                            } else if (be instanceof BossSpawnerBlockEntity spawner) {
                                cooldown = spawner.respawnCooldown;
                            }

                            CreateLobbyData.Status status;
                            if (LobbyManager.isDungeonLocked(dungeon.name())) {
                                status = CreateLobbyData.Status.LOCKED;
                            } else if (cooldown > 0) {
                                status = CreateLobbyData.Status.COOLDOWN;
                            } else {
                                status = CreateLobbyData.Status.AVAILABLE;
                            }
                            statuses.add(new CreateLobbyData.DungeonStatus(dungeon.name(), status, cooldown));
                        }
                        return new CreateLobbyData(statuses);
                    }
                    @Override public Component getDisplayName() { return Component.literal("Create Lobby"); }
                    @Nullable @Override public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
                        return new CreateLobbyScreenHandler(i, inventory, Collections.emptyList());
                    }
                });
            });
        });
        
        ServerPlayNetworking.registerGlobalReceiver(CreateLobbyRequestPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                DungeonManager.getDungeon(payload.dungeonName()).ifPresent(dungeon -> {
                    LobbyManager.Lobby lobby = LobbyManager.createLobby(player, dungeon);
                    if (lobby != null) {
                        sendOpenLobbyScreen(player, lobby);
                    }
                });
            });
        });
        
        ServerPlayNetworking.registerGlobalReceiver(DisbandLobbyRequestPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                LobbyManager.Lobby lobby = LobbyManager.getLobbyForPlayer(player.getUUID());
                if (lobby != null && lobby.getOwnerId().equals(player.getUUID())) {
                    for (UUID playerId : lobby.getPlayers()) {
                        ServerPlayer member = context.server().getPlayerList().getPlayer(playerId);
                        if (member != null) {
                            ServerPlayNetworking.send(member, new CloseLobbyScreenPayload());
                        }
                    }
                    LobbyManager.disbandLobby(lobby.getId());
                }
            });
        });
        
        ServerPlayNetworking.registerGlobalReceiver(StartLobbyRequestPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                LobbyManager.Lobby lobby = LobbyManager.getLobbyForPlayer(player.getUUID());
                if (lobby != null && lobby.getOwnerId().equals(player.getUUID())) {
                    
                    // Send countdown to all players
                    for (UUID playerId : lobby.getPlayers()) {
                        ServerPlayer member = context.server().getPlayerList().getPlayer(playerId);
                        if (member != null) {
                            ServerPlayNetworking.send(member, new StartLobbyCountdownPayload(200)); // 10 seconds
                        }
                    }

                    // Schedule the teleportation
                    scheduler.schedule(() -> {
                        context.server().execute(() -> {
                            DungeonManager.DungeonEntry dungeon = lobby.getDungeon();
                            BlockEntity be = player.level().getBlockEntity(dungeon.pos());
                            
                            if (be instanceof DungeonBossSpawnerBlockEntity spawner) {
                                for (UUID playerId : lobby.getPlayers()) {
                                    ServerPlayer member = context.server().getPlayerList().getPlayer(playerId);
                                    if (member != null) {
                                        member.teleportTo(spawner.enterPortalDestCoords.getX() + 0.5, spawner.enterPortalDestCoords.getY(), spawner.enterPortalDestCoords.getZ() + 0.5);
                                        spawner.trackPlayer(playerId);
                                        ServerPlayNetworking.send(member, new CloseLobbyScreenPayload());
                                    }
                                }
                                LobbyManager.disbandLobby(lobby.getId());
                            }
                        });
                    }, 10, TimeUnit.SECONDS);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestLobbyListPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                List<JoinLobbyData.LobbyInfo> lobbyInfos = new ArrayList<>();
                for (LobbyManager.Lobby lobby : LobbyManager.getLobbies()) {
                    ServerPlayer owner = context.server().getPlayerList().getPlayer(lobby.getOwnerId());
                    if (owner != null) {
                        lobbyInfos.add(new JoinLobbyData.LobbyInfo(
                                lobby.getId(),
                                lobby.getDungeon().name(),
                                owner.getName().getString(),
                                lobby.getPlayers().size(),
                                10 // TODO: Make max players configurable
                        ));
                    }
                }
                ServerPlayNetworking.send(context.player(), new LobbyListPayload(lobbyInfos));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(JoinLobbyRequestPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                LobbyManager.addPlayer(player, payload.lobbyId());
                LobbyManager.Lobby lobby = LobbyManager.getLobby(payload.lobbyId());
                if (lobby != null) {
                    sendOpenLobbyScreen(player, lobby);
                    updateLobbyState(lobby, context.server());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(LeaveLobbyRequestPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                LobbyManager.removePlayerFromCurrentLobby(player);
                ServerPlayNetworking.send(player, new CloseLobbyScreenPayload());
            });
        });
    }
    
    public static void registerS2CPackets() {
        PayloadTypeRegistry.playS2C().register(OpenDungeonLobbyScreenPayload.TYPE, OpenDungeonLobbyScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdateLobbyStatePayload.TYPE, UpdateLobbyStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseLobbyScreenPayload.TYPE, CloseLobbyScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LobbyListPayload.TYPE, LobbyListPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StartLobbyCountdownPayload.TYPE, StartLobbyCountdownPayload.CODEC);
    }

    public static void sendOpenLobbyScreen(ServerPlayer player, LobbyManager.Lobby lobby) {
        List<String> playerNames = lobby.getPlayers().stream()
                .map(uuid -> player.getServer().getPlayerList().getPlayer(uuid))
                .filter(p -> p != null)
                .map(p -> p.getName().getString())
                .collect(Collectors.toList());
        
        ServerPlayNetworking.send(player, new OpenDungeonLobbyScreenPayload(lobby.getId(), lobby.getOwnerId(), playerNames));
    }

    public static void updateLobbyState(LobbyManager.Lobby lobby, MinecraftServer server) {
        List<String> playerNames = lobby.getPlayers().stream()
                .map(uuid -> server.getPlayerList().getPlayer(uuid))
                .filter(p -> p != null)
                .map(p -> p.getName().getString())
                .collect(Collectors.toList());

        UpdateLobbyStatePayload payload = new UpdateLobbyStatePayload(playerNames);
        for (UUID playerId : lobby.getPlayers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(playerId);
            if (member != null) {
                ServerPlayNetworking.send(member, payload);
            }
        }
    }
}
