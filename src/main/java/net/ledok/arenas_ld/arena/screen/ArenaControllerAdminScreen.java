package net.ledok.arenas_ld.arena.screen;

import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.packet.ArenaAdminSetBoolPayload;
import net.ledok.arenas_ld.arena.packet.ArenaAdminSetIntPayload;
import net.ledok.arenas_ld.arena.packet.ArenaMoveInstancePayload;
import net.ledok.arenas_ld.arena.packet.ArenaRemoveInstancePayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetRewardCurvePayload;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Admin/config view for the arena controller. Reads the client-synced controller; edits via packets. */
public class ArenaControllerAdminScreen extends BaseOwoHandledScreen<FlowLayout, ArenaControllerAdminScreenHandler> {
    private static final int BG = 0xFF070E14, PANEL = 0xFF121922, PANEL_2 = 0xFF0C1218;
    private static final int HAIRLINE = 0xFF283442, HAIRLINE_HI = 0xFF3A4A5C, ROW_BG = 0xFF19222D;
    private static final int INK = 0xFFE8EEF5, INK_DIM = 0xFF5F6E80, ACCENT = 0xFFA98BE8, ACCENT_DARK = 0xFF6C4FB5, DANGER = 0xFFE8624A;

    private final BlockPos pos;
    private FlowLayout dynamicArea;
    private final Map<String, TextBoxComponent> intFields = new LinkedHashMap<>();
    private TextBoxComponent currencyBase, currencyExp, xpBase, xpExp;
    private int lastInstanceSig = Integer.MIN_VALUE;

