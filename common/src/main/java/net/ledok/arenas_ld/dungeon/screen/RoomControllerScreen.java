package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.RoomClearDoorPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearProtectPosPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearRespawnPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearSpawnersPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomRemoveSpawnerPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomResetPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomSetNamePayload;
import net.ledok.arenas_ld.dungeon.packet.RoomSetObjectivePayload;
import net.ledok.arenas_ld.dungeon.room.RoomObjectiveConfig;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.ledok.arenas_ld.screen.FitCanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT_DARK;
import static net.ledok.arenas_ld.screen.ArenasUi.DANGER;
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

public class RoomControllerScreen extends FitCanvasHandledScreen<RoomControllerScreenHandler> {

    /** Selected segment of the objective-type selector (old owo renderer: accent fill, accent border). */
    private static final WidgetStyle SEGMENT_ON = new WidgetStyle(
            new WidgetStyle.Skin.Flat(ACCENT_DARK, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(ACCENT, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(ACCENT_DARK, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(0xFF232B36, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, 0xFFFFFFFF, INK_DIM, INK_DIM, ACCENT, 0x80A98BE8,
            18, 16, 4, true);

    /** Unselected segment (old owo renderer: panel fill, hairline border, row highlight on hover). */
    private static final WidgetStyle SEGMENT_OFF = new WidgetStyle(
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, 0xFFFFFFFF, INK_DIM, INK_DIM, HAIRLINE, 0x80A98BE8,
            18, 16, 4, true);

    /** Destructive footer actions (old owo renderer: translucent danger fill, danger border). */
    private static final WidgetStyle DANGER_BUTTON = new WidgetStyle(
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x1F000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x44000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x44000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, 0xFFFFFFFF, INK_DIM, INK_DIM, DANGER, 0x80A98BE8,
            18, 16, 4, true);

    private final List<RoomControllerData.SpawnerEntry> spawners = new ArrayList<>();
    private List<BlockPos> doorPositions = List.of();
    private List<BlockPos> respawnPositions = List.of();
    private String roomNameInput = "";
    private RoomObjectiveConfig.Type objectiveType = RoomObjectiveConfig.Type.KILL_ALL;
    private int entryPercentInput = RoomObjectiveConfig.DEFAULT_ENTRY_PERCENT;
    private int surviveSecondsInput = 60;
    private int surviveIntervalInput = 15;
    private String protectMobIdInput = "minecraft:villager";
    private boolean protectStationaryInput = true;
    private final List<net.ledok.arenas_ld.util.AttributeData> protectAttributesInput = new ArrayList<>();
    private String newAttrIdInput = "";
    private String newAttrValueInput = "";

    private Flex contentArea;
    private Flex footerActions;
    private Label footerLabel;

    public RoomControllerScreen(RoomControllerScreenHandler handler, Inventory inventory, Component title) {
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
        info.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.title"), INK));
        info.item(ArenasUi.text(Component.literal(
            "POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()), INK_DIM));
        header.item(info);

        header.spacer();
        header.item(ArenasUi.button(Component.literal("×"), 22, 18, this::onClose));
        return header;
    }

    private Flex buildFooter() {
        Flex footer = Flex.row().gap(6).padding(Insets.of(6));
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

        // ── Room ────────────────────────────────────────────────────────────
        contentArea.item(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.room")));
        contentArea.item(ArenasUi.spacer(2));
        contentArea.item(buildNameRow());
        contentArea.item(ArenasUi.spacer(8));

        // ── Objective ───────────────────────────────────────────────────────
        contentArea.item(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.objective")));
        contentArea.item(ArenasUi.spacer(2));
        contentArea.item(buildObjectiveTypeRow());
        contentArea.item(ArenasUi.spacer(2));
        contentArea.item(buildEntryPercentRow());
        if (objectiveType == RoomObjectiveConfig.Type.SURVIVE) {
            contentArea.item(ArenasUi.spacer(2));
            contentArea.item(buildSurviveSecondsRow());
        }
        if (objectiveType == RoomObjectiveConfig.Type.PROTECT) {
            contentArea.item(ArenasUi.spacer(2));
            buildProtectRows(contentArea);
        }
        contentArea.item(ArenasUi.spacer(8));

        // ── Spawners ────────────────────────────────────────────────────────
        contentArea.item(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.spawners")
            .copy().append(Component.literal(" · " + spawners.size()))));
        contentArea.item(ArenasUi.spacer(2));
        if (spawners.isEmpty()) {
            contentArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.spawners_empty"), INK_DIM));
        } else {
            for (int i = 0; i < spawners.size(); i++) {
                if (i > 0) contentArea.item(ArenasUi.hairline());
                contentArea.item(spawnerRow(i));
            }
        }
        contentArea.item(ArenasUi.spacer(8));

        // ── Door ────────────────────────────────────────────────────────────
        contentArea.item(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.door")));
        contentArea.item(ArenasUi.spacer(2));
        contentArea.item(buildDoorRow());
        contentArea.item(ArenasUi.spacer(8));

        // ── Respawn point ───────────────────────────────────────────────────
        contentArea.item(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.respawn")));
        contentArea.item(ArenasUi.spacer(2));
        contentArea.item(buildRespawnRow());
        contentArea.item(ArenasUi.spacer(8));

        // ── Rewards (edited in a dedicated screen) ──────────────────────────
        contentArea.item(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.rewards")));
        contentArea.item(ArenasUi.spacer(2));
        Flex rewardsRow = Flex.row().gap(8).alignItems(Align.CENTER);
        rewardsRow.sizing(Sizing.fill(), Sizing.content());
        rewardsRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.reward_hint"), INK_DIM));
        rewardsRow.spacer();
        rewardsRow.item(ArenasUi.button(
            Component.translatable("gui.arenas_ld.room_controller.button.rewards"), this::openRewardsScreen));
        contentArea.item(rewardsRow);

        // ── Footer actions ──────────────────────────────────────────────────

        if (!spawners.isEmpty()) {
            footerActions.item(dangerButton(
                Component.translatable("gui.arenas_ld.room_controller.button.clear_all"),
                () -> {
                    if (minecraft == null) return;
                    minecraft.setScreen(new ConfirmScreen(ok -> {
                        minecraft.setScreen(this);
                        if (ok) ClientPlayNetworking.send(new RoomClearSpawnersPayload(menu.getBlockPos()));
                    }, Component.translatable("gui.arenas_ld.room_controller.confirm.clear_title"),
                       Component.translatable("gui.arenas_ld.room_controller.confirm.clear_message")));
                }));
        }

        footerActions.item(dangerButton(
            Component.translatable("gui.arenas_ld.room_controller.button.reset"),
            () -> {
                if (minecraft == null) return;
                minecraft.setScreen(new ConfirmScreen(ok -> {
                    minecraft.setScreen(this);
                    if (ok) ClientPlayNetworking.send(new RoomResetPayload(menu.getBlockPos()));
                }, Component.translatable("gui.arenas_ld.room_controller.confirm.reset_title"),
                   Component.translatable("gui.arenas_ld.room_controller.confirm.reset_message")));
            }));
    }

    private Flex buildNameRow() {
        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        fieldRow.item(accentBar());

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(100, roomNameInput, 48);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.changeListeners.add(v -> roomNameInput = v);
        IdSuggestionDropdown.attachFullValueTooltip(field);
        fieldRow.item(field);

        fieldRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.room_controller.button.set_name"), 64, 18,
            () -> ClientPlayNetworking.send(new RoomSetNamePayload(menu.getBlockPos(), roomNameInput == null ? "" : roomNameInput))));
        return fieldRow;
    }

    private Flex buildObjectiveTypeRow() {
        Flex row = Flex.row().gap(4).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        for (RoomObjectiveConfig.Type type : RoomObjectiveConfig.Type.values()) {
            row.item(objectiveTypeButton(type));
        }
        return row;
    }

    private Button objectiveTypeButton(RoomObjectiveConfig.Type type) {
        Button button = ArenasUi.button(
            Component.translatable("gui.arenas_ld.room_controller.objective." + type.getSerializedName()),
            () -> ClientPlayNetworking.send(new RoomSetObjectivePayload(menu.getBlockPos(), editedObjective(type))));
        button.style(objectiveType == type ? SEGMENT_ON : SEGMENT_OFF);
        return button;
    }

    private Flex buildSurviveSecondsRow() {
        Flex fieldRow = Flex.row().gap(6).alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        fieldRow.item(accentBar());

        fieldRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.objective.survive_seconds"), INK_MID));

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(48, String.valueOf(surviveSecondsInput), 4);
        field.size(48, 18);
        field.changeListeners.add(v -> {
            try {
                surviveSecondsInput = Math.clamp(Integer.parseInt(v.trim()), 1, 3600);
            } catch (NumberFormatException ignored) {
            }
        });
        fieldRow.item(field);

        fieldRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.objective.wave_interval"), INK_MID));

        IdSuggestionDropdown.Field intervalField = IdSuggestionDropdown.textBox(48, String.valueOf(surviveIntervalInput), 3);
        intervalField.size(48, 18);
        intervalField.changeListeners.add(v -> {
            try {
                surviveIntervalInput = Math.clamp(Integer.parseInt(v.trim()), 5, 600);
            } catch (NumberFormatException ignored) {
            }
        });
        fieldRow.item(intervalField);

        fieldRow.spacer();

        fieldRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.room_controller.button.set_time"), 64, 18,
            () -> ClientPlayNetworking.send(new RoomSetObjectivePayload(menu.getBlockPos(), editedObjective(objectiveType)))));
        return fieldRow;
    }

    /** Share of the online party that must touch this room's doors before it locks in (1-100%).
     *  A short field on purpose — the value never exceeds three digits. */
    private Flex buildEntryPercentRow() {
        Flex fieldRow = Flex.row().gap(6).alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        fieldRow.item(accentBar());

        fieldRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.objective.entry_percent"), INK_MID));

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(36, String.valueOf(entryPercentInput), 3);
        field.size(36, 18);
        field.changeListeners.add(v -> {
            try {
                entryPercentInput = Math.clamp(Integer.parseInt(v.trim()), 1, 100);
            } catch (NumberFormatException ignored) {
            }
        });
        fieldRow.item(field);

        fieldRow.spacer();

        fieldRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.room_controller.button.set_time"), 64, 18,
            () -> ClientPlayNetworking.send(new RoomSetObjectivePayload(menu.getBlockPos(), editedObjective(objectiveType)))));
        return fieldRow;
    }

    private void buildProtectRows(Flex container) {
        // Target mob row: id field with entity-type autocomplete + Apply.
        Flex fieldRow = Flex.row().gap(6).alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        fieldRow.item(accentBar());

        fieldRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.objective.protect_mob"), INK_MID));

        IdSuggestionDropdown.Field mobField = IdSuggestionDropdown.textBox(100, protectMobIdInput, 256);
        mobField.sizing(Sizing.expand(), Sizing.fixed(18));
        mobField.changeListeners.add(v -> protectMobIdInput = v);
        IdSuggestionDropdown.attachFullValueTooltip(mobField);
        fieldRow.item(mobField);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE, mobField);
        fieldRow.item(dropdown.chevron());

        fieldRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.room_controller.button.set_time"), 64, 18,
            () -> ClientPlayNetworking.send(new RoomSetObjectivePayload(menu.getBlockPos(), editedObjective(objectiveType)))));

        container.item(fieldRow);
        container.item(dropdown.panel());
        container.item(ArenasUi.spacer(2));

        // Target attributes (absolute values, e.g. generic.max_health = 60).
        container.item(ArenasUi.text(Component.translatable("gui.arenas_ld.attributes"), INK_DIM));
        container.item(ArenasUi.spacer(2));
        for (int i = 0; i < protectAttributesInput.size(); i++) {
            container.item(protectAttributeRow(i));
        }
        container.item(buildAddAttributeRow());
        container.item(ArenasUi.spacer(2));

        // Position + stationary row.
        Flex posRow = Flex.row().gap(8).alignItems(Align.CENTER);
        posRow.sizing(Sizing.fill(), Sizing.content());

        Optional<BlockPos> protectOffset = menu.getObjective().protectOffset();
        posRow.item(ArenasUi.text(protectOffset
            .map(offset -> (Component) Component.literal(menu.getBlockPos().offset(offset).toShortString()))
            .orElse(Component.translatable("gui.arenas_ld.room_controller.objective.protect_pos_none")),
            protectOffset.isPresent() ? INK : INK_DIM));

        posRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.objective.protect_pos_hint"), INK_DIM));

        posRow.spacer();

        posRow.item(ArenasUi.button(
            Component.translatable(protectStationaryInput
                ? "gui.arenas_ld.room_controller.objective.stationary_on"
                : "gui.arenas_ld.room_controller.objective.stationary_off"),
            () -> {
                protectStationaryInput = !protectStationaryInput;
                ClientPlayNetworking.send(new RoomSetObjectivePayload(menu.getBlockPos(), editedObjective(objectiveType)));
            }));

        Button clearPos = ArenasUi.button(Component.translatable("gui.arenas_ld.room_controller.button.clear_respawn"),
            () -> ClientPlayNetworking.send(new RoomClearProtectPosPayload(menu.getBlockPos())));
        clearPos.enabled(protectOffset.isPresent());
        posRow.item(clearPos);

        container.item(posRow);
    }

    private Flex protectAttributeRow(int index) {
        net.ledok.arenas_ld.util.AttributeData attr = protectAttributesInput.get(index);
        Flex row = Flex.row().gap(6).padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(20));
        row.backgroundFill(index % 2 == 0 ? ROW_BG : ROW_BG_ALT);

        row.item(ArenasUi.text(Component.literal(attr.id()), INK));
        row.item(ArenasUi.text(Component.literal("= " + formatAttrValue(attr.value())), INK_MID));

        row.spacer();

        int capturedIndex = index;
        row.item(ArenasUi.button(Component.literal("×"), 20, 16, () -> {
            protectAttributesInput.remove(capturedIndex);
            ClientPlayNetworking.send(new RoomSetObjectivePayload(menu.getBlockPos(), editedObjective(objectiveType)));
        }));
        return row;
    }

    private Flex buildAddAttributeRow() {
        Flex container = Flex.column();
        container.sizing(Sizing.fill(), Sizing.content());

        Flex fieldRow = Flex.row().gap(6).alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        fieldRow.item(accentBar());

        IdSuggestionDropdown.Field idField = IdSuggestionDropdown.textBox(100, newAttrIdInput, 256);
        idField.sizing(Sizing.expand(), Sizing.fixed(18));
        idField.changeListeners.add(v -> newAttrIdInput = v);
        fieldRow.item(idField);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(
            net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE, idField);
        fieldRow.item(dropdown.chevron());

        fieldRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.objective.attr_value"), INK_MID));

        IdSuggestionDropdown.Field valueField = IdSuggestionDropdown.textBox(56, newAttrValueInput, 12);
        valueField.size(56, 18);
        valueField.changeListeners.add(v -> newAttrValueInput = v);
        fieldRow.item(valueField);

        fieldRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.room_controller.reward.add_effect"),
            this::addProtectAttribute));

