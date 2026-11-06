package net.ledok.arenas_ld.arena.screen;

import net.ledok.arenas_ld.arena.packet.ArenaAdminSetIntPayload;
import net.ledok.arenas_ld.arena.packet.ArenaMoveInstancePayload;
import net.ledok.arenas_ld.arena.packet.ArenaRemoveInstancePayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetRewardCurvePayload;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.screen.AdminDrafts;
import net.ledok.arenas_ld.screen.AdminEditState;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.arenas_ld.screen.FitCanvasHandledScreen;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT_DARK;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE_HI;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;
import static net.ledok.arenas_ld.screen.ArenasUi.ROW_BG;
import net.ledok.arenas_ld.platform.ArenasNetwork;

/**
 * Admin/config view for the arena controller. Reads the menu's {@link ArenaControllerAdminData}
 * (opening data, then admin snapshots), never the client-side block entity; edits via packets.
 */
public class ArenaControllerAdminScreen extends FitCanvasHandledScreen<ArenaControllerAdminScreenHandler> {
    private static final String NAME = "name";
    private static final String ID = "id";
    private static final String CURRENCY_BASE = "currencyBase";
    private static final String CURRENCY_EXP = "currencyExp";
    private static final String XP_BASE = "xpBase";
    private static final String XP_EXP = "xpExp";

    private final BlockPos pos;
    private Flex dynamicArea;
    private final Map<String, TextField> intFields = new LinkedHashMap<>();
    /** Every settings box by key (the int keys are also the payload keys). */
    private final Map<String, TextField> fields = new LinkedHashMap<>();
    /**
     * Every box with the server value it came from: a snapshot (another admin's save) only
     * refreshes the boxes nobody is editing here, and warns about the ones both changed.
     */
    private final AdminDrafts drafts = new AdminDrafts();
    private Label editNotice;
    /** Who last saved what (another admin), shown while there's no conflict. */
    private String savedNotice;
    private Button takeTheirs;
    private TextField currencyBase, currencyExp, xpBase, xpExp;
    private TextField nameField, idField;
    private Object lastDynamicSig;

