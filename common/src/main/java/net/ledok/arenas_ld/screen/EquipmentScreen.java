package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.vectorlib.client.canvas.ItemStackNode;
import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.canvas.widget.Widget;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT_DARK;
import static net.ledok.arenas_ld.screen.ArenasUi.GOOD;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE_HI;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;
import net.ledok.arenas_ld.platform.ArenasNetwork;

/**
 * VectorLib editor for a mob's equipment: six <em>ghost</em> item slots (drag/click an item to stamp a
 * template — your item isn't consumed) each with a 0–100% spawn chance, plus the natural-loot toggle.
 * Ignores the inventory ("E") key so it can't be closed mid-edit.
 *
 * <p>The menu's slots are never positioned or drawn by vanilla: every slot is a canvas
 * {@link SlotWidget} that renders the live stack and forwards clicks through
 * {@code gameMode.handleInventoryMouseClick}, so the ghost-stamp logic in
 * {@link EquipmentScreenHandler#clicked} runs on both sides exactly as before.
 */
public class EquipmentScreen extends FitCanvasHandledScreen<EquipmentScreenHandler> {
    private static final int SLOT_BG = 0xFF1D2530;
    private static final int SLOT_BG_HOVER = 0xFF2A3542;

    private static final String[] SLOT_KEYS = {
        "gui.arenas_ld.equipment.slot.head", "gui.arenas_ld.equipment.slot.chest",
        "gui.arenas_ld.equipment.slot.legs", "gui.arenas_ld.equipment.slot.feet",
        "gui.arenas_ld.equipment.slot.main_hand", "gui.arenas_ld.equipment.slot.off_hand"
    };

    private final int[] chances = new int[EquipmentData.SLOT_COUNT];
    private boolean dropChance = false;
    private boolean loaded = false;

    public EquipmentScreen(EquipmentScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(360, 300), Placement.Screen.center());
        canvas.theme(ArenasUi.THEME);
        fillWindow();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ArenasUi.swallowsInventoryKey(input, keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * ×, Esc, and Save all go back to the spawner screen that opened this editor. Equipment is a
     * real server menu that replaced that screen's menu, so "back" is a server round trip: the
     * server reopens the owning block's own menu and swaps the screens (or closes the container
     * if the block is gone).
     */
    @Override
    public void onClose() {
        ArenasNetwork.sendToServer(new ModPackets.OpenBlockMenuPayload(menu.blockEntity.getBlockPos()));
    }