        container.item(fieldRow);
        container.item(dropdown.panel());
        return container;
    }

    private void addProtectAttribute() {
        String id = newAttrIdInput == null ? "" : newAttrIdInput.trim();
        double value;
        try {
            value = Double.parseDouble(newAttrValueInput.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return;
        }
        if (id.isEmpty() || !Double.isFinite(value)) {
            return;
        }
        // One entry per attribute id: re-adding replaces the old value.
        protectAttributesInput.removeIf(attr -> attr.id().equals(id));
        protectAttributesInput.add(new net.ledok.arenas_ld.util.AttributeData(id, value));
        newAttrIdInput = "";
        newAttrValueInput = "";
        ClientPlayNetworking.send(new RoomSetObjectivePayload(menu.getBlockPos(), editedObjective(objectiveType)));
    }

    private static String formatAttrValue(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value)
            ? String.valueOf((long) value)
            : String.valueOf(value);
    }

    private Flex spawnerRow(int index) {
        RoomControllerData.SpawnerEntry entry = spawners.get(index);
        Flex row = Flex.row().gap(6).padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(20));
        row.backgroundFill(index % 2 == 0 ? ROW_BG : ROW_BG_ALT);

        row.item(ArenasUi.text(Component.literal(entry.pos().toShortString()), entry.missing() ? INK_DIM : INK));

        if (entry.missing()) {
            row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.spawner_missing"), DANGER));
        } else {
            row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.wave_badge", entry.wave()), INK_MID));
            if (entry.isBoss()) {
                row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.boss_badge"), WARN));
            }
        }

        row.spacer();

        row.item(contentButton(Component.translatable("gui.arenas_ld.room_controller.button.remove"), 16,
            () -> ClientPlayNetworking.send(new RoomRemoveSpawnerPayload(menu.getBlockPos(), entry.pos()))));
        return row;
    }

    private Flex buildDoorRow() {
        Flex row = Flex.row().gap(8).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());

        Component doorValue = doorPositions.isEmpty()
            ? Component.translatable("gui.arenas_ld.room_controller.door_none")
            : (doorPositions.size() == 1
                ? Component.literal(doorPositions.get(0).toShortString())
                : Component.translatable("gui.arenas_ld.room_controller.door_count", doorPositions.size()));
        row.item(ArenasUi.text(doorValue, doorPositions.isEmpty() ? INK_DIM : INK));

        row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.door_hint"), INK_DIM));

        row.spacer();

        Button clearDoor = ArenasUi.button(Component.translatable("gui.arenas_ld.room_controller.button.clear_door"),
            () -> ClientPlayNetworking.send(new RoomClearDoorPayload(menu.getBlockPos())));
        clearDoor.enabled(!doorPositions.isEmpty());
        row.item(clearDoor);
        return row;
    }

    private Flex buildRespawnRow() {
        Flex row = Flex.row().gap(8).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());

        Component respawnValue = respawnPositions.isEmpty()
            ? Component.translatable("gui.arenas_ld.room_controller.respawn_none")
            : (respawnPositions.size() == 1
                ? Component.literal(respawnPositions.get(0).toShortString())
                : Component.translatable("gui.arenas_ld.room_controller.respawn_count", respawnPositions.size()));
        row.item(ArenasUi.text(respawnValue, respawnPositions.isEmpty() ? INK_DIM : INK));

        row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.respawn_hint"), INK_DIM));

        row.spacer();

        Button clear = ArenasUi.button(Component.translatable("gui.arenas_ld.room_controller.button.clear_respawn"),
            () -> ClientPlayNetworking.send(new RoomClearRespawnPayload(menu.getBlockPos())));
        clear.enabled(!respawnPositions.isEmpty());
        row.item(clear);
        return row;
    }

    private void openRewardsScreen() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.setScreen(new RoomRewardsScreen(menu, minecraft.player.getInventory(),
            Component.translatable("gui.arenas_ld.room_controller.rewards_title")));
    }

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(RoomControllerData data) {
        menu.applyData(data);
        syncState();
        rebuildUi();
    }

    private void syncState() {
        spawners.clear();
        spawners.addAll(menu.getSpawners());
        doorPositions = menu.getDoorPositions();
        respawnPositions = menu.getRespawnPositions();
        roomNameInput = menu.getRoomName();
        objectiveType = menu.getObjective().type();
        entryPercentInput = menu.getObjective().clampedEntryPercent();
        surviveSecondsInput = menu.getObjective().surviveSeconds();
        surviveIntervalInput = menu.getObjective().surviveWaveIntervalSeconds();
        protectMobIdInput = menu.getObjective().protectMobId();
        protectStationaryInput = menu.getObjective().protectStationary();
        protectAttributesInput.clear();
        protectAttributesInput.addAll(menu.getObjective().protectAttributes());
    }

    /** The objective as currently edited on this screen. The protect position is server-owned
     *  (set with the Configurator) and ignored by the handler, so it's sent empty. */
    private RoomObjectiveConfig editedObjective(RoomObjectiveConfig.Type type) {
        return new RoomObjectiveConfig(type, surviveSecondsInput, surviveIntervalInput,
            protectMobIdInput, protectStationaryInput, Optional.empty(), List.copyOf(protectAttributesInput),
            entryPercentInput);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Flex sectionLabel(Component text) {
        Flex row = Flex.row().alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(ArenasUi.text(text, INK_DIM));
        return row;
    }

    /** 2px accent stripe at the left edge of a boxed field row. */
    private static Flex accentBar() {
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        return accent;
    }

    /** Content-width button with a non-default height (the old {@code Sizing.content() x fixed(h)}). */
    private static Button contentButton(Component label, float height, Runnable onClick) {
        return ArenasUi.button(label, Minecraft.getInstance().font.width(label) + 12, height, onClick);
    }

    private Button dangerButton(Component text, Runnable onClick) {
        Button button = ArenasUi.button(text.copy().withStyle(net.minecraft.ChatFormatting.RED), onClick);
        button.style(DANGER_BUTTON);
        return button;
    }
}
