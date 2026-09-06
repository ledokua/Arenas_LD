package net.ledok.arenas_ld.arena.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.packet.ArenaAdminSetBoolPayload;
import net.ledok.arenas_ld.arena.packet.ArenaAdminSetIntPayload;
import net.ledok.arenas_ld.arena.packet.ArenaMoveInstancePayload;
import net.ledok.arenas_ld.arena.packet.ArenaRemoveInstancePayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetRewardCurvePayload;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.presentation.CanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT_DARK;
import static net.ledok.arenas_ld.screen.ArenasUi.BG;
import static net.ledok.arenas_ld.screen.ArenasUi.DANGER;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE_HI;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;
import static net.ledok.arenas_ld.screen.ArenasUi.ROW_BG;

/** Admin/config view for the arena controller. Reads the client-synced controller; edits via packets. */
public class ArenaControllerAdminScreen extends CanvasHandledScreen<ArenaControllerAdminScreenHandler> {
    private final BlockPos pos;
    private Flex dynamicArea;
    private final Map<String, TextField> intFields = new LinkedHashMap<>();
    private TextField currencyBase, currencyExp, xpBase, xpExp;
    private int lastInstanceSig = Integer.MIN_VALUE;

    public ArenaControllerAdminScreen(ArenaControllerAdminScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(600, 340), Placement.Screen.center());
        this.pos = handler.getBlockPos();
        canvas.theme(ArenasUi.THEME);
        dimBackground(false);
        fillWindow();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The old owo screen swallowed the inventory key entirely (close via × or Esc only).
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)
                && !(input.focusedNode() instanceof TextField)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private ArenaControllerBlockEntity controller() {
        return this.menu.controller;
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        buildAll();
    }

    private void buildAll() {
        canvas.clear();
        intFields.clear();
        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);
        root.backgroundFill(BG);

        float w = Math.max(480, Math.min(620, canvas.width() - 24));
        float h = Math.max(300, canvas.height() - 24);
        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(w), Sizing.fixed(h));
        shell.backgroundFill(PANEL, HAIRLINE_HI, 1);
        shell.item(header());

        Flex content = Flex.column().gap(6).padding(Insets.of(10));
        content.sizing(Sizing.fill(), Sizing.content());
        ArenaControllerBlockEntity c = controller();
        if (c == null) {
            content.item(ArenasUi.text(Component.translatable("gui.arenas_ld.arena.not_loaded"), DANGER));
        } else {
            content.item(buildSettings(c));
            dynamicArea = content.item(Flex.column().gap(4));
            dynamicArea.sizing(Sizing.fill(), Sizing.content());
        }
        ScrollPanel scroll = new ScrollPanel(100, 100, content);
        scroll.sizing(Sizing.fill(), Sizing.expand());
        shell.item(scroll);

        root.layoutIn(canvas.width(), canvas.height());
        rebuildDynamic();
    }

    @Override
    protected void onCanvasFrame(float partialTick) {
        ArenaControllerBlockEntity c = controller();
        int sig = c == null ? 0 : c.getInstances().size() * 31 + c.getLeaderboard().size();
        if (sig != lastInstanceSig) rebuildDynamic();
    }

    private Flex buildSettings(ArenaControllerBlockEntity c) {
        Flex box = Flex.column().gap(4);
        box.sizing(Sizing.fill(), Sizing.content());
        box.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.settings")));
        box.item(intField("maxPartySize", Component.translatable("gui.arenas_ld.arena.max_party"), c.getMaxPartySize()));
        box.item(intField("maxWave", Component.translatable("gui.arenas_ld.arena.max_wave"), c.getMaxWave()));
        box.item(intField("closeTimer", Component.translatable("gui.arenas_ld.arena.close_timer_seconds"), c.getCloseTimerSeconds()));
        box.item(intField("cooldown", Component.translatable("gui.arenas_ld.arena.cooldown_ticks"), c.getCooldownTicks()));
        box.item(intField("respawnTime", Component.translatable("gui.arenas_ld.arena.respawn_ticks"), c.getRespawnTimeTicks()));
        box.item(intField("inviteExpiry", Component.translatable("gui.arenas_ld.arena.invite_expiry_ticks"), c.getInviteExpiryTicks()));
        box.item(intField("deathPenalty", Component.translatable("gui.arenas_ld.arena.death_penalty_ticks"), c.getDeathTimePenaltyTicks()));
        box.item(intField("hpScalePct", Component.translatable("gui.arenas_ld.arena.hp_scale_pct"), (int) Math.round(c.getHpScalePerPlayer() * 100.0)));
        box.item(intField("hpWavePct", Component.translatable("gui.arenas_ld.arena.hp_wave_pct"), (int) Math.round(c.getHpScalePerWave() * 100.0)));

        box.item(ArenasUi.spacer(4));
        box.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.reward_curve")));
        Flex curveRow = Flex.row().gap(6);
        curveRow.sizing(Sizing.fill(), Sizing.content());
        currencyBase = labeledBox(curveRow, Component.literal("$ base"), str(c.getRewardCurrencyBase()));
        currencyExp = labeledBox(curveRow, Component.literal("$ exp"), str(c.getRewardCurrencyExp()));
        xpBase = labeledBox(curveRow, Component.literal("xp base"), str(c.getRewardXpBase()));
        xpExp = labeledBox(curveRow, Component.literal("xp exp"), str(c.getRewardXpExp()));
        box.item(curveRow);

        box.item(ArenasUi.spacer(4));
        Flex buttons = Flex.row().gap(6);
        buttons.sizing(Sizing.fill(), Sizing.content());
        buttons.item(ArenasUi.button(Component.translatable("gui.arenas_ld.save"), this::saveSettings));
        boolean inbox = c.isLootViaInbox();
        buttons.item(ArenasUi.button(Component.translatable("gui.arenas_ld.arena.loot_via_inbox", inbox ? "ON" : "OFF"),
            () -> ClientPlayNetworking.send(new ArenaAdminSetBoolPayload(pos, "lootViaInbox", !inbox))));
        box.item(buttons);
        return box;
    }

    private void rebuildDynamic() {
        if (dynamicArea == null) return;
        ArenaControllerBlockEntity c = controller();
        if (c == null) return;
        lastInstanceSig = c.getInstances().size() * 31 + c.getLeaderboard().size();
        dynamicArea.clear();

        dynamicArea.item(ArenasUi.spacer(4));
        dynamicArea.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.instances")));
        List<ArenaControllerBlockEntity.ArenaInstanceState> instances = c.getInstances();
        if (instances.isEmpty()) {
            dynamicArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.arena.no_instances"), INK_DIM));
        }
        for (int i = 0; i < instances.size(); i++) {
            ArenaControllerBlockEntity.ArenaInstanceState inst = instances.get(i);
            int idx = i;
            Flex row = Flex.row().gap(4).padding(Insets.of(2, 6, 2, 6)).alignItems(Align.CENTER);
            row.sizing(Sizing.fill(), Sizing.fixed(20));
            row.backgroundFill(ROW_BG);
            BlockPos sp = inst.spawnerPos();
            row.item(ArenasUi.text(Component.literal(sp.toShortString() + "  " + inst.status().name()), INK));
            row.spacer();
            row.item(ArenasUi.button(Component.literal("▲"), 18, 16,
                () -> ClientPlayNetworking.send(new ArenaMoveInstancePayload(pos, idx, idx - 1))));
            row.item(ArenasUi.button(Component.literal("▼"), 18, 16,
                () -> ClientPlayNetworking.send(new ArenaMoveInstancePayload(pos, idx, idx + 1))));
            row.item(ArenasUi.button(Component.literal("✕"), 18, 16,
                () -> ClientPlayNetworking.send(new ArenaRemoveInstancePayload(pos, sp, inst.dimension().location().toString()))));
            dynamicArea.item(row);
        }

        dynamicArea.item(ArenasUi.spacer(4));
        dynamicArea.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.leaderboard")));
        List<LeaderboardEntry> board = c.getLeaderboard();
        if (board.isEmpty()) {
            dynamicArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.arena.no_scores"), INK_DIM));
        }
        int rank = 1;
        for (LeaderboardEntry e : board) {
            dynamicArea.item(ArenasUi.text(Component.literal((rank++) + ". " + e.playerName()
                + " — " + Component.translatable("gui.arenas_ld.arena.wave_n", e.timeSeconds()).getString()), INK));
        }
    }

    private void saveSettings() {
        intFields.forEach((key, field) -> {
            try {
                ClientPlayNetworking.send(new ArenaAdminSetIntPayload(pos, key, Integer.parseInt(field.text().trim())));
            } catch (NumberFormatException ignored) {}
        });
        try {
            ClientPlayNetworking.send(new ArenaSetRewardCurvePayload(pos,
                Double.parseDouble(currencyBase.text().trim()),
                Double.parseDouble(currencyExp.text().trim()),
                Double.parseDouble(xpBase.text().trim()),
                Double.parseDouble(xpExp.text().trim())));
        } catch (NumberFormatException ignored) {}
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private Flex intField(String key, Component caption, int value) {
        Flex row = Flex.row().gap(6).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(20));
        var label = ArenasUi.label(180, caption, INK_DIM);
        label.sizing(Sizing.fixed(180), Sizing.content());
        row.item(label);
        TextField box = ArenasUi.textField(80, String.valueOf(value), 32).size(80, 16);
        intFields.put(key, box);
        row.item(box);
        return row;
    }

    private TextField labeledBox(Flex parent, Component caption, String value) {
        Flex col = Flex.column().gap(2);
        col.item(ArenasUi.text(caption, INK_DIM));
        TextField box = ArenasUi.textField(64, value, 32).size(64, 16);
        col.item(box);
        parent.item(col);
        return box;
    }

    private Flex header() {
        Flex h = Flex.row().gap(10).padding(Insets.of(8, 10, 8, 10)).alignItems(Align.CENTER);
        h.sizing(Sizing.fill(), Sizing.fixed(36));
        h.backgroundFill(PANEL_2);
        h.item(ArenasUi.text(Component.translatable("gui.arenas_ld.arena_controller_admin.title"), INK));
        h.spacer();
        h.item(ArenasUi.button(Component.literal("×"), 22, 18, this::onClose));
        return h;
    }

    private static String str(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
