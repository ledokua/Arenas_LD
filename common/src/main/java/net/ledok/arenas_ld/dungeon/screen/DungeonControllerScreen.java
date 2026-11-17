package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.packet.AcceptInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.AcceptJoinRequestPayload;
import net.ledok.arenas_ld.dungeon.packet.CreateLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineJoinRequestPayload;
import net.ledok.arenas_ld.dungeon.packet.InvitePlayerPayload;
import net.ledok.arenas_ld.dungeon.packet.JoinLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.KickFromLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.LeaveLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.RequestJoinPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyTierPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyVisibilityPayload;
import net.ledok.arenas_ld.dungeon.packet.StartRunPayload;
import net.ledok.arenas_ld.dungeon.packet.ToggleReadyPayload;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.platform.ArenasNetwork;
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

/** The dungeon controller's lobby screen: {@link net.ledok.arenas_ld.screen.lobby.LobbyView} over the dungeon payloads. */
public class DungeonControllerScreen extends LobbyScreen<DungeonControllerScreenHandler> {

    public DungeonControllerScreen(DungeonControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        snapshot(handler.getServerGameTick());
    }

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    /** A snapshot from the server. */
    public void applyData(DungeonControllerData data) {
        menu.applyData(data);
        snapshot(menu.getServerGameTick());
        activeView().dataChanged();
    }

    // ── LobbyHost: data ──────────────────────────────────────────────────────

    @Override public Kind kind() { return Kind.DUNGEON; }
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
    @Override public @Nullable List<Instance> instances() { return null; }
    @Override public boolean hasTiers() { return true; }

    @Override
    public boolean tierEnabled(DifficultyTier tier) {
        net.ledok.arenas_ld.dungeon.run.TierConfig config = menu.getTiers().get(tier);
        return config == null || config.enabled();
    }

    @Override public @Nullable Component tierLock(DifficultyTier tier) { return menu.getLockedTiers().get(tier); }
    @Override public @Nullable Component tierMet(DifficultyTier tier) { return menu.getMetRequirements().get(tier); }
    @Override public List<RunModifierOption> modifierOptions() { return menu.getModifierOptions(); }
    @Override public Component runModifiersLine() { return menu.getRunModifiers(); }

    @Override
    public List<LeaderboardEntry> leaderboard(@Nullable DifficultyTier tier) {
        return tier == null ? List.of() : menu.getTopLeaderboards().getOrDefault(tier, List.of());
    }

    @Override public Set<UUID> busyPlayers() { return menu.getBusyPlayers() == null ? Set.of() : menu.getBusyPlayers(); }

    // ── LobbyHost: actions ───────────────────────────────────────────────────

    private BlockPos pos() {
        return menu.getBlockPos();
    }

    @Override public void create() { ArenasNetwork.sendToServer(new CreateLobbyPayload(pos())); }
    @Override public void join(UUID lobbyId) { ArenasNetwork.sendToServer(new JoinLobbyPayload(pos(), lobbyId)); }
    @Override public void request(UUID lobbyId) { ArenasNetwork.sendToServer(new RequestJoinPayload(pos(), lobbyId)); }
    @Override public void acceptInvite(UUID lobbyId) { ArenasNetwork.sendToServer(new AcceptInvitePayload(pos(), lobbyId)); }
    @Override public void declineInvite(UUID lobbyId) { ArenasNetwork.sendToServer(new DeclineInvitePayload(pos(), lobbyId)); }
    @Override public void acceptRequest(UUID requester) { ArenasNetwork.sendToServer(new AcceptJoinRequestPayload(pos(), requester)); }
    @Override public void declineRequest(UUID requester) { ArenasNetwork.sendToServer(new DeclineJoinRequestPayload(pos(), requester)); }
    @Override public void invite(UUID player) { ArenasNetwork.sendToServer(new InvitePlayerPayload(pos(), player)); }
    @Override public void kick(UUID member) { ArenasNetwork.sendToServer(new KickFromLobbyPayload(pos(), member)); }
    @Override public void toggleReady() { ArenasNetwork.sendToServer(new ToggleReadyPayload(pos())); }
    @Override public void leave() { ArenasNetwork.sendToServer(new LeaveLobbyPayload(pos())); }
    @Override public void start() { ArenasNetwork.sendToServer(new StartRunPayload(pos())); }
    @Override public void setTier(DifficultyTier tier) { ArenasNetwork.sendToServer(new SetLobbyTierPayload(pos(), tier)); }
    @Override public void setVisibility(LobbyVisibility visibility) { ArenasNetwork.sendToServer(new SetLobbyVisibilityPayload(pos(), visibility)); }
}
