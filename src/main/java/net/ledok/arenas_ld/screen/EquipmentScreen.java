package net.ledok.arenas_ld.screen;

import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * owo-lib editor for a mob's equipment: six <em>ghost</em> item slots (drag/click an item to stamp a
 * template — your item isn't consumed) each with a 0–100% spawn chance, plus the natural-loot toggle.
 * Ignores the inventory ("E") key so it can't be closed mid-edit.
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
    private static final int SLOT_BG     = 0xFF1D2530;

    private static final String[] SLOT_KEYS = {
        "gui.arenas_ld.equipment.slot.head", "gui.arenas_ld.equipment.slot.chest",
        "gui.arenas_ld.equipment.slot.legs", "gui.arenas_ld.equipment.slot.feet",
        "gui.arenas_ld.equipment.slot.main_hand", "gui.arenas_ld.equipment.slot.off_hand"
    };

    private final int[] chances = new int[EquipmentData.SLOT_COUNT];
    private boolean dropChance = false;

    public EquipmentScreen(EquipmentScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.titleLabelY = 9999;
        this.inventoryLabelY = 9999;
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
        loadFromHandler();

        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth = Math.max(300, Math.min(360, this.width - 24));
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellWidth), Sizing.content());
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));

        shell.child(buildHeader());

        FlowLayout content = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.surface(Surface.flat(PANEL));
        content.padding(Insets.of(10));
        content.gap(4);
        for (int i = 0; i < EquipmentData.SLOT_COUNT; i++) {
            content.child(slotRow(i));
        }
        content.child(spacer(4));
        content.child(dropToggle());
        content.child(spacer(6));
        content.child(inventoryGrid());
        shell.child(content);

        shell.child(buildFooter());
        rootComponent.child(shell);
    }

    private void loadFromHandler() {
        if (menu.equipmentProvider == null) return;
        EquipmentData data = menu.equipmentProvider.getEquipment();
        for (int i = 0; i < EquipmentData.SLOT_COUNT; i++) {
            chances[i] = data.chances[i];
        }
        dropChance = data.dropChance;
    }

    private FlowLayout slotRow(int index) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        row.gap(8);
        row.verticalAlignment(VerticalAlignment.CENTER);

        row.child(framedSlot(index));

        LabelComponent label = text(Component.translatable(SLOT_KEYS[index]), INK);
        label.horizontalSizing(Sizing.fixed(74));
        row.child(label);

        row.child(text(Component.translatable("gui.arenas_ld.equipment.chance"), INK_DIM));

        FlowLayout fieldWrap = Containers.horizontalFlow(Sizing.fixed(46), Sizing.fixed(18));
        fieldWrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldWrap.verticalAlignment(VerticalAlignment.CENTER);
        TextBoxComponent field = Components.textBox(Sizing.fill(100), Integer.toString(chances[index]));
        field.verticalSizing(Sizing.fixed(16));
        field.onChanged().subscribe(v -> {
            int parsed;
            try {
                parsed = Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return;
            }
            chances[index] = Math.max(0, Math.min(100, parsed));
        });
        fieldWrap.child(field);
        row.child(fieldWrap);
        row.child(text(Component.literal("%"), INK_DIM));
        return row;
    }

    /** A bordered 18×18 frame around a menu slot so empty slots are visible drop targets. */
    private FlowLayout framedSlot(int index) {
        FlowLayout frame = Containers.horizontalFlow(Sizing.fixed(18), Sizing.fixed(18));
        frame.surface(Surface.flat(SLOT_BG).and(Surface.outline(HAIRLINE)));
        frame.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        frame.child(this.slotAsComponent(index));
        return frame;
    }

    private FlowLayout inventoryGrid() {
        FlowLayout grid = Containers.verticalFlow(Sizing.content(), Sizing.content());
        grid.gap(1);
        grid.horizontalAlignment(HorizontalAlignment.CENTER);
        // Main inventory: handler slots 6..32 (3 rows of 9).
        for (int r = 0; r < 3; r++) {
            FlowLayout rowFlow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
            rowFlow.gap(1);
            for (int c = 0; c < 9; c++) {
                rowFlow.child(framedSlot(EquipmentData.SLOT_COUNT + r * 9 + c));
            }
            grid.child(rowFlow);
        }
        grid.child(spacer(3));
        // Hotbar: handler slots 33..41.
        FlowLayout hotbar = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        hotbar.gap(1);
        for (int c = 0; c < 9; c++) {
            hotbar.child(framedSlot(EquipmentData.SLOT_COUNT + 27 + c));
        }
        grid.child(hotbar);
        return grid;
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

    private void onSave() {
        ItemStack[] items = menu.currentItems();
        EquipmentData data = new EquipmentData(items, chances, dropChance);
        ClientPlayNetworking.send(new ModPackets.UpdateEquipmentPayload(menu.blockEntity.getBlockPos(), data));
        this.onClose();
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
