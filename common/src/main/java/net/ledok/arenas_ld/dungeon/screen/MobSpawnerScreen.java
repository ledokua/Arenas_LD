package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.UpdateMobSpawnerEntityDefPayload;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.ledok.arenas_ld.screen.MobAttributesData;
import net.ledok.arenas_ld.screen.MobAttributesScreen;
import net.ledok.arenas_ld.screen.MobAttributesScreenHandler;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.ledok.vectorlib.client.presentation.CanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.BG;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE_HI;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_MID;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;
import static net.ledok.arenas_ld.screen.ArenasUi.ROW_BG;
import static net.ledok.arenas_ld.screen.ArenasUi.ROW_BG_ALT;
import static net.ledok.arenas_ld.screen.ArenasUi.WARN;

public class MobSpawnerScreen extends CanvasHandledScreen<MobSpawnerScreenHandler> {

    private static final int POS_ROW_HEIGHT = 20;
    private static final int POS_MAX_VISIBLE = 6;

    /** The old subtle stepper-button renderer: PANEL_2 fill, ROW_BG on hover, HAIRLINE outline. */
    private static final WidgetStyle STEP_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, 0xFFFFFFFF, INK_DIM, INK_DIM, ACCENT, 0x80A98BE8,
            16, 16, 4, true);

    private IdSuggestionDropdown.Field mobIdField;
    private TextField spawnCountField;
    private TextField waveField;
    private Flex positionsList;
    private ScrollPanel positionsScroll;
    private TextField addXField;
    private TextField addYField;
    private TextField addZField;

    private int spawnCount;
    private int wave;
    private final List<BlockPos> spawnOffsets = new ArrayList<>();

    public MobSpawnerScreen(MobSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(600, 340), Placement.Screen.center());
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

    @Override
    protected void onCanvasResized(float width, float height) {
        buildAll();
    }

    private void buildAll() {
        canvas.clear();
        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);
        root.backgroundFill(BG);

        spawnCount = menu.getSpawnCount();
        wave = menu.getWave();
        spawnOffsets.clear();
        spawnOffsets.addAll(menu.getSpawnOffsets());

        float shellWidth = Math.min(340, Math.max(260, canvas.width() - 40));

        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(shellWidth), Sizing.content());
        shell.backgroundFill(PANEL, HAIRLINE_HI, 1);

        shell.item(buildHeader());
        shell.item(buildContent());

        root.layoutIn(canvas.width(), canvas.height());
    }

    private Flex buildHeader() {
        Flex header = Flex.row().gap(8).padding(Insets.of(6)).alignItems(Align.CENTER);
        header.sizing(Sizing.fill(), Sizing.fixed(42));
        header.backgroundFill(PANEL_2);

        Flex mark = Flex.column();
        mark.sizing(Sizing.fixed(18), Sizing.fixed(18));
        mark.backgroundFill(WARN);
        header.item(mark);

        Flex info = Flex.column().gap(3);
        info.sizing(Sizing.content(), Sizing.content());

        Flex titleLine = Flex.row().gap(8).alignItems(Align.CENTER);
        titleLine.sizing(Sizing.content(), Sizing.content());
        titleLine.item(ArenasUi.text(Component.translatable("gui.arenas_ld.mob_spawner.title"), INK));
        titleLine.item(badge(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.op")), WARN));
        info.item(titleLine);

        Flex meta = Flex.row().gap(8).alignItems(Align.CENTER);
        meta.sizing(Sizing.content(), Sizing.content());
        meta.item(ArenasUi.text(Component.literal(
            "POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()), INK_DIM));
        info.item(meta);

        header.item(info);
        header.spacer();
        header.item(ArenasUi.button(Component.literal("X"), 20, 16, this::onClose));
        return header;
    }

    private Flex buildContent() {
        Flex content = Flex.column().gap(8).padding(Insets.of(10));
        content.sizing(Sizing.fill(), Sizing.content());

        // MOB ID
        content.item(sectionCaption(tr("gui.arenas_ld.mob_spawner.ui.mob_id")));
        content.item(buildMobIdSection());

        content.item(ArenasUi.hairline());

        // SPAWN COUNT
        content.item(sectionCaption(tr("gui.arenas_ld.mob_spawner.ui.spawn_settings")));
        Flex countRow = Flex.row().gap(8).alignItems(Align.CENTER);
        countRow.sizing(Sizing.fill(), Sizing.content());
        countRow.item(ArenasUi.text(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.count")), INK_MID));
        countRow.spacer();
        countRow.item(buildCountStepper());
        content.item(countRow);

        Flex waveRow = Flex.row().gap(8).alignItems(Align.CENTER);
        waveRow.sizing(Sizing.fill(), Sizing.content());
        waveRow.item(ArenasUi.text(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.wave")), INK_MID));
        waveRow.spacer();
        waveRow.item(buildWaveStepper());
        content.item(waveRow);

        content.item(ArenasUi.hairline());

        // SPAWN POSITIONS
        Flex posHeaderRow = Flex.row().gap(8).alignItems(Align.CENTER);
        posHeaderRow.sizing(Sizing.fill(), Sizing.content());
        posHeaderRow.item(ArenasUi.text(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.spawn_positions")), INK_MID));
        posHeaderRow.spacer();
        content.item(posHeaderRow);

        positionsList = Flex.column();
        positionsList.sizing(Sizing.fill(), Sizing.content());
        positionsScroll = new ScrollPanel(100, POS_ROW_HEIGHT, positionsList);
        positionsScroll.sizing(Sizing.fill(), Sizing.fixed(POS_ROW_HEIGHT));
        positionsScroll.barWidth(4);
        positionsScroll.wheelStep(POS_ROW_HEIGHT);
        rebuildPositionsList();
        content.item(positionsScroll);

        content.item(buildAddRow());

        content.item(ArenasUi.hairline());

        // ACTION BUTTONS
        Flex actions = Flex.row();
        actions.sizing(Sizing.fill(), Sizing.content());

        Button attrsBtn = ArenasUi.button(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.attributes")),
            100, 20, this::openAttributesScreen);
        attrsBtn.sizing(Sizing.fill(), Sizing.fixed(20));
        Button equipBtn = ArenasUi.button(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.equipment")),
            100, 20, this::openEquipmentScreen);
        equipBtn.sizing(Sizing.fill(), Sizing.fixed(20));

        Flex leftCell = Flex.column().padding(Insets.of(0, 3, 0, 0));
        leftCell.sizing(Sizing.fill(0.5f), Sizing.content());
        leftCell.item(attrsBtn);
        Flex rightCell = Flex.column().padding(Insets.of(0, 0, 0, 3));
        rightCell.sizing(Sizing.fill(0.5f), Sizing.content());
        rightCell.item(equipBtn);
        actions.item(leftCell);
        actions.item(rightCell);
        content.item(actions);

        return content;
    }

    private Flex buildCountStepper() {
        Flex stepper = Flex.row().gap(2).padding(Insets.of(2, 4, 4, 2)).alignItems(Align.CENTER);
        stepper.sizing(Sizing.content(), Sizing.content());
        stepper.backgroundFill(PANEL_2, HAIRLINE, 1);

        stepper.item(stepBtn("−", () -> adjustCount(-1)));

        spawnCountField = ArenasUi.textField(30, String.valueOf(spawnCount), 32);
        spawnCountField.size(30, 16);
        // 0 is a legal stored value ("unconfigured", spawns a single mob at the spawner).
        spawnCountField.onChange(value -> {
            try {
                spawnCount = Math.max(0, Math.min(64, Integer.parseInt(value.trim())));
            } catch (NumberFormatException ignored) {
            }
        });
        stepper.item(spawnCountField);

        stepper.item(stepBtn("+", () -> adjustCount(1)));
        return stepper;
    }

    private void adjustCount(int delta) {
        spawnCount = Math.max(0, Math.min(64, spawnCount + delta));
        if (spawnCountField != null) {
            spawnCountField.text(String.valueOf(spawnCount));
        }
    }

    private Flex buildWaveStepper() {
        Flex stepper = Flex.row().gap(2).padding(Insets.of(2, 4, 4, 2)).alignItems(Align.CENTER);
        stepper.sizing(Sizing.content(), Sizing.content());
        stepper.backgroundFill(PANEL_2, HAIRLINE, 1);

        stepper.item(stepBtn("−", () -> adjustWave(-1)));

        waveField = ArenasUi.textField(30, String.valueOf(wave), 32);
        waveField.size(30, 16);
        waveField.onChange(value -> {
            try {
                wave = Math.max(1, Math.min(10, Integer.parseInt(value.trim())));
            } catch (NumberFormatException ignored) {
            }
        });
        stepper.item(waveField);

        stepper.item(stepBtn("+", () -> adjustWave(1)));
        return stepper;
    }

    private void adjustWave(int delta) {
        wave = Math.max(1, Math.min(10, wave + delta));
        if (waveField != null) {
            waveField.text(String.valueOf(wave));
        }
    }

    private void rebuildPositionsList() {
        positionsList.clear();
        if (spawnOffsets.isEmpty()) {
            Flex emptyRow = Flex.row().padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
            emptyRow.sizing(Sizing.fill(), Sizing.fixed(POS_ROW_HEIGHT));
            emptyRow.item(ArenasUi.text(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.no_positions")), INK_DIM));
            positionsList.item(emptyRow);
        } else {
            for (int i = 0; i < spawnOffsets.size(); i++) {
                positionsList.item(buildPositionRow(i));
            }
        }
        if (positionsScroll != null) {
            int visibleRows = Math.min(Math.max(spawnOffsets.size(), 1), POS_MAX_VISIBLE);
            positionsScroll.sizing(Sizing.fill(), Sizing.fixed(visibleRows * POS_ROW_HEIGHT));
        }
    }

    private Flex buildPositionRow(int index) {
        BlockPos offset = spawnOffsets.get(index);
        Flex row = Flex.row().gap(6).padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(POS_ROW_HEIGHT));
        row.backgroundFill(index % 2 == 0 ? ROW_BG : ROW_BG_ALT);

        IntFunction<String> sign = x -> x >= 0 ? "+" + x : String.valueOf(x);
        String coordText = sign.apply(offset.getX()) + ", " + sign.apply(offset.getY()) + ", " + sign.apply(offset.getZ());
        row.item(ArenasUi.text(Component.literal(coordText), INK_MID));

        row.spacer();

        int capturedIndex = index;
        Component removeLabel = Component.literal(tr("gui.arenas_ld.mob_spawner.ui.remove"));
        row.item(ArenasUi.button(removeLabel, Minecraft.getInstance().font.width(removeLabel) + 12, 16, () -> {
            spawnOffsets.remove(capturedIndex);
            rebuildPositionsList();
        }));
        return row;
    }

    private Flex buildAddRow() {
        Flex row = Flex.row().gap(4).padding(Insets.of(4, 0, 0, 0)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());

        addXField = ArenasUi.textField(36, "0", 32);
        addXField.size(36, 16);
        addYField = ArenasUi.textField(36, "0", 32);
        addYField.size(36, 16);
        addZField = ArenasUi.textField(36, "0", 32);
        addZField.size(36, 16);

        Component addLabel = Component.literal(tr("gui.arenas_ld.mob_spawner.ui.add_position"));
        Button addBtn = ArenasUi.button(addLabel, Minecraft.getInstance().font.width(addLabel) + 12, 16, () -> {
            try {
                int x = Integer.parseInt(addXField.text().trim());
                int y = Integer.parseInt(addYField.text().trim());
                int z = Integer.parseInt(addZField.text().trim());
                spawnOffsets.add(new BlockPos(x, y, z));
                rebuildPositionsList();
                addXField.text("0");
                addYField.text("0");
                addZField.text("0");
            } catch (NumberFormatException ignored) {}
        });

        row.item(ArenasUi.text(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.pos_x")), INK_DIM));
        row.item(addXField);
        row.item(ArenasUi.text(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.pos_y")), INK_DIM));
        row.item(addYField);
        row.item(ArenasUi.text(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.pos_z")), INK_DIM));
        row.item(addZField);
        row.item(addBtn);
        return row;
    }

    private Flex buildMobIdSection() {
        Flex container = Flex.column().gap(0);
        container.sizing(Sizing.fill(), Sizing.content());

        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldRow.item(accent);

        mobIdField = IdSuggestionDropdown.textBox(100, menu.getMobId(), 256);
        mobIdField.sizing(Sizing.expand(), Sizing.fixed(18));
        fieldRow.item(mobIdField);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(BuiltInRegistries.ENTITY_TYPE, mobIdField);
        fieldRow.item(dropdown.chevron());

        fieldRow.item(ArenasUi.button(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.apply")), 64, 18, this::sendApply));

        container.item(fieldRow);
        container.item(dropdown.panel());

        return container;
    }

    private void sendApply() {
        ClientPlayNetworking.send(new UpdateMobSpawnerEntityDefPayload(
            menu.getBlockPos(),
            mobIdField.text(),
            spawnCount,
            wave,
            List.copyOf(spawnOffsets)
        ));
    }

    private void openAttributesScreen() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.setScreen(new MobAttributesScreen(
                new MobAttributesScreenHandler(menu.containerId, minecraft.player.getInventory(), new MobAttributesData(menu.getBlockPos())),
                minecraft.player.getInventory(),
                Component.translatable("gui.arenas_ld.mob_attributes")
            ));
        }
    }

    private void openEquipmentScreen() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new net.ledok.arenas_ld.networking.ModPackets.OpenEquipmentEditorPayload(menu.getBlockPos()));
    }

    public boolean matchesSpawner(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(MobSpawnerData data) {
        menu.applyData(data);
        if (mobIdField != null) {
            mobIdField.text(menu.getMobId());
            mobIdField.notifyChanged();
        }
        spawnCount = menu.getSpawnCount();
        if (spawnCountField != null) {
            spawnCountField.text(String.valueOf(spawnCount));
        }
        wave = menu.getWave();
        if (waveField != null) {
            waveField.text(String.valueOf(wave));
        }
        spawnOffsets.clear();
        spawnOffsets.addAll(menu.getSpawnOffsets());
        if (positionsList != null) {
            rebuildPositionsList();
        }
    }

    // ---- Helpers ----

    private Flex badge(Component text, int color) {
        Flex tag = Flex.row().padding(Insets.of(4, 4, 2, 4));
        tag.sizing(Sizing.content(), Sizing.content());
        tag.justify(Justify.CENTER).alignItems(Align.CENTER);
        tag.backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        tag.item(ArenasUi.text(text, color));
        return tag;
    }

    private net.ledok.vectorlib.client.canvas.TextNode sectionCaption(String text) {
        return ArenasUi.text(Component.literal(text), INK_DIM);
    }

    private Button stepBtn(String glyph, Runnable action) {
        Button button = new Button(16, 16, Component.literal(glyph), action);
        button.style(STEP_STYLE);
        return button;
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }
}
