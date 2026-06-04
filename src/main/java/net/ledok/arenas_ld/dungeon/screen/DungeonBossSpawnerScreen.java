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
import net.ledok.arenas_ld.dungeon.packet.DbsClearRoomsPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsMoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsRemoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateDbsEntityDefPayload;
import net.ledok.arenas_ld.screen.EquipmentScreen;
import net.ledok.arenas_ld.screen.EquipmentScreenData;
import net.ledok.arenas_ld.screen.EquipmentScreenHandler;
import net.ledok.arenas_ld.screen.MobAttributesData;
import net.ledok.arenas_ld.screen.MobAttributesScreen;
import net.ledok.arenas_ld.screen.MobAttributesScreenHandler;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class DungeonBossSpawnerScreen extends BaseOwoHandledScreen<FlowLayout, DungeonBossSpawnerScreenHandler> {

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
    private static final int DROPDOWN_MAX_CANDIDATES = 50;
    private static final int DROPDOWN_MAX_HEIGHT = 140;

    private String mobIdValue = "";
    private boolean mobIdDropdownOpen = false;
    private FlowLayout mobIdDropdownPanel;
    private TextBoxComponent mobIdTextField;
    private final List<BlockPos> rooms = new ArrayList<>();

    private FlowLayout contentArea;
    private FlowLayout footerActions;
    private LabelComponent footerLabel;

    public DungeonBossSpawnerScreen(DungeonBossSpawnerScreenHandler handler, Inventory inventory, Component title) {
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

        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(18), Sizing.fixed(18));
        mark.surface(Surface.flat(WARN));
        header.child(mark);

        FlowLayout info = Containers.verticalFlow(Sizing.content(), Sizing.content());
        info.gap(3);
        LabelComponent titleLabel = Components.label(Component.translatable("gui.arenas_ld.boss_spawner"));
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
        mobIdDropdownOpen = false;
        mobIdDropdownPanel = null;
        mobIdTextField = null;

        contentArea.clearChildren();
        footerActions.clearChildren();

        // General section
        contentArea.child(sectionLabel(Component.translatable("gui.arenas_ld.tab_general")));
        contentArea.child(spacer(2));
        contentArea.child(buildMobIdSection());
        contentArea.child(spacer(6));

        LabelComponent entranceHint = Components.label(Component.translatable("gui.arenas_ld.dbs.entrance_hint"));
        entranceHint.color(Color.ofArgb(INK_DIM));
        contentArea.child(entranceHint);
        contentArea.child(spacer(6));

        FlowLayout actionRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actionRow.gap(6);
        actionRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        ButtonComponent attrsBtn = smallButton(Component.translatable("gui.arenas_ld.attributes"), b -> openAttributesScreen());
        attrsBtn.horizontalSizing(Sizing.fixed(110));
        actionRow.child(attrsBtn);

        ButtonComponent equipBtn = smallButton(Component.translatable("gui.arenas_ld.equipment"), b -> openEquipmentScreen());
        equipBtn.horizontalSizing(Sizing.fixed(110));
        actionRow.child(equipBtn);
        contentArea.child(actionRow);

        contentArea.child(spacer(10));

        // Rooms section
        contentArea.child(sectionLabel(Component.translatable("gui.arenas_ld.dbs.rooms")));
        contentArea.child(spacer(2));

        if (rooms.isEmpty()) {
            LabelComponent empty = Components.label(Component.translatable("gui.arenas_ld.room_controller.spawners_empty"));
            empty.color(Color.ofArgb(INK_DIM));
            contentArea.child(empty);
        } else {
            for (int i = 0; i < rooms.size(); i++) {
                if (i > 0) contentArea.child(rowDivider());
                contentArea.child(roomRow(i));
            }
        }

        // Footer: apply mob ID + clear all
        ButtonComponent applyMobId = smallButton(
            Component.translatable("gui.arenas_ld.apply"),
            b -> applyMobId());
        applyMobId.horizontalSizing(Sizing.fixed(80));
        footerActions.child(applyMobId);

        if (!rooms.isEmpty()) {
            ButtonComponent clearAll = dangerButton(
                Component.translatable("gui.arenas_ld.room_controller.button.clear_all"),
                b -> {
                    if (minecraft == null) return;
                    minecraft.setScreen(new ConfirmScreen(ok -> {
                        minecraft.setScreen(this);
                        if (ok) ClientPlayNetworking.send(new DbsClearRoomsPayload(menu.getBlockPos()));
                    }, Component.translatable("gui.arenas_ld.room_controller.confirm.clear_title"),
                       Component.translatable("gui.arenas_ld.dbs.clear_rooms_confirm")));
                });
            clearAll.horizontalSizing(Sizing.fixed(90));
            footerActions.child(clearAll);
        }
    }

    private FlowLayout buildMobIdSection() {
        FlowLayout container = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        container.gap(0);

        LabelComponent caption = Components.label(Component.translatable("gui.arenas_ld.mob_id"));
        caption.color(Color.ofArgb(INK_DIM));
        container.child(caption);
        container.child(spacer(4));

        FlowLayout fieldRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldRow.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldRow.child(accent);

        TextBoxComponent field = Components.textBox(Sizing.expand(), mobIdValue);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(v -> {
            mobIdValue = v;
            if (mobIdDropdownOpen) refreshMobIdDropdown();
        });
        field.mouseDown().subscribe((mx, my, btn) -> { openMobIdDropdown(); return false; });
        mobIdTextField = field;
        fieldRow.child(field);

        ButtonComponent chevron = Components.button(Component.empty(), b -> toggleMobIdDropdown());
        chevron.sizing(Sizing.fixed(18), Sizing.fixed(20));
        chevron.renderer((context, rendered, delta) -> {
            String glyph = mobIdDropdownOpen ? "▲" : "▼";
            int tx = rendered.getX() + (rendered.getWidth() - this.font.width(glyph)) / 2;
            int ty = rendered.getY() + (rendered.getHeight() - this.font.lineHeight) / 2 + 1;
            context.drawString(this.font, glyph, tx, ty, INK_MID, false);
        });
        fieldRow.child(chevron);
        container.child(fieldRow);

        FlowLayout dropdown = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        dropdown.surface(Surface.BLANK);
        mobIdDropdownPanel = dropdown;
        container.child(dropdown);

        return container;
    }

    private void openMobIdDropdown() {
        if (!mobIdDropdownOpen) { mobIdDropdownOpen = true; refreshMobIdDropdown(); }
    }

    private void closeMobIdDropdown() {
        mobIdDropdownOpen = false;
        if (mobIdDropdownPanel != null) { mobIdDropdownPanel.clearChildren(); mobIdDropdownPanel.surface(Surface.BLANK); }
    }

    private void toggleMobIdDropdown() {
        if (mobIdDropdownOpen) closeMobIdDropdown(); else openMobIdDropdown();
    }

    private void refreshMobIdDropdown() {
        if (mobIdDropdownPanel == null) return;
        String current = mobIdTextField != null ? mobIdTextField.getValue() : "";
        List<String> candidates = mobIdCandidates(current);
        mobIdDropdownPanel.clearChildren();
        if (!mobIdDropdownOpen || candidates.isEmpty()) { mobIdDropdownPanel.surface(Surface.BLANK); return; }
        mobIdDropdownPanel.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));

        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        boolean first = true;
        for (String id : candidates) {
            if (!first) list.child(rowDivider());
            list.child(mobIdCandidateRow(id));
            first = false;
        }

        int rowsHeight = candidates.size() * 20 + Math.max(0, candidates.size() - 1);
        int visibleHeight = Math.min(rowsHeight, DROPDOWN_MAX_HEIGHT);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(visibleHeight), list);
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        mobIdDropdownPanel.child(scroll);
    }

    private List<String> mobIdCandidates(String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream()
            .map(net.minecraft.resources.ResourceLocation::toString)
            .filter(id -> needle.isEmpty() || id.toLowerCase(Locale.ROOT).contains(needle))
            .sorted()
            .limit(DROPDOWN_MAX_CANDIDATES)
            .collect(Collectors.toList());
    }

    private ButtonComponent mobIdCandidateRow(String id) {
        ButtonComponent row = Components.button(Component.empty(), b -> {
            mobIdValue = id;
            if (mobIdTextField != null) mobIdTextField.text(id);
            closeMobIdDropdown();
        });
        row.sizing(Sizing.fill(100), Sizing.fixed(20));
        row.renderer((context, rendered, delta) -> {
            int x1 = rendered.getX(); int y1 = rendered.getY();
            boolean hover = rendered.isHoveredOrFocused();
            context.fill(x1, y1, x1 + rendered.getWidth(), y1 + rendered.getHeight(), hover ? ROW_BG : PANEL_2);
            if (hover) context.fill(x1, y1, x1 + 2, y1 + rendered.getHeight(), ACCENT);
            context.drawString(this.font, id, x1 + 8, y1 + (rendered.getHeight() - this.font.lineHeight) / 2 + 1, INK, false);
        });
        return row;
    }

    private FlowLayout roomRow(int index) {
        BlockPos room = rooms.get(index);
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        row.surface(Surface.flat(index % 2 == 0 ? ROW_BG : ROW_BG_ALT));
        row.padding(Insets.of(0, 0, 6, 6));
        row.gap(4);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent num = Components.label(Component.literal((index + 1) + ". "));
        num.color(Color.ofArgb(INK_MID));
        num.horizontalSizing(Sizing.fixed(26));
        row.child(num);

        LabelComponent coord = Components.label(Component.literal(room.toShortString()));
        coord.color(Color.ofArgb(INK));
        coord.horizontalSizing(Sizing.expand());
        row.child(coord);

        ButtonComponent up = smallButton(Component.literal("↑"), b ->
            ClientPlayNetworking.send(new DbsMoveRoomPayload(menu.getBlockPos(), index, Math.max(0, index - 1))));
        up.sizing(Sizing.fixed(20), Sizing.fixed(18));
        up.active(index > 0);
        row.child(up);

        ButtonComponent down = smallButton(Component.literal("↓"), b ->
            ClientPlayNetworking.send(new DbsMoveRoomPayload(menu.getBlockPos(), index, Math.min(rooms.size() - 1, index + 1))));
        down.sizing(Sizing.fixed(20), Sizing.fixed(18));
        down.active(index < rooms.size() - 1);
        row.child(down);

        ButtonComponent remove = smallButton(Component.literal("×"), b ->
            ClientPlayNetworking.send(new DbsRemoveRoomPayload(menu.getBlockPos(), room)));
        remove.sizing(Sizing.fixed(20), Sizing.fixed(18));
        row.child(remove);

        return row;
    }

    private void applyMobId() {
        ClientPlayNetworking.send(new UpdateDbsEntityDefPayload(menu.getBlockPos(), mobIdValue));
    }

    private void openAttributesScreen() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.setScreen(new MobAttributesScreen(
            new MobAttributesScreenHandler(menu.containerId, minecraft.player.getInventory(), new MobAttributesData(menu.getBlockPos())),
            minecraft.player.getInventory(),
            Component.translatable("gui.arenas_ld.boss_attributes")));
    }

    private void openEquipmentScreen() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
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
        rooms.clear();
        rooms.addAll(menu.getRooms());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private FlowLayout sectionLabel(Component text) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(INK_DIM));
        row.child(label);
        return row;
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

    private ButtonComponent smallButton(Component text, java.util.function.Consumer<ButtonComponent> action) {
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

    private ButtonComponent dangerButton(Component text, java.util.function.Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text.copy().withStyle(net.minecraft.ChatFormatting.RED), action);
        button.sizing(Sizing.content(), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ((DANGER & 0x00FFFFFF) | 0x44000000) : ((DANGER & 0x00FFFFFF) | 0x1F000000);
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), DANGER);
        });
        return button;
    }
}
