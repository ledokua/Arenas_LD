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
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.arena.packet.ArenaSpawnerMobsPayload;
import net.ledok.arenas_ld.arena.packet.ArenaSpawnerRewardsPayload;
import net.ledok.arenas_ld.arena.packet.ArenaSpawnerSettingsPayload;
import net.ledok.arenas_ld.arena.run.ObjectiveType;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.ledok.arenas_ld.util.MobArenaRewardData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Config editor for the arena spawner: combat/timing/cadence settings, mob list, and per-wave rewards. */
public class ArenaSpawnerScreen extends BaseOwoHandledScreen<FlowLayout, ArenaSpawnerScreenHandler> {
    private static final int BG = 0xFF070E14, PANEL = 0xFF121922, PANEL_2 = 0xFF0C1218;
    private static final int HAIRLINE = 0xFF283442, HAIRLINE_HI = 0xFF3A4A5C, ROW_BG = 0xFF19222D;
    private static final int INK = 0xFFE8EEF5, INK_DIM = 0xFF5F6E80, ACCENT = 0xFFA98BE8, ACCENT_DARK = 0xFF6C4FB5, DANGER = 0xFFE8624A;

    private final BlockPos pos;
    private FlowLayout mobsArea;
    private FlowLayout rewardsArea;
    private final Map<String, TextBoxComponent> settings = new LinkedHashMap<>();
    private final List<ObjectiveType> objectives = new ArrayList<>();
    private final List<MobArenaMobData> mobs = new ArrayList<>();
    private final List<MobArenaRewardData> rewards = new ArrayList<>();
    private boolean loaded = false;

