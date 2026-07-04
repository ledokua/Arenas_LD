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
import net.ledok.arenas_ld.dungeon.packet.RoomSetRewardPayload;
import net.ledok.arenas_ld.dungeon.room.RoomEffectData;
import net.ledok.arenas_ld.dungeon.room.RoomRewardConfig;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Room clear reward editor, opened from the Room Controller screen's "Rewards" button.
 * Shares the parent's menu instance, so it edits the same server-synced snapshot.
 */
public class RoomRewardsScreen extends BaseOwoHandledScreen<FlowLayout, RoomControllerScreenHandler> {

    private static final int BG          = 0xFF070E14;
    private static final int PANEL       = 0xFF121922;
    private static final int PANEL_2     = 0xFF0C1218;
    private static final int HAIRLINE    = 0xFF283442;
    private static final int HAIRLINE_HI = 0xFF3A4A5C;
    private static final int ROW_BG      = 0xFF19222D;
    private static final int ROW_BG_ALT  = 0xFF16202A;
    private static final int INK         = 0xFFE8EEF5;
    private static final int INK_MID     = 0xFF9AA8B8;
    private static final int INK_DIM     = 0xFF5F6E80;
    private static final int WARN        = 0xFFF5B042;
    private static final int ACCENT      = 0xFFA98BE8;
    private static final int ACCENT_DARK = 0xFF6C4FB5;
    private static final int DANGER      = 0xFFE8624A;

    private String lootTableInput = "";
    private long currencyValue = 0L;
    private int xpValue = 0;
    private final List<RoomEffectData> effectsEdit = new ArrayList<>();
    private final List<String> commandsEdit = new ArrayList<>();
    private int newEffectDuration = 30;
    private int newEffectLevel = 1;

    private FlowLayout contentArea;
    private FlowLayout footerActions;
    private LabelComponent footerLabel;
    private TextBoxComponent newEffectIdField;
    private TextBoxComponent effectDurationField;
    private TextBoxComponent effectLevelField;
    private TextBoxComponent newCommandField;

