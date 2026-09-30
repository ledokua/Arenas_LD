package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.packet.DbsClearRoomsPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsMoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsRemoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateDbsEntityDefPayload;
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
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.ledok.arenas_ld.screen.FitCanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.DANGER;
import static net.ledok.arenas_ld.screen.ArenasUi.GOOD;
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
import net.ledok.arenas_ld.platform.ArenasNetwork;

public class DungeonBossSpawnerScreen extends FitCanvasHandledScreen<DungeonBossSpawnerScreenHandler> {

    /** The old translucent danger-button renderer: faint DANGER fill, stronger on hover, DANGER outline. */
    private static final WidgetStyle DANGER_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x1F000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x44000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x44000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x1F000000, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, 0xFFFFFFFF, INK_DIM, INK_DIM, ACCENT, 0x80A98BE8,
            18, 16, 4, true);

    private String mobIdValue = "";
    private int waveValue = 1;
    private TextField waveField;
    private final List<BlockPos> rooms = new ArrayList<>();
    private final List<String> roomNames = new ArrayList<>();
    private java.util.Optional<BlockPos> startRoom = java.util.Optional.empty();
    private java.util.Optional<BlockPos> finalRoom = java.util.Optional.empty();

    private Flex contentArea;
    private Flex footerActions;
    private Label footerLabel;

    public DungeonBossSpawnerScreen(DungeonBossSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(600, 340), Placement.Screen.center());
        canvas.theme(ArenasUi.THEME);
        fillWindow();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The old owo screen swallowed the inventory key entirely (close via × or Esc only).
        if (ArenasUi.swallowsInventoryKey(input, keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        buildAll();
    }

    private void buildAll() {
        syncState();

        canvas.clear();
        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);

        float shellWidth  = Math.max(440, Math.min(560, canvas.width() - 24));
        float shellHeight = Math.max(300, canvas.height() - 24);
        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(shellWidth), Sizing.fixed(shellHeight));
        shell.backgroundFill(PANEL, HAIRLINE_HI, 1);

        shell.item(buildHeader());

        contentArea = Flex.column().gap(4).padding(Insets.of(10));
        contentArea.sizing(Sizing.fill(), Sizing.content());
        contentArea.backgroundFill(PANEL);
        ScrollPanel scroll = new ScrollPanel(100, 100, contentArea);
        scroll.sizing(Sizing.fill(), Sizing.expand());
        scroll.barWidth(8);
        scroll.wheelStep(18);
        shell.item(scroll);

        shell.item(buildFooter());

        root.layoutIn(canvas.width(), canvas.height());
        rebuildUi();
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
        info.item(ArenasUi.text(Component.translatable("gui.arenas_ld.boss_spawner"), INK));
        info.item(ArenasUi.text(Component.literal(
            "POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()), INK_DIM));
        header.item(info);

        header.spacer();

        header.item(ArenasUi.button(Component.literal("×"), 22, 18, this::onClose));
        return header;
    }

    private Flex buildFooter() {
        Flex footer = Flex.row().gap(6).padding(Insets.of(6)).alignItems(Align.CENTER);
        footer.sizing(Sizing.fill(), Sizing.fixed(34));
        footer.backgroundFill(PANEL_2);

        footerLabel = ArenasUi.label(100, Component.empty(), DANGER);
        footerLabel.sizing(Sizing.expand(), Sizing.content());
        footer.item(footerLabel);

        footerActions = Flex.row().gap(4);
        footerActions.sizing(Sizing.content(), Sizing.content());
        footer.item(footerActions);
        return footer;
    }

    private void rebuildUi() {
        if (contentArea == null) return;
        contentArea.clear();
        footerActions.clear();

        // General section
        contentArea.item(sectionLabel(Component.translatable("gui.arenas_ld.tab_general")));
        contentArea.item(ArenasUi.spacer(2));
        contentArea.item(buildMobIdSection());
        contentArea.item(ArenasUi.spacer(6));
        contentArea.item(buildWaveRow());
        contentArea.item(ArenasUi.spacer(6));

        contentArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.dbs.entrance_hint"), INK_DIM));
        contentArea.item(ArenasUi.spacer(6));

        Flex actionRow = Flex.row().gap(6).alignItems(Align.CENTER);
        actionRow.sizing(Sizing.fill(), Sizing.content());
        actionRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.attributes"), 110, 18, this::openAttributesScreen));
        actionRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.equipment"), 110, 18, this::openEquipmentScreen));
        contentArea.item(actionRow);

        contentArea.item(ArenasUi.spacer(10));

        // Rooms section
        contentArea.item(sectionLabel(Component.translatable("gui.arenas_ld.dbs.rooms")));
        contentArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.dbs.order_hint"), INK_DIM));
        contentArea.item(ArenasUi.spacer(2));

        if (rooms.isEmpty()) {
            contentArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.spawners_empty"), INK_DIM));
        } else {
            for (int i = 0; i < rooms.size(); i++) {
                if (i > 0) contentArea.item(ArenasUi.hairline());
                contentArea.item(roomRow(i));
            }
        }

        // Footer: apply mob ID + clear all
        footerActions.item(ArenasUi.button(Component.translatable("gui.arenas_ld.apply"), 80, 18, this::applyMobId));

        if (!rooms.isEmpty()) {
            Button clearAll = new Button(90, 18,
                Component.translatable("gui.arenas_ld.room_controller.button.clear_all").copy().withStyle(net.minecraft.ChatFormatting.RED),
                () -> {
                    if (minecraft == null) return;
                    minecraft.setScreen(new ConfirmScreen(ok -> {
                        minecraft.setScreen(this);
                        if (ok) ArenasNetwork.sendToServer(new DbsClearRoomsPayload(menu.getBlockPos()));
                    }, Component.translatable("gui.arenas_ld.room_controller.confirm.clear_title"),
                       Component.translatable("gui.arenas_ld.dbs.clear_rooms_confirm")));
                });
            clearAll.style(DANGER_STYLE);
            footerActions.item(clearAll);
        }
    }

    private Flex buildMobIdSection() {
        Flex container = Flex.column().gap(0);
        container.sizing(Sizing.fill(), Sizing.content());

        container.item(ArenasUi.text(Component.translatable("gui.arenas_ld.mob_id"), INK_DIM));
        container.item(ArenasUi.spacer(4));

        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldRow.item(accent);

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(100, mobIdValue, 256);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.changeListeners.add(v -> mobIdValue = v);
        fieldRow.item(field);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(BuiltInRegistries.ENTITY_TYPE, field);
        fieldRow.item(dropdown.chevron());
        container.item(fieldRow);
        container.item(dropdown.panel());

        return container;
    }

    private Flex roomRow(int index) {
        BlockPos room = rooms.get(index);
        Flex row = Flex.row().gap(4).padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(24));
        row.backgroundFill(index % 2 == 0 ? ROW_BG : ROW_BG_ALT);

        Label num = ArenasUi.label(26, Component.literal((index + 1) + ". "), INK_MID);
        num.sizing(Sizing.fixed(26), Sizing.content());
        row.item(num);

        String name = index < roomNames.size() ? roomNames.get(index) : "";
        String label = name == null || name.isBlank()
            ? room.toShortString()
            : name + " (" + room.toShortString() + ")";
        Label coord = ArenasUi.label(100, Component.literal(label), INK);
        coord.sizing(Sizing.expand(), Sizing.content());
        row.item(coord);

        if (startRoom.filter(room::equals).isPresent()) {
            row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.dbs.badge.start"), GOOD));
        }
        if (finalRoom.filter(room::equals).isPresent()) {
            row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.dbs.badge.final"), DANGER));
        }

        Button markStart = ArenasUi.button(Component.literal("S"), 20, 18, () ->
            ArenasNetwork.sendToServer(new net.ledok.arenas_ld.dungeon.packet.DbsSetRoomMarkerPayload(menu.getBlockPos(), room, false)));
        markStart.tooltip(Component.translatable("gui.arenas_ld.dbs.room_marker.start"));
        row.item(markStart);

        Button markFinal = ArenasUi.button(Component.literal("F"), 20, 18, () ->
            ArenasNetwork.sendToServer(new net.ledok.arenas_ld.dungeon.packet.DbsSetRoomMarkerPayload(menu.getBlockPos(), room, true)));
        markFinal.tooltip(Component.translatable("gui.arenas_ld.dbs.room_marker.final"));
        row.item(markFinal);

        Button up = ArenasUi.button(Component.literal("↑"), 20, 18, () ->
            ArenasNetwork.sendToServer(new DbsMoveRoomPayload(menu.getBlockPos(), index, Math.max(0, index - 1))));
        up.enabled(index > 0);
        row.item(up);

        Button down = ArenasUi.button(Component.literal("↓"), 20, 18, () ->
            ArenasNetwork.sendToServer(new DbsMoveRoomPayload(menu.getBlockPos(), index, Math.min(rooms.size() - 1, index + 1))));
        down.enabled(index < rooms.size() - 1);
        row.item(down);

        row.item(ArenasUi.button(Component.literal("×"), 20, 18, () ->
            ArenasNetwork.sendToServer(new DbsRemoveRoomPayload(menu.getBlockPos(), room))));

        return row;
    }

    private Flex buildWaveRow() {
        Flex row = Flex.row().gap(8).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());

        row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.dungeon_boss_spawner.ui.wave"), INK_DIM));
        row.spacer();

        Flex stepper = Flex.row().gap(2).padding(Insets.of(2, 4, 4, 2)).alignItems(Align.CENTER);
        stepper.sizing(Sizing.content(), Sizing.content());
        stepper.backgroundFill(PANEL_2, HAIRLINE, 1);

        stepper.item(ArenasUi.button(Component.literal("−"), 16, 16, () -> adjustWave(-1)));

        waveField = ArenasUi.textField(30, String.valueOf(waveValue), 32);
        waveField.size(30, 16);
        waveField.onChange(value -> {
            try {
                waveValue = Math.max(1, Math.min(10, Integer.parseInt(value.trim())));
            } catch (NumberFormatException ignored) {
            }
        });
        stepper.item(waveField);

        stepper.item(ArenasUi.button(Component.literal("+"), 16, 16, () -> adjustWave(1)));
        row.item(stepper);
        return row;
    }

    private void adjustWave(int delta) {
        waveValue = Math.max(1, Math.min(10, waveValue + delta));
        if (waveField != null) {
            waveField.text(String.valueOf(waveValue));
        }
    }

    private void applyMobId() {
        ArenasNetwork.sendToServer(new UpdateDbsEntityDefPayload(menu.getBlockPos(), mobIdValue, waveValue));
    }

    private void openAttributesScreen() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.setScreen(new MobAttributesScreen(
            new MobAttributesScreenHandler(menu.containerId, minecraft.player.getInventory(), new MobAttributesData(menu.getBlockPos())),
            minecraft.player.getInventory(),
            Component.translatable("gui.arenas_ld.boss_attributes")).returnTo(this));
    }

    private void openEquipmentScreen() {
        net.ledok.arenas_ld.platform.ArenasNetwork.sendToServer(
            new net.ledok.arenas_ld.networking.ModPackets.OpenEquipmentEditorPayload(menu.getBlockPos()));
    }

    public boolean matchesSpawner(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(DungeonBossSpawnerData data) {
        menu.applyData(data);
        syncState();
        rebuildUi();
    }

    private void syncState() {
        mobIdValue = menu.getMobId();
        waveValue = menu.getWave();
        rooms.clear();
        rooms.addAll(menu.getRooms());
        roomNames.clear();
        roomNames.addAll(menu.getRoomNames());
        startRoom = menu.getStartRoom();
        finalRoom = menu.getFinalRoom();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Flex sectionLabel(Component text) {
        Flex row = Flex.row().alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(ArenasUi.text(text, INK_DIM));
        return row;
    }
}
