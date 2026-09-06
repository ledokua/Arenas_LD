package net.ledok.arenas_ld.arena.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.arena.packet.ArenaSpawnerMobsPayload;
import net.ledok.arenas_ld.arena.packet.ArenaSpawnerRewardsPayload;
import net.ledok.arenas_ld.arena.packet.ArenaSpawnerSettingsPayload;
import net.ledok.arenas_ld.arena.run.ObjectiveType;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.ledok.arenas_ld.util.MobArenaRewardData;
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
import net.ledok.vectorlib.client.presentation.CanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static net.ledok.arenas_ld.screen.ArenasUi.BG;
import static net.ledok.arenas_ld.screen.ArenasUi.DANGER;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE_HI;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;
import static net.ledok.arenas_ld.screen.ArenasUi.ROW_BG;

/** Config editor for the arena spawner: combat/timing/cadence settings, mob list, and per-wave rewards. */
public class ArenaSpawnerScreen extends CanvasHandledScreen<ArenaSpawnerScreenHandler> {
    private final BlockPos pos;
    private Flex mobsArea;
    private Flex rewardsArea;
    private final Map<String, TextField> settings = new LinkedHashMap<>();
    private final List<ObjectiveType> objectives = new ArrayList<>();
    private final List<MobArenaMobData> mobs = new ArrayList<>();
    private final List<MobArenaRewardData> rewards = new ArrayList<>();
    private boolean loaded = false;

    public ArenaSpawnerScreen(ArenaSpawnerScreenHandler handler, Inventory inventory, Component title) {
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

    private ArenaSpawnerBlockEntity spawner() {
        return this.menu.spawner;
    }

    private void loadFromBe() {
        if (loaded) return;
        loaded = true;
        ArenaSpawnerBlockEntity s = spawner();
        if (s == null) return;
        objectives.clear();
        objectives.addAll(s.getEnabledObjectives());
        mobs.clear();
        for (MobArenaMobData m : s.getMobs()) {
            mobs.add(new MobArenaMobData(m.mobId, new ArrayList<>(m.attributes), m.equipment, m.weight, m.minWave, m.maxWave, m.isBoss));
        }
        rewards.clear();
        for (MobArenaRewardData r : s.getRewards()) {
            rewards.add(new MobArenaRewardData(r.perPlayer, r.lootTableId, r.weight, r.rolls, r.minWave, r.maxWave, r.waveFrequency));
        }
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        buildAll();
    }

    private void buildAll() {
        loadFromBe();
        canvas.clear();
        settings.clear();
        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);
        root.backgroundFill(BG);

        float w = Math.max(500, Math.min(640, canvas.width() - 24));
        float h = Math.max(300, canvas.height() - 24);
        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(w), Sizing.fixed(h));
        shell.backgroundFill(PANEL, HAIRLINE_HI, 1);
        shell.item(header());

        Flex content = Flex.column().gap(6).padding(Insets.of(10));
        content.sizing(Sizing.fill(), Sizing.content());
        ArenaSpawnerBlockEntity s = spawner();
        if (s == null) {
            mobsArea = null;
            rewardsArea = null;
            content.item(ArenasUi.text(Component.translatable("gui.arenas_ld.arena.not_loaded"), DANGER));
        } else {
            content.item(buildSettings(s));
            content.item(ArenasUi.spacer(4));
            content.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.mobs")));
            mobsArea = content.item(Flex.column().gap(3));
            mobsArea.sizing(Sizing.fill(), Sizing.content());
            content.item(button(Component.translatable("gui.arenas_ld.add"), () -> {
                List<AttributeData> def = new ArrayList<>();
                def.add(new AttributeData("minecraft:generic.max_health", 20.0));
                def.add(new AttributeData("minecraft:generic.attack_damage", 3.0));
                mobs.add(new MobArenaMobData("minecraft:zombie", def, new EquipmentData(), 10, 1, 100, false));
                rebuildMobs();
            }));

            content.item(ArenasUi.spacer(4));
            content.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.rewards")));
            rewardsArea = content.item(Flex.column().gap(3));
            rewardsArea.sizing(Sizing.fill(), Sizing.content());
            content.item(button(Component.translatable("gui.arenas_ld.add"), () -> {
                rewards.add(new MobArenaRewardData(false, "", 10, 1, 1, 100, 1));
                rebuildRewards();
            }));
        }
        ScrollPanel scroll = new ScrollPanel(100, 100, content);
        scroll.sizing(Sizing.fill(), Sizing.expand());
        shell.item(scroll);