    public ArenaControllerAdminScreen(ArenaControllerAdminScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(600, 340), Placement.Screen.center());
        this.pos = handler.getBlockPos();
        canvas.theme(ArenasUi.THEME);
        fillWindow();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The old owo screen swallowed the inventory key entirely (close via × or Esc only).
        if (ArenasUi.swallowsInventoryKey(input, keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private ArenaControllerAdminData data() {
        return this.menu.data();
    }

    /** Whether a snapshot is for this screen's controller: same position and dimension. */
    public boolean matchesController(BlockPos blockPos, ResourceKey<Level> dimension) {
        return pos.equals(blockPos) && data().dimension().equals(dimension);
    }

    /**
     * Takes a fresh snapshot: settings merge box by box (see {@link AdminDrafts}); the instance and
     * leaderboard rows rebuild on the next frame when they changed.
     */
    public void applyData(ArenaControllerAdminData data) {
        menu.applyData(data);
        if (fields.isEmpty()) {
            return; // buildAll takes everything at once
        }
        drafts.setAll(boxTexts());
        List<String> changed = drafts.merge(fieldValues(data));
        for (String key : changed) {
            TextField box = fields.get(key);
            if (box != null && !box.text().equals(drafts.get(key))) box.text(drafts.get(key));
        }
        String notice = AdminEditState.notice(data.lastEditor(), changed,
            Map.of("", Component.translatable("gui.arenas_ld.arena.settings")));
        refreshEditNotice(notice);
    }

    private Map<String, String> boxTexts() {
        Map<String, String> texts = new HashMap<>();
        fields.forEach((key, box) -> texts.put(key, box.text()));
        return texts;
    }

    /** The conflict warning (with "use theirs"), else who just saved, else nothing. */
    private void refreshEditNotice(String saved) {
        if (editNotice == null) return;
        Map<String, String> conflicts = drafts.conflicts("");
        takeTheirs.enabled(!conflicts.isEmpty());
        if (!conflicts.isEmpty()) {
            editNotice.text(AdminEditState.conflictText(data().lastEditor(), conflicts, ArenaControllerAdminScreen::caption))
                .color(ArenasUi.WARN);
        } else {
            if (saved != null) savedNotice = saved;
            editNotice.text(savedNotice == null ? "" : savedNotice).color(ArenasUi.INFO);
        }
    }

    private void takeTheirs() {
        Map<String, String> conflicts = drafts.conflicts("");
        drafts.setAll(boxTexts());
        drafts.takeServer(conflicts.keySet());
        conflicts.keySet().forEach(key -> {
            TextField box = fields.get(key);
            if (box != null) box.text(drafts.get(key));
        });
        refreshEditNotice(null);
    }

    /** A box's caption, for the conflict warning. */
    private static Component caption(String key) {
        return switch (key) {
            case NAME -> Component.translatable("gui.arenas_ld.arena.name");
            case ID -> Component.translatable("gui.arenas_ld.arena.activity_id");
            case "maxPartySize" -> Component.translatable("gui.arenas_ld.arena.max_party");
            case "maxWave" -> Component.translatable("gui.arenas_ld.arena.max_wave");
            case "closeTimer" -> Component.translatable("gui.arenas_ld.arena.close_timer_seconds");
            case "cooldown" -> Component.translatable("gui.arenas_ld.arena.cooldown_ticks");
            case "respawnTime" -> Component.translatable("gui.arenas_ld.arena.respawn_ticks");
            case "inviteExpiry" -> Component.translatable("gui.arenas_ld.arena.invite_expiry_ticks");
            case "deathPenalty" -> Component.translatable("gui.arenas_ld.arena.death_penalty_ticks");
            case "hpScalePct" -> Component.translatable("gui.arenas_ld.arena.hp_scale_pct");
            case "hpWavePct" -> Component.translatable("gui.arenas_ld.arena.hp_wave_pct");
            case CURRENCY_BASE -> Component.literal("$ base");
            case CURRENCY_EXP -> Component.literal("$ exp");
            case XP_BASE -> Component.literal("xp base");
            case XP_EXP -> Component.literal("xp exp");
            default -> Component.literal(key);
        };
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        buildAll();
    }

    private void buildAll() {
        // A rebuild (resize) keeps what the admin typed but hasn't saved.
        if (fields.isEmpty()) {
            drafts.merge(fieldValues(data()));
        } else {
            drafts.setAll(boxTexts());
        }
        canvas.clear();
        intFields.clear();
        fields.clear();
        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);

        float w = Math.max(480, Math.min(620, canvas.width() - 24));
        float h = Math.max(300, canvas.height() - 24);
        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(w), Sizing.fixed(h));
        shell.backgroundFill(PANEL, HAIRLINE_HI, 1);
        shell.item(header());

        Flex content = Flex.column().gap(6).padding(Insets.of(10));
        content.sizing(Sizing.fill(), Sizing.content());
        content.item(buildSettings(data()));
        fields.forEach((key, box) -> {
            if (!box.text().equals(drafts.get(key))) box.text(drafts.get(key));
        });
        refreshEditNotice(null);
        dynamicArea = content.item(Flex.column().gap(4));
        dynamicArea.sizing(Sizing.fill(), Sizing.content());
        ScrollPanel scroll = new ScrollPanel(100, 100, content);
        scroll.sizing(Sizing.fill(), Sizing.expand());
        shell.item(scroll);

        root.layoutIn(canvas.width(), canvas.height());
        rebuildDynamic();
    }

    @Override
    protected void onCanvasFrame(float partialTick) {
        if (!dynamicSignature(data()).equals(lastDynamicSig)) rebuildDynamic();
    }

    /** Everything the instance and leaderboard rows show (cooldowns as the whole seconds shown). */
    private static Object dynamicSignature(ArenaControllerAdminData d) {
        return List.of(
            d.instances().stream().map(inst -> List.<Object>of(inst.spawnerPos(), inst.dimension(), inst.status(),
                inst.cooldownTicksRemaining() / 20)).toList(),
            d.pendingRemovals(),
            d.leaderboard());
    }

    /** Each settings box's text for this data, by key. */
    private static Map<String, String> fieldValues(ArenaControllerAdminData d) {
        Map<String, String> v = new HashMap<>();
        v.put(NAME, d.name());
        v.put(ID, d.activityId());
        v.put("maxPartySize", String.valueOf(d.maxPartySize()));
        v.put("maxWave", String.valueOf(d.maxWave()));
        v.put("closeTimer", String.valueOf(d.closeTimerSeconds()));
        v.put("cooldown", String.valueOf(d.cooldownTicks()));
        v.put("respawnTime", String.valueOf(d.respawnTimeTicks()));
        v.put("inviteExpiry", String.valueOf(d.inviteExpiryTicks()));
        v.put("deathPenalty", String.valueOf(d.deathTimePenaltyTicks()));
        v.put("hpScalePct", String.valueOf((int) Math.round(d.hpScalePerPlayer() * 100.0)));
        v.put("hpWavePct", String.valueOf((int) Math.round(d.hpScalePerWave() * 100.0)));
        v.put(CURRENCY_BASE, str(d.rewardCurrencyBase()));
        v.put(CURRENCY_EXP, str(d.rewardCurrencyExp()));
        v.put(XP_BASE, str(d.rewardXpBase()));
        v.put(XP_EXP, str(d.rewardXpExp()));
        return v;
    }