    public ArenaSpawnerScreen(ArenaSpawnerScreenHandler handler, Inventory inventory, Component title) {
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
    protected void build(FlowLayout root) {
        loadFromBe();
        root.surface(Surface.flat(BG));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int w = Math.max(500, Math.min(640, this.width - 24));
        int h = Math.max(300, this.height - 24);
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(w), Sizing.fixed(h));
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));
        shell.child(header());

        FlowLayout content = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.padding(Insets.of(10));
        content.gap(6);
        ArenaSpawnerBlockEntity s = spawner();
        if (s == null) {
            content.child(text(Component.translatable("gui.arenas_ld.arena.not_loaded"), DANGER));
        } else {
            content.child(buildSettings(s));
            content.child(spacer(4));
            content.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.mobs")));
            mobsArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            mobsArea.gap(3);
            content.child(mobsArea);
            content.child(button(Component.translatable("gui.arenas_ld.add"), b -> {
                List<AttributeData> def = new ArrayList<>();
                def.add(new AttributeData("minecraft:generic.max_health", 20.0));
                def.add(new AttributeData("minecraft:generic.attack_damage", 3.0));
                mobs.add(new MobArenaMobData("minecraft:zombie", def, new EquipmentData(), 10, 1, 100, false));
                rebuildMobs();
            }));

            content.child(spacer(4));
            content.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.rewards")));
            rewardsArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            rewardsArea.gap(3);
            content.child(rewardsArea);
            content.child(button(Component.translatable("gui.arenas_ld.add"), b -> {
                rewards.add(new MobArenaRewardData(false, "", 10, 1, 1, 100, 1));
                rebuildRewards();
            }));
        }
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), content);
        scroll.surface(Surface.flat(PANEL));
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        shell.child(scroll);
        root.child(shell);

        rebuildMobs();
        rebuildRewards();
    }

    private FlowLayout buildSettings(ArenaSpawnerBlockEntity s) {
        FlowLayout box = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        box.gap(3);
        box.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.settings")));
        box.child(intRow("battleRadius", Component.translatable("gui.arenas_ld.arena.battle_radius"), s.getBattleRadius()));
        box.child(intRow("spawnDistance", Component.translatable("gui.arenas_ld.arena.spawn_distance"), s.getSpawnDistance()));
        box.child(doubleRow("attributeScale", Component.translatable("gui.arenas_ld.arena.attribute_scale"), s.getAttributeScale()));
        box.child(intRow("waveTimer", Component.translatable("gui.arenas_ld.arena.wave_timer"), s.getWaveTimer()));
        box.child(intRow("additionalTime", Component.translatable("gui.arenas_ld.arena.additional_time"), s.getAdditionalTime()));
        box.child(intRow("timeBetweenWaves", Component.translatable("gui.arenas_ld.arena.time_between_waves"), s.getTimeBetweenWaves()));
        box.child(intRow("prepareTime", Component.translatable("gui.arenas_ld.arena.prepare_time"), s.getPrepareTime()));
        box.child(intRow("bossWaveAdditionalTime", Component.translatable("gui.arenas_ld.arena.boss_wave_time"), s.getBossWaveAdditionalTime()));
        box.child(intRow("bossEveryN", Component.translatable("gui.arenas_ld.arena.boss_every"), s.getBossEveryNWaves()));
        box.child(intRow("eliteEveryN", Component.translatable("gui.arenas_ld.arena.elite_every"), s.getEliteEveryNWaves()));
        box.child(intRow("objectiveEveryN", Component.translatable("gui.arenas_ld.arena.objective_every"), s.getObjectiveEveryNWaves()));
        box.child(intRow("entityHighlightTime", Component.translatable("gui.arenas_ld.arena.highlight_time"), s.getEntityHighlightTime()));

        FlowLayout objRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        objRow.gap(4);
        objRow.child(text(Component.translatable("gui.arenas_ld.arena.objectives"), INK_DIM));
        for (ObjectiveType t : new ObjectiveType[]{ObjectiveType.DEFEND_ZONE, ObjectiveType.SURVIVE_UNTOUCHED, ObjectiveType.KILL_MARKED}) {
            objRow.child(button(Component.literal(t.name() + (objectives.contains(t) ? " ✓" : "")), b -> {
                if (objectives.contains(t)) objectives.remove(t); else objectives.add(t);
                b.setMessage(Component.literal(t.name() + (objectives.contains(t) ? " ✓" : "")));
            }));
        }
        box.child(objRow);
        box.child(button(Component.translatable("gui.arenas_ld.save"), b -> save()));
        return box;
    }

    private void rebuildMobs() {
        if (mobsArea == null) return;
        mobsArea.clearChildren();
        if (mobs.isEmpty()) mobsArea.child(text(Component.translatable("gui.arenas_ld.arena.no_mobs"), INK_DIM));
        for (int i = 0; i < mobs.size(); i++) {
            MobArenaMobData mob = mobs.get(i);
            int idx = i;
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
            row.surface(Surface.flat(ROW_BG));
            row.padding(Insets.of(2, 2, 4, 4));
            row.gap(3);
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.child(box(Sizing.expand(), mob.mobId, v -> mob.mobId = v));
            row.child(box(Sizing.fixed(34), String.valueOf(mob.weight), v -> mob.weight = parseInt(v, mob.weight)));
            row.child(box(Sizing.fixed(30), String.valueOf(mob.minWave), v -> mob.minWave = parseInt(v, mob.minWave)));
            row.child(box(Sizing.fixed(30), String.valueOf(mob.maxWave), v -> mob.maxWave = parseInt(v, mob.maxWave)));
            ButtonComponent boss = button(Component.literal(mob.isBoss ? "BOSS" : "reg"), b -> { mob.isBoss = !mob.isBoss; rebuildMobs(); });
            boss.horizontalSizing(Sizing.fixed(40));
            row.child(boss);
            ButtonComponent rm = button(Component.literal("✕"), b -> { mobs.remove(idx); rebuildMobs(); });
            rm.sizing(Sizing.fixed(16), Sizing.fixed(16));
            row.child(rm);
            mobsArea.child(row);
        }
    }

    private void rebuildRewards() {
        if (rewardsArea == null) return;
        rewardsArea.clearChildren();
        if (rewards.isEmpty()) rewardsArea.child(text(Component.translatable("gui.arenas_ld.arena.no_rewards"), INK_DIM));
        for (int i = 0; i < rewards.size(); i++) {
            MobArenaRewardData rw = rewards.get(i);
            int idx = i;
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
            row.surface(Surface.flat(ROW_BG));
            row.padding(Insets.of(2, 2, 4, 4));
            row.gap(3);
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.child(box(Sizing.expand(), rw.lootTableId, v -> rw.lootTableId = v));
            row.child(box(Sizing.fixed(28), String.valueOf(rw.rolls), v -> rw.rolls = parseInt(v, rw.rolls)));
            row.child(box(Sizing.fixed(30), String.valueOf(rw.minWave), v -> rw.minWave = parseInt(v, rw.minWave)));
            row.child(box(Sizing.fixed(30), String.valueOf(rw.maxWave), v -> rw.maxWave = parseInt(v, rw.maxWave)));
            row.child(box(Sizing.fixed(28), String.valueOf(rw.waveFrequency), v -> rw.waveFrequency = parseInt(v, rw.waveFrequency)));
            ButtonComponent pp = button(Component.literal(rw.perPlayer ? "each" : "one"), b -> { rw.perPlayer = !rw.perPlayer; rebuildRewards(); });
            pp.horizontalSizing(Sizing.fixed(40));
            row.child(pp);
            ButtonComponent rm = button(Component.literal("✕"), b -> { rewards.remove(idx); rebuildRewards(); });
            rm.sizing(Sizing.fixed(16), Sizing.fixed(16));
            row.child(rm);
            rewardsArea.child(row);
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
        TextBoxComponent f = settings.get(key);
        if (f == null) return def;
        try { return Integer.parseInt(f.getValue().trim()); } catch (NumberFormatException e) { return def; }
    }

    private double sd(String key, double def) {
        TextBoxComponent f = settings.get(key);
        if (f == null) return def;
        try { return Double.parseDouble(f.getValue().trim()); } catch (NumberFormatException e) { return def; }
    }

    private static int parseInt(String v, int fallback) {
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private FlowLayout intRow(String key, Component caption, int value) {
        return settingRow(key, caption, String.valueOf(value));
    }

    private FlowLayout doubleRow(String key, Component caption, double value) {
        String s = (value == Math.floor(value)) ? String.valueOf((long) value) : String.valueOf(value);
        return settingRow(key, caption, s);
    }

    private FlowLayout settingRow(String key, Component caption, String value) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        LabelComponent label = text(caption, INK_DIM);
        label.horizontalSizing(Sizing.fixed(190));
        row.child(label);
        TextBoxComponent box = Components.textBox(Sizing.fixed(70), value);
        box.verticalSizing(Sizing.fixed(15));
        settings.put(key, box);
        row.child(box);
        return row;
    }

    private FlowLayout box(Sizing width, String initial, Consumer<String> onChange) {
        FlowLayout wrap = Containers.horizontalFlow(width, Sizing.fixed(18));
        wrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        wrap.verticalAlignment(VerticalAlignment.CENTER);
        TextBoxComponent field = net.ledok.arenas_ld.screen.IdSuggestionDropdown.textBox(Sizing.expand(), initial, 256);
        field.verticalSizing(Sizing.fixed(15));
        field.onChanged().subscribe(onChange::accept);
        wrap.child(field);
        return wrap;
    }

    private FlowLayout header() {
        FlowLayout h = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(36));
        h.surface(Surface.flat(PANEL_2));
        h.padding(Insets.of(8, 8, 10, 10));
        h.gap(10);
        h.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        h.child(text(Component.translatable("block.arenas_ld.arena_spawner"), INK));
        h.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        ButtonComponent close = button(Component.literal("×"), b -> onClose());
        close.sizing(Sizing.fixed(22), Sizing.fixed(18));
        h.child(close);
        return h;
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
        b.sizing(Sizing.content(), Sizing.fixed(16));
        b.renderer((ctx, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ACCENT : ACCENT_DARK;
            ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), HAIRLINE);
        });
        return b;
    }
}
