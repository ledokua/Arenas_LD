package net.ledok.arenas_ld.arena.screen;

import net.ledok.arenas_ld.arena.packet.ArenaLobbyActionPayload;
import net.ledok.arenas_ld.arena.packet.ArenaLobbyTargetPayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetVisibilityPayload;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.rewards.RunModifierOption;
import net.ledok.arenas_ld.screen.lobby.LobbyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The arena controller's lobby screen: {@link net.ledok.arenas_ld.screen.lobby.LobbyView} over the arena payloads.
 * Arenas have no tiers and rank by wave; times are the client's game time (the arena data carries no server tick).
 */
public class ArenaControllerScreen extends LobbyScreen<ArenaControllerScreenHandler> {
    private long instancesTick;

    public ArenaControllerScreen(ArenaControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        instancesTick = clientTick();
    }

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    /** A pushed snapshot. */
    public void applyData(ArenaControllerData data) {
        menu.applyData(data);
        instancesTick = clientTick();
        activeView().dataChanged();
    }

    private ArenaControllerData data() {
        return menu.data();
    }

    private static long clientTick() {
        var level = Minecraft.getInstance().level;
        return level != null ? level.getGameTime() : 0L;
    }

    private static UUID self() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.getUUID() : new UUID(0, 0);
    }

    // ── LobbyHost: data ──────────────────────────────────────────────────────

    @Override public Kind kind() { return Kind.ARENA; }
    @Override public BlockPos controllerPos() { return menu.getBlockPos(); }

    @Override
    public List<Component> meta() {
        List<Component> meta = new ArrayList<>();
        int maxWave = data().maxWave();
        String wave = maxWave < 0 ? Component.translatable("gui.arenas_ld.arena.endless").getString() : String.valueOf(maxWave);
        meta.add(Component.translatable("gui.arenas_ld.arena.max_wave_meta", wave));
        if (!data().requirement().getString().isEmpty()) {
            meta.add(data().requirement());
        }
        if (!data().runModifiers().getString().isEmpty()) {
            meta.add(Component.translatable("message.arenas_ld.reward.lobby", data().runModifiers()));
        }
        return meta;
    }

    @Override
    public List<Lobby> visibleLobbies() {
        UUID ownId = ownLobby().map(Lobby::lobbyId).orElse(null);
        List<Lobby> out = new ArrayList<>();
        for (Lobby lobby : data().lobbies()) {
            if ((ownId != null && lobby.lobbyId().equals(ownId)) || lobby.status() == LobbyStatus.DISBANDED
                || lobby.visibility() == LobbyVisibility.PRIVATE) {
                continue;
            }
            out.add(lobby);
        }
        return out;
    }

    @Override
    public Optional<Lobby> ownLobby() {
        UUID me = self();
        for (Lobby lobby : data().lobbies()) {
            if (lobby.isMember(me)) {
                return Optional.of(lobby);
            }
        }
        return Optional.empty();
    }

    @Override public List<PendingInvite> invites() { return data().myInvites(); }
    @Override public List<PendingJoinRequest> joinRequests() { return data().myJoinRequests(); }
    @Override public int maxPartySize() { return data().maxPartySize(); }
    @Override public int queuePosition() { return ownLobby().isPresent() ? data().queuePosition() : 0; }
    @Override public int etaSeconds() { return 0; }
    @Override public long serverTick() { return clientTick(); }
    @Override public long snapshotTick() { return instancesTick; }

    @Override
    public @Nullable List<Instance> instances() {
        return data().instances().stream().map(i -> new Instance(i.status(), i.cooldownTicksRemaining())).toList();
    }

    @Override public boolean hasTiers() { return false; }
    @Override public boolean tierEnabled(DifficultyTier tier) { return false; }
    @Override public @Nullable Component tierLock(DifficultyTier tier) { return null; }
    @Override public @Nullable Component tierMet(DifficultyTier tier) { return null; }
    @Override public List<RunModifierOption> modifierOptions() { return data().modifierOptions(); }
    @Override public Component runModifiersLine() { return data().runModifiers(); }
    @Override public List<LeaderboardEntry> leaderboard(@Nullable DifficultyTier tier) { return data().leaderboard(); }

    @Override
    public InviteStatus inviteStatus(UUID player) {
        if (data().inRun().contains(player)) return InviteStatus.IN_RUN;
        if (data().inLobby().contains(player)) return InviteStatus.IN_LOBBY;
        return InviteStatus.ONLINE;
    }

    // ── LobbyHost: actions ───────────────────────────────────────────────────

    private void send(CustomPacketPayload payload) {
        ArenasNetwork.sendToServer(payload);
    }

    private BlockPos pos() {
        return menu.getBlockPos();
    }

    @Override public void create() { send(new ArenaLobbyActionPayload(pos(), "create")); }
    @Override public void join(UUID lobbyId) { send(new ArenaLobbyTargetPayload(pos(), "join", lobbyId)); }
    @Override public void request(UUID lobbyId) { send(new ArenaLobbyTargetPayload(pos(), "request", lobbyId)); }
    @Override public void acceptInvite(UUID lobbyId) { send(new ArenaLobbyTargetPayload(pos(), "accept_invite", lobbyId)); }
    @Override public void declineInvite(UUID lobbyId) { send(new ArenaLobbyTargetPayload(pos(), "decline_invite", lobbyId)); }
    @Override public void acceptRequest(UUID requester) { send(new ArenaLobbyTargetPayload(pos(), "accept_request", requester)); }
    @Override public void declineRequest(UUID requester) { send(new ArenaLobbyTargetPayload(pos(), "decline_request", requester)); }
    @Override public void invite(UUID player) { send(new ArenaLobbyTargetPayload(pos(), "invite", player)); }
    @Override public void kick(UUID member) { send(new ArenaLobbyTargetPayload(pos(), "kick", member)); }
    @Override public void toggleReady() { send(new ArenaLobbyActionPayload(pos(), "ready")); }
    @Override public void leave() { send(new ArenaLobbyActionPayload(pos(), "leave")); }
    @Override public void start() { send(new ArenaLobbyActionPayload(pos(), "start")); }
    @Override public void setTier(DifficultyTier tier) { }
    @Override public void setVisibility(LobbyVisibility visibility) { send(new ArenaSetVisibilityPayload(pos(), visibility.name())); }
}