    private Flex buildSettings(ArenaControllerAdminData d) {
        Map<String, String> v = fieldValues(d);
        Flex box = Flex.column().gap(4);
        box.sizing(Sizing.fill(), Sizing.content());
        box.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.identity")));
        nameField = textRow(box, NAME, Component.translatable("gui.arenas_ld.arena.name"), v.get(NAME), 48);
        idField = textRow(box, ID, Component.translatable("gui.arenas_ld.arena.activity_id"), v.get(ID), 128);

        box.item(ArenasUi.spacer(4));
        box.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.settings")));
        box.item(intField("maxPartySize", Component.translatable("gui.arenas_ld.arena.max_party"), v));
        box.item(intField("maxWave", Component.translatable("gui.arenas_ld.arena.max_wave"), v));
        box.item(intField("closeTimer", Component.translatable("gui.arenas_ld.arena.close_timer_seconds"), v));
        box.item(intField("cooldown", Component.translatable("gui.arenas_ld.arena.cooldown_ticks"), v));
        box.item(intField("respawnTime", Component.translatable("gui.arenas_ld.arena.respawn_ticks"), v));
        box.item(intField("inviteExpiry", Component.translatable("gui.arenas_ld.arena.invite_expiry_ticks"), v));
        box.item(intField("deathPenalty", Component.translatable("gui.arenas_ld.arena.death_penalty_ticks"), v));
        box.item(intField("hpScalePct", Component.translatable("gui.arenas_ld.arena.hp_scale_pct"), v));
        box.item(intField("hpWavePct", Component.translatable("gui.arenas_ld.arena.hp_wave_pct"), v));

        box.item(ArenasUi.spacer(4));
        box.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.reward_curve")));
        Flex curveRow = Flex.row().gap(6);
        curveRow.sizing(Sizing.fill(), Sizing.content());
        currencyBase = labeledBox(curveRow, CURRENCY_BASE, Component.literal("$ base"), v.get(CURRENCY_BASE));
        currencyExp = labeledBox(curveRow, CURRENCY_EXP, Component.literal("$ exp"), v.get(CURRENCY_EXP));
        xpBase = labeledBox(curveRow, XP_BASE, Component.literal("xp base"), v.get(XP_BASE));
        xpExp = labeledBox(curveRow, XP_EXP, Component.literal("xp exp"), v.get(XP_EXP));
        box.item(curveRow);

        box.item(ArenasUi.spacer(4));
        Flex buttons = Flex.row().gap(6);
        buttons.sizing(Sizing.fill(), Sizing.content());
        buttons.item(ArenasUi.button(Component.translatable("gui.arenas_ld.save"), this::saveSettings));
        takeTheirs = ArenasUi.button(Component.translatable("gui.arenas_ld.admin.take_theirs"), this::takeTheirs);
        takeTheirs.enabled(false);
        buttons.item(takeTheirs);
        box.item(buttons);
        editNotice = ArenasUi.label(100, Component.empty(), ArenasUi.WARN).tooltipWhenCut(true);
        editNotice.sizing(Sizing.fill(), Sizing.content());
        box.item(editNotice);
        return box;
    }

