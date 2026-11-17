package net.ledok.arenas_ld.raid.screen;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.raid.packet.RaidAcceptInvitePayload;
import net.ledok.arenas_ld.raid.packet.RaidAcceptJoinRequestPayload;
import net.ledok.arenas_ld.raid.packet.RaidCreateLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidDeclineInvitePayload;
import net.ledok.arenas_ld.raid.packet.RaidDeclineJoinRequestPayload;
import net.ledok.arenas_ld.raid.packet.RaidInvitePlayerPayload;
import net.ledok.arenas_ld.raid.packet.RaidJoinLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidKickFromLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidLeaveLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidRequestJoinPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLobbyTierPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLobbyVisibilityPayload;
import net.ledok.arenas_ld.raid.packet.RaidStartRunPayload;
import net.ledok.arenas_ld.raid.packet.RaidToggleReadyPayload;
import net.ledok.arenas_ld.rewards.RunModifierOption;
import net.ledok.arenas_ld.screen.lobby.LobbyScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The raid controller's lobby screen: {@link net.ledok.arenas_ld.screen.lobby.LobbyView} over the raid payloads. */
public class RaidControllerScreen extends LobbyScreen<RaidControllerScreenHandler> {

    public RaidControllerScreen(RaidControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        snapshot(handler.getServerGameTick());
    }

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    /** A full snapshot from the server. */
    public void applyData(RaidControllerData data) {
        menu.applyData(data);
        snapshot(menu.getServerGameTick());
        activeView().dataChanged();
    }

    /** Legacy info packet: just redraw with the current data. */
    public void applyServerInfo(ModPackets.RaidControllerInfoPayload payload) {
        activeView().build();
    }

    // ── LobbyHost: data ──────────────────────────────────────────────────────

    @Override public Kind kind() { return Kind.RAID; }
    @Override public BlockPos controllerPos() { return menu.getBlockPos(); }

    @Override
    public List<Component> meta() {
        Component modifiers = menu.getRunModifiers();
        List<Component> meta = new java.util.ArrayList<>();
        if (!modifiers.getString().isEmpty()) {
            meta.add(Component.translatable("message.arenas_ld.reward.lobby", modifiers));
        }
        meta.addAll(net.ledok.arenas_ld.screen.RentalPick.settingsMeta(menu.getRunSettings()));
        return meta;
    }

    @Override public List<Lobby> visibleLobbies() { return menu.getVisibleLobbies(); }
    @Override public Optional<Lobby> ownLobby() { return menu.getOwnLobby(); }
    @Override public List<PendingInvite> invites() { return menu.getMyInvites(); }
    @Override public List<PendingJoinRequest> joinRequests() { return menu.getMyJoinRequests(); }
    @Override public int maxPartySize() { return menu.getMaxPartySize(); }
    @Override public int queuePosition() { return menu.getQueuePosition(); }
    @Override public int etaSeconds() { return menu.getEstimatedWaitSeconds(); }

    @Override
    public @Nullable List<Instance> instances() {
        return menu.getInstances().stream().map(i -> new Instance(i.status(), i.cooldownTicks())).toList();
    }

    @Override public boolean hasTiers() { return true; }
    @Override public boolean tierEnabled(DifficultyTier tier) { return menu.getEnabledTiers().contains(tier); }
    @Override public @Nullable Component tierLock(DifficultyTier tier) { return menu.getLockedTiers().get(tier); }
    @Override public @Nullable Component tierMet(DifficultyTier tier) { return menu.getMetRequirements().get(tier); }
    @Override public List<RunModifierOption> modifierOptions() { return menu.getModifierOptions(); }
    @Override public Component runModifiersLine() { return menu.getRunModifiers(); }

    @Override
    public List<LeaderboardEntry> leaderboard(@Nullable DifficultyTier tier) {
        return tier == null ? List.of() : menu.getTopLeaderboards().getOrDefault(tier, List.of());
    }

    @Override public Set<UUID> busyPlayers() { return menu.getBusyPlayers() == null ? Set.of() : menu.getBusyPlayers(); }
    @Override public boolean queueStartButton() { return true; }

    // ── LobbyHost: actions ───────────────────────────────────────────────────

    private BlockPos pos() {
        return menu.getBlockPos();
    }

    @Override public void create() { ArenasNetwork.sendToServer(new RaidCreateLobbyPayload(pos())); }
    @Override public void join(UUID lobbyId) { ArenasNetwork.sendToServer(new RaidJoinLobbyPayload(pos(), lobbyId)); }
    @Override public void request(UUID lobbyId) { ArenasNetwork.sendToServer(new RaidRequestJoinPayload(pos(), lobbyId)); }
    @Override public void acceptInvite(UUID lobbyId) { ArenasNetwork.sendToServer(new RaidAcceptInvitePayload(pos(), lobbyId)); }
    @Override public void declineInvite(UUID lobbyId) { ArenasNetwork.sendToServer(new RaidDeclineInvitePayload(pos(), lobbyId)); }
    @Override public void acceptRequest(UUID requester) { ArenasNetwork.sendToServer(new RaidAcceptJoinRequestPayload(pos(), requester)); }
    @Override public void declineRequest(UUID requester) { ArenasNetwork.sendToServer(new RaidDeclineJoinRequestPayload(pos(), requester)); }
    @Override public void invite(UUID player) { ArenasNetwork.sendToServer(new RaidInvitePlayerPayload(pos(), player)); }
    @Override public void kick(UUID member) { ArenasNetwork.sendToServer(new RaidKickFromLobbyPayload(pos(), member)); }
    @Override public void toggleReady() { ArenasNetwork.sendToServer(new RaidToggleReadyPayload(pos())); }
    @Override public void leave() { ArenasNetwork.sendToServer(new RaidLeaveLobbyPayload(pos())); }
    @Override public void start() { ArenasNetwork.sendToServer(new RaidStartRunPayload(pos())); }
    @Override public void setTier(DifficultyTier tier) { ArenasNetwork.sendToServer(new RaidSetLobbyTierPayload(pos(), tier)); }
    @Override public void setVisibility(LobbyVisibility visibility) { ArenasNetwork.sendToServer(new RaidSetLobbyVisibilityPayload(pos(), visibility)); }
}
