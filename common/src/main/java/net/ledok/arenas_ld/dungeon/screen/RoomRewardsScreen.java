package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.RoomSetRewardPayload;
import net.ledok.arenas_ld.dungeon.room.RoomEffectData;
import net.ledok.arenas_ld.dungeon.room.RoomRewardConfig;
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
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.ledok.vectorlib.client.presentation.CanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.BG;
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

/**
 * Room clear reward editor, opened from the Room Controller screen's "Rewards" button.
 * Shares the parent's menu instance, so it edits the same server-synced snapshot.
 */
public class RoomRewardsScreen extends CanvasHandledScreen<RoomControllerScreenHandler> {

    /** Stepper − / + buttons (old owo renderer: panel fill, hairline border, row highlight on hover). */
    private static final WidgetStyle STEP_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, 0xFFFFFFFF, INK_DIM, INK_DIM, HAIRLINE, 0x80A98BE8,
            18, 16, 4, true);

    private String lootTableInput = "";
    private long currencyValue = 0L;
    private int xpValue = 0;
    private final List<RoomEffectData> effectsEdit = new ArrayList<>();
    private final List<String> commandsEdit = new ArrayList<>();
    private int newEffectDuration = 30;
    private int newEffectLevel = 1;

    private Flex contentArea;
    private Flex footerActions;
    private Label footerLabel;
    private IdSuggestionDropdown.Field newEffectIdField;
    private TextField effectDurationField;
    private TextField effectLevelField;
    private IdSuggestionDropdown.Field newCommandField;

    public RoomRewardsScreen(RoomControllerScreenHandler handler, Inventory inventory, Component title) {
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
        syncState();

        canvas.clear();
        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);
        root.backgroundFill(BG);

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

        header.item(ArenasUi.button(Component.literal("←"), 22, 18, this::returnToRoomScreen));

        Flex mark = Flex.column();
        mark.sizing(Sizing.fixed(18), Sizing.fixed(18));
        mark.backgroundFill(WARN);
        header.item(mark);

        Flex info = Flex.column().gap(3);
        info.sizing(Sizing.content(), Sizing.content());
        info.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.rewards_title"), INK));
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

        contentArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.reward_hint"), INK_DIM));
        contentArea.item(ArenasUi.spacer(6));

        contentArea.item(buildLootTableRow());
        contentArea.item(ArenasUi.spacer(4));
        contentArea.item(buildNumberRow(
            Component.translatable("gui.arenas_ld.room_controller.reward.currency"),
            String.valueOf(currencyValue),
            value -> {
                try {
                    currencyValue = Math.max(0L, Long.parseLong(value.trim()));
                } catch (NumberFormatException ignored) {
                }
            }));
        contentArea.item(buildNumberRow(
            Component.translatable("gui.arenas_ld.room_controller.reward.skill_xp"),
            String.valueOf(xpValue),
            value -> {
                try {
                    xpValue = Math.max(0, Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {
                }
            }));
        contentArea.item(ArenasUi.spacer(8));

        contentArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.reward.effects"), INK_MID));
        contentArea.item(ArenasUi.spacer(2));
        for (int i = 0; i < effectsEdit.size(); i++) {
            if (i > 0) contentArea.item(ArenasUi.hairline());
            contentArea.item(effectRow(i));
        }
        contentArea.item(ArenasUi.spacer(2));
        contentArea.item(buildAddEffectRow());
        contentArea.item(ArenasUi.spacer(8));

        contentArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.reward.commands"), INK_MID));
        contentArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.reward.command_hint"), INK_DIM));
        contentArea.item(ArenasUi.spacer(2));
        for (int i = 0; i < commandsEdit.size(); i++) {
            if (i > 0) contentArea.item(ArenasUi.hairline());
            contentArea.item(commandRow(i));
        }
        contentArea.item(ArenasUi.spacer(2));
        contentArea.item(buildAddCommandRow());

        footerActions.item(ArenasUi.button(
            Component.translatable("gui.arenas_ld.room_controller.reward.apply"), this::sendApplyRewards));
    }

    private Flex buildLootTableRow() {
        Flex container = Flex.column();
        container.sizing(Sizing.fill(), Sizing.content());

        container.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.reward.loot_table"), INK_MID));
        container.item(ArenasUi.spacer(2));

        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        fieldRow.item(accentBar());

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(100, lootTableInput, 256);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.changeListeners.add(v -> lootTableInput = v);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(
            () -> menu.getKnownLootTableIds(), field);
        fieldRow.item(field);
        fieldRow.item(dropdown.chevron());

        container.item(fieldRow);
        container.item(dropdown.panel());
        return container;
    }

    private Flex buildNumberRow(Component caption, String initial, Consumer<String> onChanged) {
        Flex row = Flex.row().gap(8).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());

        row.item(ArenasUi.text(caption, INK_MID));
        row.spacer();

        TextField field = ArenasUi.textField(70, initial, 32);
        field.size(70, 16);
        field.onChange(onChanged);
        row.item(field);
        return row;
    }

    private Flex effectRow(int index) {
        RoomEffectData effect = effectsEdit.get(index);
        Flex row = Flex.row().gap(6).padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(20));
        row.backgroundFill(index % 2 == 0 ? ROW_BG : ROW_BG_ALT);

        row.item(ArenasUi.text(Component.literal(effect.effectId()), INK));
        row.item(ArenasUi.text(Component.literal(effect.durationSeconds() + "s · Lv " + (effect.amplifier() + 1)), INK_MID));

        row.spacer();

        int capturedIndex = index;
        row.item(ArenasUi.button(Component.literal("×"), 20, 16, () -> {
            effectsEdit.remove(capturedIndex);
            rebuildUi();
        }));
        return row;
    }

    private Flex buildAddEffectRow() {
        Flex container = Flex.column();
        container.sizing(Sizing.fill(), Sizing.content());

        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        fieldRow.item(accentBar());

        newEffectIdField = IdSuggestionDropdown.textBox(100, "", 256);
        newEffectIdField.sizing(Sizing.expand(), Sizing.fixed(18));
        fieldRow.item(newEffectIdField);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(
            BuiltInRegistries.MOB_EFFECT, newEffectIdField);
        fieldRow.item(dropdown.chevron());
        container.item(fieldRow);
        container.item(dropdown.panel());
        container.item(ArenasUi.spacer(2));

        Flex paramsRow = Flex.row().gap(8).alignItems(Align.CENTER);
        paramsRow.sizing(Sizing.fill(), Sizing.content());

        paramsRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.reward.effect_duration"), INK_DIM));
        paramsRow.item(buildStepper(
            String.valueOf(newEffectDuration),
            field -> effectDurationField = field,
            delta -> {
                newEffectDuration = Math.max(1, Math.min(3600, newEffectDuration + delta * 5));
                effectDurationField.text(String.valueOf(newEffectDuration));
            },
            value -> {
                try {
                    newEffectDuration = Math.max(1, Math.min(3600, Integer.parseInt(value.trim())));
                } catch (NumberFormatException ignored) {
                }
            }));

        paramsRow.item(ArenasUi.text(Component.translatable("gui.arenas_ld.room_controller.reward.effect_level"), INK_DIM));
        paramsRow.item(buildStepper(
            String.valueOf(newEffectLevel),
            field -> effectLevelField = field,
            delta -> {
                newEffectLevel = Math.max(1, Math.min(10, newEffectLevel + delta));
                effectLevelField.text(String.valueOf(newEffectLevel));
            },
            value -> {
                try {
                    newEffectLevel = Math.max(1, Math.min(10, Integer.parseInt(value.trim())));
                } catch (NumberFormatException ignored) {
                }
            }));

        paramsRow.spacer();

        paramsRow.item(contentButton(Component.translatable("gui.arenas_ld.room_controller.reward.add_effect"), 16,
            this::addEffect));

        container.item(paramsRow);
        return container;
    }

    private Flex commandRow(int index) {
        String command = commandsEdit.get(index);
        Flex row = Flex.row().gap(6).padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(20));
        row.backgroundFill(index % 2 == 0 ? ROW_BG : ROW_BG_ALT);

        Label text = ArenasUi.label(100, Component.literal(command), INK);
        text.sizing(Sizing.expand(), Sizing.content());
        text.pickable(true);
        text.tooltip(Component.literal(command));
        row.item(text);

        int capturedIndex = index;
        row.item(ArenasUi.button(Component.literal("×"), 20, 16, () -> {
            commandsEdit.remove(capturedIndex);
            rebuildUi();
        }));
        return row;
    }

    private Flex buildAddCommandRow() {
        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        fieldRow.item(accentBar());

        newCommandField = IdSuggestionDropdown.textBox(100, "", 256);
        newCommandField.sizing(Sizing.expand(), Sizing.fixed(18));
        IdSuggestionDropdown.attachFullValueTooltip(newCommandField);
        fieldRow.item(newCommandField);

        fieldRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.room_controller.reward.add_effect"), 64, 18,
            this::addCommand));
        return fieldRow;
    }

    private void addCommand() {
        String raw = newCommandField == null ? "" : newCommandField.text().trim();
        if (raw.isEmpty()) {
            return;
        }
        commandsEdit.add(raw.startsWith("/") ? raw.substring(1) : raw);
        rebuildUi();
    }

    private void addEffect() {
        String raw = newEffectIdField == null ? "" : newEffectIdField.text().trim();
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (raw.isEmpty() || id == null
            || BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, id)).isEmpty()) {
            if (footerLabel != null) {
                footerLabel.text(Component.translatable("gui.arenas_ld.room_controller.reward.invalid_effect"));
            }
            return;
        }
        if (footerLabel != null) {
            footerLabel.text(Component.empty());
        }
        effectsEdit.add(new RoomEffectData(id.toString(), newEffectDuration, newEffectLevel - 1));
        rebuildUi();
    }

    private void sendApplyRewards() {
        ClientPlayNetworking.send(new RoomSetRewardPayload(menu.getBlockPos(), new RoomRewardConfig(
            lootTableInput == null ? "" : lootTableInput.trim(),
            List.copyOf(effectsEdit),
            currencyValue,
            xpValue,
            List.copyOf(commandsEdit)
        )));
    }

    private void returnToRoomScreen() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.setScreen(new RoomControllerScreen(menu, minecraft.player.getInventory(),
            Component.translatable("gui.arenas_ld.room_controller.title")));
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
        RoomRewardConfig reward = menu.getRoomReward();
        lootTableInput = reward.lootTableId();
        currencyValue = reward.currency();
        xpValue = reward.skillXp();
        effectsEdit.clear();
        effectsEdit.addAll(reward.effects());
        commandsEdit.clear();
        commandsEdit.addAll(reward.commands());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Flex buildStepper(String initial, Consumer<TextField> fieldSink,
                              java.util.function.IntConsumer step, Consumer<String> onChanged) {
        Flex stepper = Flex.row().gap(2).alignItems(Align.CENTER).padding(Insets.of(2, 4, 4, 2));
        stepper.sizing(Sizing.content(), Sizing.content());
        stepper.backgroundFill(PANEL_2, HAIRLINE, 1);

        stepper.item(stepBtn("−", () -> step.accept(-1)));

        TextField field = ArenasUi.textField(34, initial, 32);
        field.size(34, 16);
        field.onChange(onChanged);
        fieldSink.accept(field);
        stepper.item(field);

        stepper.item(stepBtn("+", () -> step.accept(1)));
        return stepper;
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

    private Button stepBtn(String glyph, Runnable onClick) {
        Button button = ArenasUi.button(Component.literal(glyph), 16, 16, onClick);
        button.style(STEP_STYLE);
        return button;
    }
}