    private void rebuildDynamic() {
        if (dynamicArea == null) return;
        ArenaControllerAdminData d = data();
        lastDynamicSig = dynamicSignature(d);
        dynamicArea.clear();

        dynamicArea.item(ArenasUi.spacer(4));
        dynamicArea.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.instances")));
        List<ArenaControllerAdminData.InstanceEntry> instances = d.instances();
        if (instances.isEmpty()) {
            dynamicArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.arena.no_instances"), INK_DIM));
        }
        for (ArenaControllerAdminData.InstanceEntry inst : instances) {
            Flex row = Flex.row().gap(4).padding(Insets.of(2, 6, 2, 6)).alignItems(Align.CENTER);
            row.sizing(Sizing.fill(), Sizing.fixed(20));
            row.backgroundFill(ROW_BG);
            BlockPos sp = inst.spawnerPos();
            // ✕ on a running instance queues its removal for the run's end (✕ again cancels it).
            String status = inst.status() == InstanceStatus.RUNNING && d.pendingRemovals().contains(sp)
                ? Component.translatable("gui.arenas_ld.arena.pending_removal").getString()
                : inst.status().name();
            if (inst.status() == InstanceStatus.COOLDOWN && inst.cooldownTicksRemaining() > 0) {
                status += " " + (inst.cooldownTicksRemaining() / 20) + "s";
            }
            row.item(ArenasUi.text(Component.literal(sp.toShortString() + "  " + status), INK));
            row.spacer();
            String dim = inst.dimension().location().toString();
            row.item(ArenasUi.button(Component.literal("▲"), 18, 16,
                () -> ArenasNetwork.sendToServer(new ArenaMoveInstancePayload(pos, sp, dim, -1))));
            row.item(ArenasUi.button(Component.literal("▼"), 18, 16,
                () -> ArenasNetwork.sendToServer(new ArenaMoveInstancePayload(pos, sp, dim, 1))));
            row.item(ArenasUi.button(Component.literal("✕"), 18, 16,
                () -> ArenasNetwork.sendToServer(new ArenaRemoveInstancePayload(pos, sp, dim))));
            dynamicArea.item(row);
        }

        dynamicArea.item(ArenasUi.spacer(4));
        dynamicArea.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.leaderboard")));
        List<LeaderboardEntry> board = d.leaderboard();
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
        // Only what this admin changed, so a box another admin saved meanwhile is left alone.
        // Saved boxes then follow the server again, which shows what it accepted.
        drafts.setAll(boxTexts());
        List<String> sent = new java.util.ArrayList<>();
        int packets = 0;
        if (drafts.dirty(NAME) || drafts.dirty(ID)) {
            ArenasNetwork.sendToServer(new net.ledok.arenas_ld.packet.SetActivityInfoPayload(pos,
                idField.text().trim(), nameField.text().trim()));
            sent.add(NAME);
            sent.add(ID);
            packets++;
        }
        for (Map.Entry<String, TextField> entry : intFields.entrySet()) {
            if (!drafts.dirty(entry.getKey())) continue;
            try {
                ArenasNetwork.sendToServer(new ArenaAdminSetIntPayload(pos, entry.getKey(),
                    Integer.parseInt(entry.getValue().text().trim())));
                sent.add(entry.getKey());
                packets++;
            } catch (NumberFormatException ignored) {}
        }
        List<String> curve = List.of(CURRENCY_BASE, CURRENCY_EXP, XP_BASE, XP_EXP);
        if (curve.stream().anyMatch(drafts::dirty)) {
            try {
                ArenasNetwork.sendToServer(new ArenaSetRewardCurvePayload(pos,
                    Double.parseDouble(currencyBase.text().trim()),
                    Double.parseDouble(currencyExp.text().trim()),
                    Double.parseDouble(xpBase.text().trim()),
                    Double.parseDouble(xpExp.text().trim())));
                sent.addAll(curve);
                packets++;
            } catch (NumberFormatException ignored) {}
        }
        drafts.saved(sent, packets);
        refreshEditNotice(null);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private Flex intField(String key, Component caption, Map<String, String> values) {
        Flex row = Flex.row().gap(6).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(20));
        var label = ArenasUi.label(180, caption, INK_DIM);
        label.sizing(Sizing.fixed(180), Sizing.content());
        row.item(label);
        TextField box = ArenasUi.textField(80, values.get(key), 32).size(80, 16);
        intFields.put(key, box);
        fields.put(key, box);
        row.item(box);
        return row;
    }

    /** Caption + wide text box (name, id). */
    private TextField textRow(Flex parent, String key, Component caption, String value, int maxLength) {
        Flex row = Flex.row().gap(6).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(20));
        var label = ArenasUi.label(180, caption, INK_DIM);
        label.sizing(Sizing.fixed(180), Sizing.content());
        row.item(label);
        TextField box = ArenasUi.textField(240, value, maxLength).size(240, 16);
        fields.put(key, box);
        row.item(box);
        parent.item(row);
        return box;
    }

    private TextField labeledBox(Flex parent, String key, Component caption, String value) {
        Flex col = Flex.column().gap(2);
        col.item(ArenasUi.text(caption, INK_DIM));
        TextField box = ArenasUi.textField(64, value, 32).size(64, 16);
        fields.put(key, box);
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