    public ArenaControllerAdminScreen(ArenaControllerAdminScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.pos = handler.getBlockPos();
        this.titleLabelY = 9999;
        this.inventoryLabelY = 9999;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    private ArenaControllerBlockEntity controller() {
        return this.menu.controller;
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.flat(BG));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int w = Math.max(480, Math.min(620, this.width - 24));
        int h = Math.max(300, this.height - 24);
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(w), Sizing.fixed(h));
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));
        shell.child(header());

        FlowLayout content = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.padding(Insets.of(10));
        content.gap(6);
        ArenaControllerBlockEntity c = controller();
        if (c == null) {
            content.child(text(Component.translatable("gui.arenas_ld.arena.not_loaded"), DANGER));
        } else {
            content.child(buildSettings(c));
            dynamicArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            dynamicArea.gap(4);
            content.child(dynamicArea);
        }
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), content);
        scroll.surface(Surface.flat(PANEL));
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        shell.child(scroll);
        root.child(shell);

        rebuildDynamic();
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ArenaControllerBlockEntity c = controller();
        int sig = c == null ? 0 : c.getInstances().size() * 31 + c.getLeaderboard().size();
        if (sig != lastInstanceSig) rebuildDynamic();
        super.render(ctx, mouseX, mouseY, delta);
    }

    private FlowLayout buildSettings(ArenaControllerBlockEntity c) {
        FlowLayout box = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        box.gap(4);
        box.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.settings")));
        box.child(intField("maxPartySize", Component.translatable("gui.arenas_ld.arena.max_party"), c.getMaxPartySize()));
        box.child(intField("maxWave", Component.translatable("gui.arenas_ld.arena.max_wave"), c.getMaxWave()));
        box.child(intField("closeTimer", Component.translatable("gui.arenas_ld.arena.close_timer_seconds"), c.getCloseTimerSeconds()));
        box.child(intField("cooldown", Component.translatable("gui.arenas_ld.arena.cooldown_ticks"), c.getCooldownTicks()));
        box.child(intField("respawnTime", Component.translatable("gui.arenas_ld.arena.respawn_ticks"), c.getRespawnTimeTicks()));
        box.child(intField("inviteExpiry", Component.translatable("gui.arenas_ld.arena.invite_expiry_ticks"), c.getInviteExpiryTicks()));
        box.child(intField("deathPenalty", Component.translatable("gui.arenas_ld.arena.death_penalty_ticks"), c.getDeathTimePenaltyTicks()));

        box.child(spacer(4));
        box.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.reward_curve")));
        FlowLayout curveRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        curveRow.gap(6);
        currencyBase = labeledBox(curveRow, Component.literal("$ base"), str(c.getRewardCurrencyBase()));
        currencyExp = labeledBox(curveRow, Component.literal("$ exp"), str(c.getRewardCurrencyExp()));
        xpBase = labeledBox(curveRow, Component.literal("xp base"), str(c.getRewardXpBase()));
        xpExp = labeledBox(curveRow, Component.literal("xp exp"), str(c.getRewardXpExp()));
        box.child(curveRow);

        box.child(spacer(4));
        FlowLayout buttons = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        buttons.gap(6);
        buttons.child(button(Component.translatable("gui.arenas_ld.save"), b -> saveSettings()));
        boolean inbox = c.isLootViaInbox();
        buttons.child(button(Component.translatable("gui.arenas_ld.arena.loot_via_inbox", inbox ? "ON" : "OFF"),
            b -> ClientPlayNetworking.send(new ArenaAdminSetBoolPayload(pos, "lootViaInbox", !inbox))));
        box.child(buttons);
        return box;
    }

    private void rebuildDynamic() {
        if (dynamicArea == null) return;
        ArenaControllerBlockEntity c = controller();
        if (c == null) return;
        lastInstanceSig = c.getInstances().size() * 31 + c.getLeaderboard().size();
        dynamicArea.clearChildren();

        dynamicArea.child(spacer(4));
        dynamicArea.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.instances")));
        List<ArenaControllerBlockEntity.ArenaInstanceState> instances = c.getInstances();
        if (instances.isEmpty()) {
            dynamicArea.child(text(Component.translatable("gui.arenas_ld.arena.no_instances"), INK_DIM));
        }
        for (int i = 0; i < instances.size(); i++) {
            ArenaControllerBlockEntity.ArenaInstanceState inst = instances.get(i);
            int idx = i;
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
            row.surface(Surface.flat(ROW_BG));
            row.padding(Insets.of(2, 2, 6, 6));
            row.gap(4);
            row.verticalAlignment(VerticalAlignment.CENTER);
            BlockPos sp = inst.spawnerPos();
            row.child(text(Component.literal(sp.toShortString() + "  " + inst.status().name()), INK));
            row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
            ButtonComponent up = button(Component.literal("▲"), b -> ClientPlayNetworking.send(new ArenaMoveInstancePayload(pos, idx, idx - 1)));
            up.sizing(Sizing.fixed(18), Sizing.fixed(16));
            row.child(up);
            ButtonComponent down = button(Component.literal("▼"), b -> ClientPlayNetworking.send(new ArenaMoveInstancePayload(pos, idx, idx + 1)));
            down.sizing(Sizing.fixed(18), Sizing.fixed(16));
            row.child(down);
            ButtonComponent rm = button(Component.literal("✕"), b -> ClientPlayNetworking.send(
                new ArenaRemoveInstancePayload(pos, sp, inst.dimension().location().toString())));
            rm.sizing(Sizing.fixed(18), Sizing.fixed(16));
            row.child(rm);
            dynamicArea.child(row);
        }

        dynamicArea.child(spacer(4));
        dynamicArea.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.leaderboard")));
        List<LeaderboardEntry> board = c.getLeaderboard();
        if (board.isEmpty()) {
            dynamicArea.child(text(Component.translatable("gui.arenas_ld.arena.no_scores"), INK_DIM));
        }
        int rank = 1;
        for (LeaderboardEntry e : board) {
            dynamicArea.child(text(Component.literal((rank++) + ". " + e.playerName()
                + " — " + Component.translatable("gui.arenas_ld.arena.wave_n", e.timeSeconds()).getString()), INK));
        }
    }

    private void saveSettings() {
        intFields.forEach((key, field) -> {
            try {
                ClientPlayNetworking.send(new ArenaAdminSetIntPayload(pos, key, Integer.parseInt(field.getValue().trim())));
            } catch (NumberFormatException ignored) {}
        });
        try {
            ClientPlayNetworking.send(new ArenaSetRewardCurvePayload(pos,
                Double.parseDouble(currencyBase.getValue().trim()),
                Double.parseDouble(currencyExp.getValue().trim()),
                Double.parseDouble(xpBase.getValue().trim()),
                Double.parseDouble(xpExp.getValue().trim())));
        } catch (NumberFormatException ignored) {}
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private FlowLayout intField(String key, Component caption, int value) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        LabelComponent label = text(caption, INK_DIM);
        label.horizontalSizing(Sizing.fixed(180));
        row.child(label);
        TextBoxComponent box = Components.textBox(Sizing.fixed(80), String.valueOf(value));
        box.verticalSizing(Sizing.fixed(16));
        intFields.put(key, box);
        row.child(box);
        return row;
    }

    private TextBoxComponent labeledBox(FlowLayout parent, Component caption, String value) {
        FlowLayout col = Containers.verticalFlow(Sizing.content(), Sizing.content());
        col.gap(2);
        col.child(text(caption, INK_DIM));
        TextBoxComponent box = Components.textBox(Sizing.fixed(64), value);
        box.verticalSizing(Sizing.fixed(16));
        col.child(box);
        parent.child(col);
        return box;
    }

    private FlowLayout header() {
        FlowLayout h = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(36));
        h.surface(Surface.flat(PANEL_2));
        h.padding(Insets.of(8, 8, 10, 10));
        h.gap(10);
        h.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        h.child(text(Component.translatable("gui.arenas_ld.arena_controller_admin.title"), INK));
        h.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        ButtonComponent close = button(Component.literal("×"), b -> onClose());
        close.sizing(Sizing.fixed(22), Sizing.fixed(18));
        h.child(close);
        return h;
    }

    private static String str(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private FlowLayout sectionHeader(Component title) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(text(title, INK_DIM));
        return row;
    }

    private FlowLayout spacer(int px) {
        FlowLayout s = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(px));
        s.surface(Surface.BLANK);
        return s;
    }

    private LabelComponent text(Component c, int color) {
        LabelComponent l = Components.label(c);
        l.color(Color.ofArgb(color));
        return l;
    }

    private ButtonComponent button(Component text, Consumer<ButtonComponent> action) {
        ButtonComponent b = Components.button(text, action);
        b.sizing(Sizing.content(), Sizing.fixed(18));
        b.renderer((ctx, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ACCENT : ACCENT_DARK;
            ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), HAIRLINE);
        });
        return b;
    }
}