    /** Never interpret an off-panel click as "throw the carried item out". */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return false;
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        buildAll();
    }

    private void buildAll() {
        if (!loaded) {
            loadFromHandler();
            loaded = true;
        }
        canvas.clear();
        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);

        float shellWidth = Math.max(300, Math.min(360, canvas.width() - 24));
        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(shellWidth), Sizing.content());
        shell.backgroundFill(PANEL, HAIRLINE_HI, 1);

        shell.item(buildHeader());

        Flex content = Flex.column().gap(4).padding(Insets.of(10));
        content.sizing(Sizing.fill(), Sizing.content());
        content.backgroundFill(PANEL);
        for (int i = 0; i < EquipmentData.SLOT_COUNT; i++) {
            content.item(slotRow(i));
        }
        content.item(ArenasUi.spacer(4));
        content.item(dropToggle());
        content.item(ArenasUi.spacer(6));
        content.item(inventoryGrid());
        shell.item(content);

        shell.item(buildFooter());
        root.layoutIn(canvas.width(), canvas.height());
    }

    private void loadFromHandler() {
        if (menu.equipmentProvider == null) return;
        EquipmentData data = menu.equipmentProvider.getEquipment();
        for (int i = 0; i < EquipmentData.SLOT_COUNT; i++) {
            chances[i] = data.chances[i];
        }
        dropChance = data.dropChance;
    }

    private Flex slotRow(int index) {
        Flex row = Flex.row().gap(8).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(22));

        row.item(new SlotWidget(index));

        var label = ArenasUi.label(74, Component.translatable(SLOT_KEYS[index]), INK);
        label.sizing(Sizing.fixed(74), Sizing.content());
        row.item(label);

        row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.equipment.chance"), INK_DIM));

        TextField field = ArenasUi.textField(46, Integer.toString(chances[index]), 8);
        field.size(46, 18);
        field.onChange(v -> {
            int parsed;
            try {
                parsed = Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return;
            }
            chances[index] = Math.max(0, Math.min(100, parsed));
        });
        row.item(field);
        row.item(ArenasUi.text(Component.literal("%"), INK_DIM));
        return row;
    }

    private Flex inventoryGrid() {
        Flex grid = Flex.column().gap(1).alignItems(Align.CENTER);
        grid.sizing(Sizing.content(), Sizing.content());
        // Main inventory: handler slots 6..32 (3 rows of 9).
        for (int r = 0; r < 3; r++) {
            Flex rowFlow = Flex.row().gap(1);
            rowFlow.sizing(Sizing.content(), Sizing.content());
            for (int c = 0; c < 9; c++) {
                rowFlow.item(new SlotWidget(EquipmentData.SLOT_COUNT + r * 9 + c));
            }
            grid.item(rowFlow);
        }
        grid.item(ArenasUi.spacer(3));
        // Hotbar: handler slots 33..41.
        Flex hotbar = Flex.row().gap(1);
        hotbar.sizing(Sizing.content(), Sizing.content());
        for (int c = 0; c < 9; c++) {
            hotbar.item(new SlotWidget(EquipmentData.SLOT_COUNT + 27 + c));
        }
        grid.item(hotbar);
        return grid;
    }

    private Flex buildHeader() {
        Flex header = Flex.row().gap(10).padding(Insets.of(8, 10, 8, 10)).alignItems(Align.CENTER);
        header.sizing(Sizing.fill(), Sizing.fixed(46));
        header.backgroundFill(PANEL_2);

        Flex mark = Flex.column();
        mark.sizing(Sizing.fixed(20), Sizing.fixed(20));
        mark.backgroundFill(ACCENT, ACCENT_DARK, 1);
        header.item(mark);

        header.item(ArenasUi.text(this.title, INK));
        header.spacer();
        header.item(ArenasUi.button(Component.literal("×"), 22, 18, this::onClose));
        return header;
    }

    private Flex buildFooter() {
        Flex footer = Flex.row().gap(6).padding(Insets.of(6)).alignItems(Align.CENTER);
        footer.sizing(Sizing.fill(), Sizing.fixed(34));
        footer.backgroundFill(PANEL_2);
        footer.spacer();
        footer.item(ArenasUi.button(Component.translatable("gui.arenas_ld.save"), 110, 18, this::onSave));
        return footer;
    }

    private void onSave() {
        ItemStack[] items = menu.currentItems();
        EquipmentData data = new EquipmentData(items, chances, dropChance);
        ArenasNetwork.sendToServer(new ModPackets.UpdateEquipmentPayload(menu.blockEntity.getBlockPos(), data));
        this.onClose();
    }

    private Flex dropToggle() {
        Flex row = Flex.row().gap(8).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.enable_drops"), INK_DIM));

        Button toggle = ArenasUi.button(dropLabel(), 60, 18, null);
        toggle.onClick(() -> {
            dropChance = !dropChance;
            toggle.label(dropLabel());
        });
        row.item(toggle);
        return row;
    }

    private Component dropLabel() {
        return dropChance
            ? Component.translatable("gui.arenas_ld.toggle_on").withStyle(s -> s.withColor(GOOD))
            : Component.translatable("gui.arenas_ld.toggle_off").withStyle(s -> s.withColor(INK_DIM));
    }

    // ---------------------------------------------------------------- carried stack on the cursor

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(font, carried, mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
        }
    }

    /**
     * An 18×18 bordered frame showing a menu slot's live stack; clicks go through the normal
     * container click path (ghost stamping happens in the handler on both sides).
     */
    private final class SlotWidget extends Widget {
        private final int slotIndex;
        private final ItemStackNode item;

        SlotWidget(int slotIndex) {
            super(18, 18);
            this.slotIndex = slotIndex;
            this.item = ItemStackNode.of(ItemStack.EMPTY, 1, 1);
        }

        @Override
        protected void rebuild() {
            add(Shapes.rect(0, 0, width(), height())
                    .fill(isHovered() ? SLOT_BG_HOVER : SLOT_BG)
                    .stroke(isHovered() ? HAIRLINE_HI : HAIRLINE, 1));
            add(item.stack(menu.getSlot(slotIndex).getItem()));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (minecraft != null && minecraft.gameMode != null && minecraft.player != null) {
                minecraft.gameMode.handleInventoryMouseClick(
                        menu.containerId, slotIndex, button, ClickType.PICKUP, minecraft.player);
                refresh();
            }
            return true;
        }

        @Override
        protected void draw(net.ledok.vectorlib.client.canvas.CanvasRenderContext ctx) {
            // The menu syncs stacks outside our control; keep the node current every frame.
            item.stack(menu.getSlot(slotIndex).getItem());
            super.draw(ctx);
        }
    }
}
