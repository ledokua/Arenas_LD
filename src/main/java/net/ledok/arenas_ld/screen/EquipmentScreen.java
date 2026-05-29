package net.ledok.arenas_ld.screen;

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
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.EquipmentData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * owo-lib editor for a mob's equipment (six item-id fields + a drop toggle).
 * Opened from the boss / mob spawner screens. Mirrors the dark spawner-screen
 * styling and, like the parent screens, ignores the inventory ("E") key so it
 * can't be closed mid-edit.
 */
public class EquipmentScreen extends BaseOwoHandledScreen<FlowLayout, EquipmentScreenHandler> {
    private static final int BG          = 0xFF070E14;
    private static final int PANEL       = 0xFF121922;
    private static final int PANEL_2     = 0xFF0C1218;
    private static final int HAIRLINE    = 0xFF283442;
    private static final int HAIRLINE_HI = 0xFF3A4A5C;
    private static final int INK         = 0xFFE8EEF5;
    private static final int INK_DIM     = 0xFF5F6E80;
    private static final int GOOD        = 0xFF86D36C;
    private static final int ACCENT      = 0xFFA98BE8;
    private static final int ACCENT_DARK = 0xFF6C4FB5;

    // Working model.
    private String head = "";
    private String chest = "";
    private String legs = "";
    private String feet = "";
    private String mainHand = "";
    private String offHand = "";
    private boolean dropChance = false;

    private FlowLayout contentArea;

    public EquipmentScreen(EquipmentScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.titleLabelY = 9999;
        this.inventoryLabelY = 9999;
    }

    /**
     * Swallow the inventory key so tapping E mid-edit doesn't close the editor.
     * Character input still reaches focused fields via charTyped; closing happens
     * via × or Escape.
     */
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
        loadFromHandler();

        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth  = Math.max(440, Math.min(560, this.width - 24));
        int shellHeight = Math.max(280, this.height - 24);
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellWidth), Sizing.fixed(shellHeight));
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));

        shell.child(buildHeader());

        contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        contentArea.surface(Surface.flat(PANEL));
        contentArea.padding(Insets.of(10));
        contentArea.gap(6);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), contentArea);
        scroll.surface(Surface.flat(PANEL));
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        scroll.scrollbarThiccness(8);
        scroll.fixedScrollbarLength(28);
        scroll.scrollStep(18);
        shell.child(scroll);

        shell.child(buildFooter());
        rootComponent.child(shell);

        buildContent();
    }

    private void loadFromHandler() {
        if (menu.equipmentProvider == null) return;
        EquipmentData data = menu.equipmentProvider.getEquipment();
        head = data.head;
        chest = data.chest;
        legs = data.legs;
        feet = data.feet;
        mainHand = data.mainHand;
        offHand = data.offHand;
        dropChance = data.dropChance;
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(46));
        header.surface(Surface.flat(PANEL_2));
        header.padding(Insets.of(8, 8, 10, 10));
        header.gap(10);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(20), Sizing.fixed(20));
        mark.surface(Surface.flat(ACCENT).and(Surface.outline(ACCENT_DARK)));
        header.child(mark);

        LabelComponent titleLabel = Components.label(this.title);
        titleLabel.color(Color.ofArgb(INK));
        header.child(titleLabel);

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
        footer.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);

        footer.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        ButtonComponent save = smallButton(Component.translatable("gui.arenas_ld.save"), b -> onSave());
        save.horizontalSizing(Sizing.fixed(110));
        footer.child(save);
        return footer;
    }

    private void buildContent() {
        if (contentArea == null) return;
        contentArea.clearChildren();

        contentArea.child(textField(Component.translatable("gui.arenas_ld.head_item_id"), head, v -> head = v));
        contentArea.child(textField(Component.translatable("gui.arenas_ld.chest_item_id"), chest, v -> chest = v));
        contentArea.child(textField(Component.translatable("gui.arenas_ld.legs_item_id"), legs, v -> legs = v));
        contentArea.child(textField(Component.translatable("gui.arenas_ld.feet_item_id"), feet, v -> feet = v));
        contentArea.child(textField(Component.translatable("gui.arenas_ld.main_hand_item_id"), mainHand, v -> mainHand = v));
        contentArea.child(textField(Component.translatable("gui.arenas_ld.off_hand_item_id"), offHand, v -> offHand = v));
        contentArea.child(spacer(4));
        contentArea.child(dropToggle());
    }

    private void onSave() {
        EquipmentData data = new EquipmentData(head, chest, legs, feet, mainHand, offHand, dropChance);
        ClientPlayNetworking.send(new ModPackets.UpdateEquipmentPayload(
            menu.blockEntity.getBlockPos(), data));
        this.onClose();
    }

    // ── UI helpers (shared styling with the spawner screens) ──────────────────

    private FlowLayout textField(Component caption, String initial, Consumer<String> onChange) {
        FlowLayout col = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        col.gap(4);

        col.child(text(caption, INK_DIM));

        FlowLayout fieldWrap = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldWrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldWrap.child(accent);

        TextBoxComponent field = Components.textBox(Sizing.expand(), initial == null ? "" : initial);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(onChange::accept);
        fieldWrap.child(field);

        col.child(fieldWrap);
        return col;
    }

    private FlowLayout dropToggle() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.child(text(Component.translatable("gui.arenas_ld.enable_drops"), INK_DIM));

        ButtonComponent toggle = smallButton(dropLabel(), b -> {
            dropChance = !dropChance;
            b.setMessage(dropLabel());
        });
        toggle.horizontalSizing(Sizing.fixed(60));
        row.child(toggle);
        return row;
    }

    private Component dropLabel() {
        return dropChance
            ? Component.translatable("gui.arenas_ld.toggle_on").withStyle(s -> s.withColor(GOOD))
            : Component.translatable("gui.arenas_ld.toggle_off").withStyle(s -> s.withColor(INK_DIM));
    }

    private FlowLayout spacer(int px) {
        FlowLayout spacer = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(px));
        spacer.surface(Surface.BLANK);
        return spacer;
    }

    private LabelComponent text(Component component, int color) {
        LabelComponent label = Components.label(component);
        label.color(Color.ofArgb(color));
        return label;
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
}