    public RoomRewardsScreen(RoomControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.inventoryLabelY = 9999;
        this.titleLabelY = 9999;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        syncState();

        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth  = Math.max(440, Math.min(560, this.width - 24));
        int shellHeight = Math.max(300, this.height - 24);
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellWidth), Sizing.fixed(shellHeight));
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));

        shell.child(buildHeader());

        contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        contentArea.surface(Surface.flat(PANEL));
        contentArea.padding(Insets.of(10));
        contentArea.gap(4);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), contentArea);
        scroll.surface(Surface.flat(PANEL));
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        scroll.scrollbarThiccness(8);
        scroll.fixedScrollbarLength(28);
        scroll.scrollStep(18);
        shell.child(scroll);

        shell.child(buildFooter());
        rootComponent.child(shell);

        rebuildUi();
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(42));
        header.surface(Surface.flat(PANEL_2));
        header.padding(Insets.of(6));
        header.gap(8);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        ButtonComponent back = smallButton(Component.literal("←"), b -> returnToRoomScreen());
        back.sizing(Sizing.fixed(22), Sizing.fixed(18));
        header.child(back);

        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(18), Sizing.fixed(18));
        mark.surface(Surface.flat(WARN));
        header.child(mark);

        FlowLayout info = Containers.verticalFlow(Sizing.content(), Sizing.content());
        info.gap(3);
        LabelComponent titleLabel = Components.label(Component.translatable("gui.arenas_ld.room_controller.rewards_title"));
        titleLabel.color(Color.ofArgb(INK));
        info.child(titleLabel);
        LabelComponent posLabel = Components.label(Component.literal(
            "POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()));
        posLabel.color(Color.ofArgb(INK_DIM));
        info.child(posLabel);
        header.child(info);

        header.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

        ButtonComponent close = smallButton(Component.literal("×"), b -> onClose());
        close.sizing(Sizing.fixed(22), Sizing.fixed(18));
        header.child(close);
        return header;
    }

    private FlowLayout buildFooter() {
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(34));
        footer.surface(Surface.flat(PANEL_2));
        footer.padding(Insets.of(6));
        footer.gap(6);

        footerLabel = Components.label(Component.empty());
        footerLabel.color(Color.ofArgb(DANGER));
        footerLabel.horizontalSizing(Sizing.expand());
        footer.child(footerLabel);

        footerActions = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        footerActions.gap(4);
        footer.child(footerActions);
        return footer;
    }

    private void rebuildUi() {
        if (contentArea == null) return;
        contentArea.clearChildren();
        footerActions.clearChildren();

        LabelComponent rewardHint = Components.label(Component.translatable("gui.arenas_ld.room_controller.reward_hint"));
        rewardHint.color(Color.ofArgb(INK_DIM));
        contentArea.child(rewardHint);
        contentArea.child(spacer(6));

        contentArea.child(buildLootTableRow());
        contentArea.child(spacer(4));
        contentArea.child(buildNumberRow(
            Component.translatable("gui.arenas_ld.room_controller.reward.currency"),
            String.valueOf(currencyValue),
            value -> {
                try {
                    currencyValue = Math.max(0L, Long.parseLong(value.trim()));
                } catch (NumberFormatException ignored) {
                }
            }));
        contentArea.child(buildNumberRow(
            Component.translatable("gui.arenas_ld.room_controller.reward.skill_xp"),
            String.valueOf(xpValue),
            value -> {
                try {
                    xpValue = Math.max(0, Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {
                }
            }));
        contentArea.child(spacer(8));

        LabelComponent effectsCaption = Components.label(Component.translatable("gui.arenas_ld.room_controller.reward.effects"));
        effectsCaption.color(Color.ofArgb(INK_MID));
        contentArea.child(effectsCaption);
        contentArea.child(spacer(2));
        for (int i = 0; i < effectsEdit.size(); i++) {
            if (i > 0) contentArea.child(rowDivider());
            contentArea.child(effectRow(i));
        }
        contentArea.child(spacer(2));
        contentArea.child(buildAddEffectRow());
        contentArea.child(spacer(8));

        LabelComponent commandsCaption = Components.label(Component.translatable("gui.arenas_ld.room_controller.reward.commands"));
        commandsCaption.color(Color.ofArgb(INK_MID));
        contentArea.child(commandsCaption);
        LabelComponent commandHint = Components.label(Component.translatable("gui.arenas_ld.room_controller.reward.command_hint"));
        commandHint.color(Color.ofArgb(INK_DIM));
        contentArea.child(commandHint);
        contentArea.child(spacer(2));
        for (int i = 0; i < commandsEdit.size(); i++) {
            if (i > 0) contentArea.child(rowDivider());
            contentArea.child(commandRow(i));
        }
        contentArea.child(spacer(2));
        contentArea.child(buildAddCommandRow());

        ButtonComponent applyRewards = accentButton(
            Component.translatable("gui.arenas_ld.room_controller.reward.apply"), b -> sendApplyRewards());
        applyRewards.sizing(Sizing.content(), Sizing.fixed(18));
        footerActions.child(applyRewards);
    }

    private FlowLayout buildLootTableRow() {
        FlowLayout container = Containers.verticalFlow(Sizing.fill(100), Sizing.content());

        LabelComponent caption = Components.label(Component.translatable("gui.arenas_ld.room_controller.reward.loot_table"));
        caption.color(Color.ofArgb(INK_MID));
        container.child(caption);
        container.child(spacer(2));

        FlowLayout fieldRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldRow.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldRow.child(accent);

        TextBoxComponent field = IdSuggestionDropdown.textBox(Sizing.expand(), lootTableInput, 256);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(v -> lootTableInput = v);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(
            this.font, () -> menu.getKnownLootTableIds(), field);
        fieldRow.child(field);
        fieldRow.child(dropdown.chevron());

        container.child(fieldRow);
        container.child(dropdown.panel());
        return container;
    }

    private FlowLayout buildNumberRow(Component caption, String initial, Consumer<String> onChanged) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent label = Components.label(caption);
        label.color(Color.ofArgb(INK_MID));
        row.child(label);
        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));

        TextBoxComponent field = Components.textBox(Sizing.fixed(70), initial);
        field.verticalSizing(Sizing.fixed(16));
        field.onChanged().subscribe(onChanged::accept);
        row.child(field);
        return row;
    }

    private FlowLayout effectRow(int index) {
        RoomEffectData effect = effectsEdit.get(index);
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.surface(Surface.flat(index % 2 == 0 ? ROW_BG : ROW_BG_ALT));
        row.padding(Insets.of(0, 0, 6, 6));
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent id = Components.label(Component.literal(effect.effectId()));
        id.color(Color.ofArgb(INK));
        row.child(id);

        LabelComponent meta = Components.label(Component.literal(effect.durationSeconds() + "s · Lv " + (effect.amplifier() + 1)));
        meta.color(Color.ofArgb(INK_MID));
        row.child(meta);

        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));

        int capturedIndex = index;
        ButtonComponent remove = smallButton(Component.literal("×"), b -> {
            effectsEdit.remove(capturedIndex);
            rebuildUi();
        });
        remove.sizing(Sizing.fixed(20), Sizing.fixed(16));
        row.child(remove);
        return row;
    }

    private FlowLayout buildAddEffectRow() {
        FlowLayout container = Containers.verticalFlow(Sizing.fill(100), Sizing.content());

        FlowLayout fieldRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldRow.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldRow.child(accent);

        newEffectIdField = Components.textBox(Sizing.expand(), "");
        newEffectIdField.verticalSizing(Sizing.fixed(18));
        fieldRow.child(newEffectIdField);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(
            this.font, BuiltInRegistries.MOB_EFFECT, newEffectIdField);
        fieldRow.child(dropdown.chevron());
        container.child(fieldRow);
        container.child(dropdown.panel());
        container.child(spacer(2));

        FlowLayout paramsRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        paramsRow.gap(8);
        paramsRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent durationCaption = Components.label(Component.translatable("gui.arenas_ld.room_controller.reward.effect_duration"));
        durationCaption.color(Color.ofArgb(INK_DIM));
        paramsRow.child(durationCaption);
        paramsRow.child(buildStepper(
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

        LabelComponent levelCaption = Components.label(Component.translatable("gui.arenas_ld.room_controller.reward.effect_level"));
        levelCaption.color(Color.ofArgb(INK_DIM));
        paramsRow.child(levelCaption);
        paramsRow.child(buildStepper(
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

        paramsRow.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));

        ButtonComponent addBtn = accentButton(Component.translatable("gui.arenas_ld.room_controller.reward.add_effect"),
            b -> addEffect());
        addBtn.sizing(Sizing.content(), Sizing.fixed(16));
        paramsRow.child(addBtn);

        container.child(paramsRow);
        return container;
    }

    private FlowLayout commandRow(int index) {
        String command = commandsEdit.get(index);
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.surface(Surface.flat(index % 2 == 0 ? ROW_BG : ROW_BG_ALT));
        row.padding(Insets.of(0, 0, 6, 6));
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent text = Components.label(Component.literal(command));
        text.color(Color.ofArgb(INK));
        text.tooltip(Component.literal(command));
        text.horizontalSizing(Sizing.expand());
        row.child(text);

        int capturedIndex = index;
        ButtonComponent remove = smallButton(Component.literal("×"), b -> {
            commandsEdit.remove(capturedIndex);
            rebuildUi();
        });
        remove.sizing(Sizing.fixed(20), Sizing.fixed(16));
        row.child(remove);
        return row;
    }

    private FlowLayout buildAddCommandRow() {
        FlowLayout fieldRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldRow.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldRow.child(accent);

        newCommandField = Components.textBox(Sizing.expand(), "");
        newCommandField.verticalSizing(Sizing.fixed(18));
        newCommandField.setMaxLength(256);
        IdSuggestionDropdown.attachFullValueTooltip(newCommandField);
        fieldRow.child(newCommandField);

        ButtonComponent addBtn = accentButton(Component.translatable("gui.arenas_ld.room_controller.reward.add_effect"),
            b -> addCommand());
        addBtn.sizing(Sizing.fixed(64), Sizing.fixed(18));
        fieldRow.child(addBtn);
        return fieldRow;
    }

    private void addCommand() {
        String raw = newCommandField == null ? "" : newCommandField.getValue().trim();
        if (raw.isEmpty()) {
            return;
        }
        commandsEdit.add(raw.startsWith("/") ? raw.substring(1) : raw);
        rebuildUi();
    }

    private void addEffect() {
        String raw = newEffectIdField == null ? "" : newEffectIdField.getValue().trim();
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

    private FlowLayout buildStepper(String initial, Consumer<TextBoxComponent> fieldSink,
                                    java.util.function.IntConsumer step, Consumer<String> onChanged) {
        FlowLayout stepper = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        stepper.gap(2);
        stepper.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        stepper.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        stepper.padding(Insets.of(2, 4, 2, 4));

        ButtonComponent minus = stepBtn("−", b -> step.accept(-1));
        TextBoxComponent field = Components.textBox(Sizing.fixed(34), initial);
        field.verticalSizing(Sizing.fixed(16));
        field.onChanged().subscribe(onChanged::accept);
        fieldSink.accept(field);
        ButtonComponent plus = stepBtn("+", b -> step.accept(1));

        stepper.child(minus);
        stepper.child(field);
        stepper.child(plus);
        return stepper;
    }

    private FlowLayout spacer(int px) {
        FlowLayout s = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(px));
        s.surface(Surface.BLANK);
        return s;
    }

    private FlowLayout rowDivider() {
        FlowLayout d = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        d.surface(Surface.flat(HAIRLINE));
        return d;
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
}
