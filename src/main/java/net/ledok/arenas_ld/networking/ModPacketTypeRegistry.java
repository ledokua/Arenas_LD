package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.ledok.arenas_ld.dungeon.packet.DbsClearRoomsPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsMoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsRemoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.AddDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.AcceptInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.CreateLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.InvitePlayerPayload;
import net.ledok.arenas_ld.dungeon.packet.JoinLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.KickFromLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.LeaveLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.MoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.RemoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearDoorPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearSpawnersPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomRemoveSpawnerPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomResetPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyTierPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyHardcorePayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyVisibilityPayload;
import net.ledok.arenas_ld.dungeon.packet.SetCloseTimerSecondsPayload;
import net.ledok.arenas_ld.dungeon.packet.SetCooldownTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetInviteExpiryTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetMaxPartySizePayload;
import net.ledok.arenas_ld.dungeon.packet.SetTierConfigPayload;
import net.ledok.arenas_ld.dungeon.packet.StartRunPayload;
import net.ledok.arenas_ld.dungeon.packet.ToggleReadyPayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateDbsEntityDefPayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateMobSpawnerEntityDefPayload;

import static net.ledok.arenas_ld.networking.ModPackets.*;

final class ModPacketTypeRegistry {
    private ModPacketTypeRegistry() {
    }

    static void registerC2STypes() {
        PayloadTypeRegistry.playC2S().register(UpdateBossSpawnerPayload.TYPE, UpdateBossSpawnerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateBossSpawnerTierConfigsPayload.TYPE, UpdateBossSpawnerTierConfigsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobArenaSpawnerPayload.TYPE, UpdateMobArenaSpawnerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobArenaMobsPayload.TYPE, UpdateMobArenaMobsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobArenaRewardsPayload.TYPE, UpdateMobArenaRewardsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateAttributesPayload.TYPE, UpdateAttributesPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateEquipmentPayload.TYPE, UpdateEquipmentPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CycleLinkerModePayload.TYPE, CycleLinkerModePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CycleConfiguratorModePayload.TYPE, CycleConfiguratorModePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MobArenaControllerActionPayload.TYPE, MobArenaControllerActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RaidControllerActionPayload.TYPE, RaidControllerActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(JoinRaidLobbyPayload.TYPE, JoinRaidLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RespondRaidLobbyInvitePayload.TYPE, RespondRaidLobbyInvitePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CreateRaidLobbyPayload.TYPE, CreateRaidLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DisbandRaidLobbyPayload.TYPE, DisbandRaidLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(InviteRaidLobbyPlayerPayload.TYPE, InviteRaidLobbyPlayerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestRaidControllerInfoPayload.TYPE, RequestRaidControllerInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestMobArenaControllerInfoPayload.TYPE, RequestMobArenaControllerInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateRaidControllerSettingsPayload.TYPE, UpdateRaidControllerSettingsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateRaidLobbyVisibilityPayload.TYPE, UpdateRaidLobbyVisibilityPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateRaidControllerAdminSettingsPayload.TYPE, UpdateRaidControllerAdminSettingsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobArenaControllerSettingsPayload.TYPE, UpdateMobArenaControllerSettingsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RoomRemoveSpawnerPayload.TYPE, RoomRemoveSpawnerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RoomClearSpawnersPayload.TYPE, RoomClearSpawnersPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RoomClearDoorPayload.TYPE, RoomClearDoorPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RoomResetPayload.TYPE, RoomResetPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateMobSpawnerEntityDefPayload.TYPE, UpdateMobSpawnerEntityDefPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateDbsEntityDefPayload.TYPE, UpdateDbsEntityDefPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DbsRemoveRoomPayload.TYPE, DbsRemoveRoomPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DbsMoveRoomPayload.TYPE, DbsMoveRoomPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DbsClearRoomsPayload.TYPE, DbsClearRoomsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CreateLobbyPayload.TYPE, CreateLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(JoinLobbyPayload.TYPE, JoinLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(InvitePlayerPayload.TYPE, InvitePlayerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AcceptInvitePayload.TYPE, AcceptInvitePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DeclineInvitePayload.TYPE, DeclineInvitePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(LeaveLobbyPayload.TYPE, LeaveLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(KickFromLobbyPayload.TYPE, KickFromLobbyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetLobbyTierPayload.TYPE, SetLobbyTierPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetLobbyHardcorePayload.TYPE, SetLobbyHardcorePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetLobbyVisibilityPayload.TYPE, SetLobbyVisibilityPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ToggleReadyPayload.TYPE, ToggleReadyPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(StartRunPayload.TYPE, StartRunPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AddDungeonInstancePayload.TYPE, AddDungeonInstancePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RemoveDungeonInstancePayload.TYPE, RemoveDungeonInstancePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(MoveDungeonInstancePayload.TYPE, MoveDungeonInstancePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetCooldownTicksPayload.TYPE, SetCooldownTicksPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetCloseTimerSecondsPayload.TYPE, SetCloseTimerSecondsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetMaxPartySizePayload.TYPE, SetMaxPartySizePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetInviteExpiryTicksPayload.TYPE, SetInviteExpiryTicksPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SetTierConfigPayload.TYPE, SetTierConfigPayload.STREAM_CODEC);
    }

    static void registerS2CTypes() {
        PayloadTypeRegistry.playS2C().register(RaidControllerInfoPayload.TYPE, RaidControllerInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(MobArenaControllerInfoPayload.TYPE, MobArenaControllerInfoPayload.STREAM_CODEC);
    }
}