        root.layoutIn(canvas.width(), canvas.height());
        rebuildMobs();
        rebuildRewards();
    }

    private Flex buildSettings(ArenaSpawnerBlockEntity s) {
        Flex box = Flex.column().gap(3);
        box.sizing(Sizing.fill(), Sizing.content());
        box.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.arena.settings")));
        box.item(intRow("battleRadius", Component.translatable("gui.arenas_ld.arena.battle_radius"), s.getBattleRadius()));
        box.item(intRow("spawnDistance", Component.translatable("gui.arenas_ld.arena.spawn_distance"), s.getSpawnDistance()));
        box.item(doubleRow("attributeScale", Component.translatable("gui.arenas_ld.arena.attribute_scale"), s.getAttributeScale()));
        box.item(intRow("waveTimer", Component.translatable("gui.arenas_ld.arena.wave_timer"), s.getWaveTimer()));
        box.item(intRow("additionalTime", Component.translatable("gui.arenas_ld.arena.additional_time"), s.getAdditionalTime()));
        box.item(intRow("timeBetweenWaves", Component.translatable("gui.arenas_ld.arena.time_between_waves"), s.getTimeBetweenWaves()));
        box.item(intRow("prepareTime", Component.translatable("gui.arenas_ld.arena.prepare_time"), s.getPrepareTime()));
        box.item(intRow("bossWaveAdditionalTime", Component.translatable("gui.arenas_ld.arena.boss_wave_time"), s.getBossWaveAdditionalTime()));
        box.item(intRow("bossEveryN", Component.translatable("gui.arenas_ld.arena.boss_every"), s.getBossEveryNWaves()));
        box.item(intRow("eliteEveryN", Component.translatable("gui.arenas_ld.arena.elite_every"), s.getEliteEveryNWaves()));
        box.item(intRow("objectiveEveryN", Component.translatable("gui.arenas_ld.arena.objective_every"), s.getObjectiveEveryNWaves()));
        box.item(intRow("entityHighlightTime", Component.translatable("gui.arenas_ld.arena.highlight_time"), s.getEntityHighlightTime()));

        Flex objRow = Flex.row().gap(4);
        objRow.sizing(Sizing.fill(), Sizing.content());
        objRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.arena.objectives"), INK_DIM));
        for (ObjectiveType t : new ObjectiveType[]{ObjectiveType.DEFEND_ZONE, ObjectiveType.SURVIVE_UNTOUCHED, ObjectiveType.KILL_MARKED}) {
            objRow.item(objectiveToggle(t));
        }
        box.item(objRow);
        box.item(button(Component.translatable("gui.arenas_ld.save"), this::save));
        return box;
    }

    /** Content-width toggle that relabels (and re-measures) itself in place, like owo's setMessage. */
    private Button objectiveToggle(ObjectiveType t) {
        Component label = Component.literal(t.name() + (objectives.contains(t) ? " ✓" : ""));
        Button b = button(label, null);
        b.onClick(() -> {
            if (objectives.contains(t)) objectives.remove(t); else objectives.add(t);
            Component updated = Component.literal(t.name() + (objectives.contains(t) ? " ✓" : ""));
            b.label(updated);
            b.size(Minecraft.getInstance().font.width(updated) + 12, 16);
        });
        return b;
    }

    private void rebuildMobs() {
        if (mobsArea == null) return;
        mobsArea.clear();
        if (mobs.isEmpty()) mobsArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.arena.no_mobs"), INK_DIM));
        for (int i = 0; i < mobs.size(); i++) {
            MobArenaMobData mob = mobs.get(i);
            int idx = i;
            Flex row = Flex.row().gap(3).padding(Insets.of(2, 4, 2, 4)).alignItems(Align.CENTER);
            row.sizing(Sizing.fill(), Sizing.fixed(22));
            row.backgroundFill(ROW_BG);
            row.item(box(Sizing.expand(), mob.mobId, v -> mob.mobId = v));
            row.item(box(Sizing.fixed(34), String.valueOf(mob.weight), v -> mob.weight = parseInt(v, mob.weight)));
            row.item(box(Sizing.fixed(30), String.valueOf(mob.minWave), v -> mob.minWave = parseInt(v, mob.minWave)));
            row.item(box(Sizing.fixed(30), String.valueOf(mob.maxWave), v -> mob.maxWave = parseInt(v, mob.maxWave)));
            row.item(button(Component.literal(mob.isBoss ? "BOSS" : "reg"), 40, 16,
                () -> { mob.isBoss = !mob.isBoss; rebuildMobs(); }));
            row.item(button(Component.literal("✕"), 16, 16,
                () -> { mobs.remove(idx); rebuildMobs(); }));
            mobsArea.item(row);
        }
    }

    private void rebuildRewards() {
        if (rewardsArea == null) return;
        rewardsArea.clear();
        if (rewards.isEmpty()) rewardsArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.arena.no_rewards"), INK_DIM));
        for (int i = 0; i < rewards.size(); i++) {
            MobArenaRewardData rw = rewards.get(i);
            int idx = i;
            Flex row = Flex.row().gap(3).padding(Insets.of(2, 4, 2, 4)).alignItems(Align.CENTER);
            row.sizing(Sizing.fill(), Sizing.fixed(22));
            row.backgroundFill(ROW_BG);
            row.item(box(Sizing.expand(), rw.lootTableId, v -> rw.lootTableId = v));
            row.item(box(Sizing.fixed(28), String.valueOf(rw.rolls), v -> rw.rolls = parseInt(v, rw.rolls)));
            row.item(box(Sizing.fixed(30), String.valueOf(rw.minWave), v -> rw.minWave = parseInt(v, rw.minWave)));
            row.item(box(Sizing.fixed(30), String.valueOf(rw.maxWave), v -> rw.maxWave = parseInt(v, rw.maxWave)));
            row.item(box(Sizing.fixed(28), String.valueOf(rw.waveFrequency), v -> rw.waveFrequency = parseInt(v, rw.waveFrequency)));
            row.item(button(Component.literal(rw.perPlayer ? "each" : "one"), 40, 16,
                () -> { rw.perPlayer = !rw.perPlayer; rebuildRewards(); }));
            row.item(button(Component.literal("✕"), 16, 16,
                () -> { rewards.remove(idx); rebuildRewards(); }));
            rewardsArea.item(row);
        }
    }

    private void save() {
        List<String> objNames = new ArrayList<>();
        for (ObjectiveType t : objectives) objNames.add(t.name());
        ClientPlayNetworking.send(new ArenaSpawnerSettingsPayload(pos,
            si("battleRadius", 64), si("spawnDistance", 8), sd("attributeScale", 0.1), si("entityHighlightTime", 0),
            si("waveTimer", 120), si("additionalTime", 5), si("timeBetweenWaves", 10), si("prepareTime", 10),
            si("bossWaveAdditionalTime", 60), si("bossEveryN", 5), si("eliteEveryN", 3), si("objectiveEveryN", 7), objNames));
        ClientPlayNetworking.send(new ArenaSpawnerMobsPayload(pos, new ArrayList<>(mobs)));
        ClientPlayNetworking.send(new ArenaSpawnerRewardsPayload(pos, new ArrayList<>(rewards)));
    }

    private int si(String key, int def) {
        TextField f = settings.get(key);
        if (f == null) return def;
        try { return Integer.parseInt(f.text().trim()); } catch (NumberFormatException e) { return def; }
    }

    private double sd(String key, double def) {
        TextField f = settings.get(key);
        if (f == null) return def;
        try { return Double.parseDouble(f.text().trim()); } catch (NumberFormatException e) { return def; }
    }

    private static int parseInt(String v, int fallback) {
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private Flex intRow(String key, Component caption, int value) {
        return settingRow(key, caption, String.valueOf(value));
    }

    private Flex doubleRow(String key, Component caption, double value) {
        String s = (value == Math.floor(value)) ? String.valueOf((long) value) : String.valueOf(value);
        return settingRow(key, caption, s);
    }

    private Flex settingRow(String key, Component caption, String value) {
        Flex row = Flex.row().gap(6).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(18));
        Label label = ArenasUi.label(190, caption, INK_DIM);
        label.sizing(Sizing.fixed(190), Sizing.content());
        row.item(label);
        TextField box = ArenasUi.textField(70, value, 32).size(70, 15);
        settings.put(key, box);
        row.item(box);
        return row;
    }

    private Flex box(Sizing width, String initial, Consumer<String> onChange) {
        Flex wrap = Flex.row().alignItems(Align.CENTER);
        wrap.sizing(width, Sizing.fixed(18));
        wrap.backgroundFill(PANEL_2, HAIRLINE, 1);
        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(50, initial, 256);
        field.sizing(Sizing.expand(), Sizing.fixed(15));
        field.changeListeners.add(onChange::accept);
        wrap.item(field);
        return wrap;
    }

    private Flex header() {
        Flex h = Flex.row().gap(10).padding(Insets.of(8, 10, 8, 10)).alignItems(Align.CENTER);
        h.sizing(Sizing.fill(), Sizing.fixed(36));
        h.backgroundFill(PANEL_2);
        h.item(ArenasUi.text(Component.translatable("block.arenas_ld.arena_spawner"), INK));
        h.spacer();
        h.item(button(Component.literal("×"), 22, 18, this::onClose));
        return h;
    }

    /** Content-width button, 16 px tall (this screen's old owo buttons were 16, not 18). */
    private Button button(Component text, Runnable onClick) {
        float w = Minecraft.getInstance().font.width(text) + 12;
        return ArenasUi.button(text, w, 16, onClick);
    }

    private Button button(Component text, float w, float h, Runnable onClick) {
        return ArenasUi.button(text, w, h, onClick);
    }
}
