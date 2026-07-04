package net.ledok.arenas_ld.dungeon.screen;

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
import net.ledok.arenas_ld.dungeon.packet.UpdateMobSpawnerEntityDefPayload;
import net.ledok.arenas_ld.screen.EquipmentScreen;
import net.ledok.arenas_ld.screen.EquipmentScreenData;
import net.ledok.arenas_ld.screen.EquipmentScreenHandler;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.ledok.arenas_ld.screen.MobAttributesData;
import net.ledok.arenas_ld.screen.MobAttributesScreen;
import net.ledok.arenas_ld.screen.MobAttributesScreenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public class MobSpawnerScreen extends BaseOwoHandledScreen<FlowLayout, MobSpawnerScreenHandler> {

    private static final int BG         = 0xFF070E14;
    private static final int PANEL      = 0xFF121922;
    private static final int PANEL_2    = 0xFF0C1218;
    private static final int HAIRLINE   = 0xFF283442;
    private static final int HAIRLINE_HI= 0xFF3A4A5C;
    private static final int ROW_BG     = 0xFF19222D;
    private static final int ROW_BG_ALT = 0xFF16202A;
    private static final int INK        = 0xFFE8EEF5;
    private static final int INK_MID    = 0xFF9AA8B8;
    private static final int INK_DIM    = 0xFF5F6E80;
    private static final int WARN       = 0xFFF5B042;
    private static final int ACCENT     = 0xFFA98BE8;
    private static final int ACCENT_DARK= 0xFF6C4FB5;
    private static final int DANGER     = 0xFFE8624A;
    private static final int GOOD       = 0xFF86D36C;

    private static final int POS_ROW_HEIGHT = 20;
    private static final int POS_MAX_VISIBLE = 6;

    private TextBoxComponent mobIdField;
    private TextBoxComponent spawnCountField;
    private TextBoxComponent waveField;
    private FlowLayout positionsList;
    private ScrollContainer<FlowLayout> positionsScroll;
    private TextBoxComponent addXField;
    private TextBoxComponent addYField;
    private TextBoxComponent addZField;

    private int spawnCount;
    private int wave;
    private final List<BlockPos> spawnOffsets = new ArrayList<>();

    public MobSpawnerScreen(MobSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.inventoryLabelY = 9999;
        this.titleLabelY = 9999;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        spawnCount = menu.getSpawnCount();
        wave = menu.getWave();
        spawnOffsets.clear();
        spawnOffsets.addAll(menu.getSpawnOffsets());

        int shellWidth = Math.min(340, Math.max(260, this.width - 40));

        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellWidth), Sizing.content());
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));

        shell.child(buildHeader());
        shell.child(buildContent());

        rootComponent.child(shell);
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(42));
        header.surface(Surface.flat(PANEL_2));
        header.padding(Insets.of(6));
        header.gap(8);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(18), Sizing.fixed(18));
        mark.surface(Surface.flat(WARN));
        header.child(mark);

        FlowLayout info = Containers.verticalFlow(Sizing.content(), Sizing.content());
        info.gap(3);

        FlowLayout titleLine = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        titleLine.gap(8);
        titleLine.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent titleLabel = Components.label(Component.translatable("gui.arenas_ld.mob_spawner.title"));
        titleLabel.color(Color.ofArgb(INK));
        titleLine.child(titleLabel);
        titleLine.child(badge(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.op")), WARN));
        info.child(titleLine);

        FlowLayout meta = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        meta.gap(8);
        meta.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        meta.child(metaLabel("POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()));
        info.child(meta);

        header.child(info);
        header.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));

        ButtonComponent close = smallButton(Component.literal("X"), b -> onClose());
        close.sizing(Sizing.fixed(20), Sizing.fixed(16));
        header.child(close);
        return header;
    }

    private FlowLayout buildContent() {
        FlowLayout content = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.padding(Insets.of(10));
        content.gap(8);

        // MOB ID
        content.child(sectionCaption(tr("gui.arenas_ld.mob_spawner.ui.mob_id")));
        content.child(buildMobIdSection());

        content.child(hairline());

        // SPAWN COUNT
        content.child(sectionCaption(tr("gui.arenas_ld.mob_spawner.ui.spawn_settings")));
        FlowLayout countRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        countRow.gap(8);
        countRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent countCaption = Components.label(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.count")));
        countCaption.color(Color.ofArgb(INK_MID));
        countRow.child(countCaption);
        countRow.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
        countRow.child(buildCountStepper());
        content.child(countRow);

        FlowLayout waveRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        waveRow.gap(8);
        waveRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent waveCaption = Components.label(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.wave")));
        waveCaption.color(Color.ofArgb(INK_MID));
        waveRow.child(waveCaption);
        waveRow.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
        waveRow.child(buildWaveStepper());
        content.child(waveRow);

        content.child(hairline());

        // SPAWN POSITIONS
        FlowLayout posHeaderRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        posHeaderRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        posHeaderRow.gap(8);
        LabelComponent posCaption = Components.label(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.spawn_positions")));
        posCaption.color(Color.ofArgb(INK_MID));
        posHeaderRow.child(posCaption);
        posHeaderRow.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
        content.child(posHeaderRow);

        positionsList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        positionsScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(POS_ROW_HEIGHT), positionsList);
        positionsScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        positionsScroll.scrollbarThiccness(4);
        positionsScroll.scrollStep(POS_ROW_HEIGHT);
        rebuildPositionsList();
        content.child(positionsScroll);

        content.child(buildAddRow());

        content.child(hairline());

        // ACTION BUTTONS
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        ButtonComponent attrsBtn = Components.button(
            Component.literal(tr("gui.arenas_ld.mob_spawner.ui.attributes")),
            b -> openAttributesScreen()
        );
        attrsBtn.sizing(Sizing.fill(100), Sizing.fixed(20));
        ButtonComponent equipBtn = Components.button(
            Component.literal(tr("gui.arenas_ld.mob_spawner.ui.equipment")),
            b -> openEquipmentScreen()
        );
        equipBtn.sizing(Sizing.fill(100), Sizing.fixed(20));

        FlowLayout leftCell = Containers.verticalFlow(Sizing.fill(50), Sizing.content());
        leftCell.padding(Insets.of(0, 0, 0, 3));
        leftCell.child(attrsBtn);
        FlowLayout rightCell = Containers.verticalFlow(Sizing.fill(50), Sizing.content());
        rightCell.padding(Insets.of(0, 0, 3, 0));
        rightCell.child(equipBtn);
        actions.child(leftCell);
        actions.child(rightCell);
        content.child(actions);

        return content;
    }

    private FlowLayout buildCountStepper() {
        FlowLayout stepper = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        stepper.gap(2);
        stepper.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        stepper.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        stepper.padding(Insets.of(2, 4, 2, 4));

        ButtonComponent minus = stepBtn("−", b -> adjustCount(-1));

        spawnCountField = Components.textBox(Sizing.fixed(30), String.valueOf(spawnCount));
        spawnCountField.verticalSizing(Sizing.fixed(16));
        // 0 is a legal stored value ("unconfigured", spawns a single mob at the spawner).
        spawnCountField.onChanged().subscribe(value -> {
            try {
                spawnCount = Math.max(0, Math.min(64, Integer.parseInt(value.trim())));
            } catch (NumberFormatException ignored) {
            }
        });

        ButtonComponent plus = stepBtn("+", b -> adjustCount(1));

        stepper.child(minus);
        stepper.child(spawnCountField);
        stepper.child(plus);
        return stepper;
    }

    private void adjustCount(int delta) {
        spawnCount = Math.max(0, Math.min(64, spawnCount + delta));
        if (spawnCountField != null) {
            spawnCountField.text(String.valueOf(spawnCount));
        }
    }

    private FlowLayout buildWaveStepper() {
        FlowLayout stepper = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        stepper.gap(2);
        stepper.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        stepper.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        stepper.padding(Insets.of(2, 4, 2, 4));

        ButtonComponent minus = stepBtn("−", b -> adjustWave(-1));

        waveField = Components.textBox(Sizing.fixed(30), String.valueOf(wave));
        waveField.verticalSizing(Sizing.fixed(16));
        waveField.onChanged().subscribe(value -> {
            try {
                wave = Math.max(1, Math.min(10, Integer.parseInt(value.trim())));
            } catch (NumberFormatException ignored) {
            }
        });

        ButtonComponent plus = stepBtn("+", b -> adjustWave(1));

        stepper.child(minus);
        stepper.child(waveField);
        stepper.child(plus);
        return stepper;
    }

    private void adjustWave(int delta) {
        wave = Math.max(1, Math.min(10, wave + delta));
        if (waveField != null) {
            waveField.text(String.valueOf(wave));
        }
    }

    private void rebuildPositionsList() {
        positionsList.clearChildren();
        if (spawnOffsets.isEmpty()) {
            FlowLayout emptyRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(POS_ROW_HEIGHT));
            emptyRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            emptyRow.padding(Insets.of(0, 0, 6, 6));
            LabelComponent empty = Components.label(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.no_positions")));
            empty.color(Color.ofArgb(INK_DIM));
            emptyRow.child(empty);
            positionsList.child(emptyRow);
        } else {
            for (int i = 0; i < spawnOffsets.size(); i++) {
                positionsList.child(buildPositionRow(i));
            }
        }
        if (positionsScroll != null) {
            int visibleRows = Math.min(Math.max(spawnOffsets.size(), 1), POS_MAX_VISIBLE);
            positionsScroll.verticalSizing(Sizing.fixed(visibleRows * POS_ROW_HEIGHT));
        }
    }

    private FlowLayout buildPositionRow(int index) {
        BlockPos offset = spawnOffsets.get(index);
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(POS_ROW_HEIGHT));
        row.surface(index % 2 == 0 ? Surface.flat(ROW_BG) : Surface.flat(ROW_BG_ALT));
        row.padding(Insets.of(0, 0, 6, 6));
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        IntFunction<String> sign = x -> x >= 0 ? "+" + x : String.valueOf(x);
        String coordText = sign.apply(offset.getX()) + ", " + sign.apply(offset.getY()) + ", " + sign.apply(offset.getZ());
        LabelComponent coordLabel = Components.label(Component.literal(coordText));
        coordLabel.color(Color.ofArgb(INK_MID));
        row.child(coordLabel);

        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));

        int capturedIndex = index;
        ButtonComponent removeBtn = smallButton(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.remove")), b -> {
            spawnOffsets.remove(capturedIndex);
            rebuildPositionsList();
        });
        removeBtn.sizing(Sizing.content(), Sizing.fixed(16));
        row.child(removeBtn);
        return row;
    }

    private FlowLayout buildAddRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.padding(Insets.of(4, 0, 0, 0));

        LabelComponent xLbl = Components.label(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.pos_x")));
        xLbl.color(Color.ofArgb(INK_DIM));
        addXField = Components.textBox(Sizing.fixed(36), "0");
        addXField.verticalSizing(Sizing.fixed(16));

        LabelComponent yLbl = Components.label(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.pos_y")));
        yLbl.color(Color.ofArgb(INK_DIM));
        addYField = Components.textBox(Sizing.fixed(36), "0");
        addYField.verticalSizing(Sizing.fixed(16));

        LabelComponent zLbl = Components.label(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.pos_z")));
        zLbl.color(Color.ofArgb(INK_DIM));
        addZField = Components.textBox(Sizing.fixed(36), "0");
        addZField.verticalSizing(Sizing.fixed(16));

        ButtonComponent addBtn = accentButton(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.add_position")), b -> {
            try {
                int x = Integer.parseInt(addXField.getValue().trim());
                int y = Integer.parseInt(addYField.getValue().trim());
                int z = Integer.parseInt(addZField.getValue().trim());
                spawnOffsets.add(new BlockPos(x, y, z));
                rebuildPositionsList();
                addXField.text("0");
                addYField.text("0");
                addZField.text("0");
            } catch (NumberFormatException ignored) {}
        });
        addBtn.sizing(Sizing.content(), Sizing.fixed(16));

        row.child(xLbl);
        row.child(addXField);
        row.child(yLbl);
        row.child(addYField);
        row.child(zLbl);
        row.child(addZField);
        row.child(addBtn);
        return row;
    }

    private FlowLayout buildMobIdSection() {
        FlowLayout container = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.gap(0);

        FlowLayout fieldRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldRow.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldRow.child(accent);

        mobIdField = Components.textBox(Sizing.expand(), menu.getMobId());
        mobIdField.verticalSizing(Sizing.fixed(18));
        fieldRow.child(mobIdField);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(
            this.font, BuiltInRegistries.ENTITY_TYPE, mobIdField);
        fieldRow.child(dropdown.chevron());

        ButtonComponent applyBtn = accentButton(Component.literal(tr("gui.arenas_ld.mob_spawner.ui.apply")), b -> sendApply());
        applyBtn.sizing(Sizing.fixed(64), Sizing.fixed(18));
        fieldRow.child(applyBtn);

        container.child(fieldRow);
        container.child(dropdown.panel());

        return container;
    }

    private void sendApply() {
        ClientPlayNetworking.send(new UpdateMobSpawnerEntityDefPayload(
            menu.getBlockPos(),
            mobIdField.getValue(),
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- Helpers ----

    private FlowLayout badge(Component text, int color) {
        FlowLayout tag = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        tag.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        tag.padding(Insets.of(4, 2, 4, 4));
        tag.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(color));
        tag.child(label);
        return tag;
    }

    private LabelComponent sectionCaption(String text) {
        LabelComponent label = Components.label(Component.literal(text));
        label.color(Color.ofArgb(INK_DIM));
        return label;
    }

    private FlowLayout hairline() {
        FlowLayout line = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        line.surface(Surface.flat(HAIRLINE));
        return line;
    }

    private ButtonComponent smallButton(Component text, Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text, action);
        button.sizing(Sizing.content(), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.active() ? (rendered.isHoveredOrFocused() ? ACCENT : ACCENT_DARK) : PANEL;
            int border = rendered.active() ? ACCENT : HAIRLINE;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
        });
        return button;
    }

    private ButtonComponent accentButton(Component text, Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text, action);
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ACCENT : ACCENT_DARK;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), ACCENT);
        });
        return button;
    }

    private ButtonComponent stepBtn(String glyph, Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(Component.literal(glyph), action);
        button.sizing(Sizing.fixed(16), Sizing.fixed(16));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ROW_BG : PANEL_2;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), HAIRLINE);
        });
        return button;
    }

    private LabelComponent metaLabel(String text) {
        LabelComponent label = Components.label(Component.literal(text));
        label.color(Color.ofArgb(INK_DIM));
        return label;
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }
}
