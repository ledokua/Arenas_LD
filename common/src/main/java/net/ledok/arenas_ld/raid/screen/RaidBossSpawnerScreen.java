package net.ledok.arenas_ld.raid.screen;

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
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Minimal admin view for a raid boss spawner. The spawner is now a passive arena:
 * the controller owns all per-tier mechanics (HP/damage scaling, time limit,
 * loot, XP, regen, per-player HP scaling). This screen only sets the mob ID,
 * shows the linked respawn-point count, and opens the mob attributes /
 * equipment sub-screens.
 */
public class RaidBossSpawnerScreen extends BaseOwoHandledScreen<FlowLayout, RaidBossSpawnerScreenHandler> {
    private static final int BG          = 0xFF070E14;
    private static final int PANEL       = 0xFF121922;
    private static final int PANEL_2     = 0xFF0C1218;
    private static final int HAIRLINE    = 0xFF283442;
    private static final int HAIRLINE_HI = 0xFF3A4A5C;
    private static final int INK         = 0xFFE8EEF5;
    private static final int INK_MID     = 0xFF9AA8B8;
    private static final int INK_DIM     = 0xFF5F6E80;
    private static final int GOOD        = 0xFF86D36C;
    private static final int DANGER      = 0xFFE8624A;
    private static final int ACCENT      = 0xFFA98BE8;
    private static final int ACCENT_DARK = 0xFF6C4FB5;

    private String mobIdValue = "";

    private FlowLayout contentArea;
    private FlowLayout footerActions;
    private LabelComponent footerLabel;
    private String footerError;

    public RaidBossSpawnerScreen(RaidBossSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.inventoryLabelY = 9999;
        this.titleLabelY = 9999;
    }

    /**
     * Swallow the inventory key so tapping E mid-edit doesn't kick the admin out.
     * Text fields get first crack at the key via the owo focus dispatch, so
     * typing "e" in the mob-ID field still works. Closing happens via × or Escape.
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
        loadFromBlockEntity();

        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth  = Math.max(440, Math.min(560, this.width - 24));
        int shellHeight = Math.max(260, this.height - 24);
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
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(46));
        header.surface(Surface.flat(PANEL_2));
        header.padding(Insets.of(8, 8, 10, 10));
        header.gap(10);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(20), Sizing.fixed(20));
        mark.surface(Surface.flat(ACCENT).and(Surface.outline(ACCENT_DARK)));
        header.child(mark);

        FlowLayout info = Containers.verticalFlow(Sizing.content(), Sizing.content());
        info.gap(3);

        LabelComponent titleLabel = Components.label(Component.translatable("gui.arenas_ld.boss_spawner"));
        titleLabel.color(Color.ofArgb(INK));
        info.child(titleLabel);

        FlowLayout meta = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        meta.gap(10);
        meta.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        meta.child(smallMeta("POS · X " + menu.blockEntity.getBlockPos().getX()
            + " · Y " + menu.blockEntity.getBlockPos().getY()
            + " · Z " + menu.blockEntity.getBlockPos().getZ(), INK_DIM));
        FlowLayout live = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        live.gap(4);
        live.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        live.child(text(Component.literal("●"), GOOD));
        live.child(smallMeta("LIVE", GOOD));
        meta.child(live);
        info.child(meta);
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

        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.tab_general")));
        contentArea.child(spacer(2));

        contentArea.child(searchableMobIdField(tr("gui.arenas_ld.mob_id"), mobIdValue, v -> mobIdValue = v));

        contentArea.child(spacer(8));
        int linkedRespawns = menu.blockEntity.getRespawnPointOffsets().size();
        contentArea.child(text(
            Component.translatable("gui.arenas_ld.respawn_points_linked_readonly", linkedRespawns),
            INK_MID));

        contentArea.child(spacer(8));
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.gap(6);
        actions.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        ButtonComponent attributes = smallButton(
            Component.translatable("gui.arenas_ld.attributes"),
            b -> openAttributesScreen());
        attributes.horizontalSizing(Sizing.fixed(110));
        actions.child(attributes);

        ButtonComponent equipment = smallButton(
            Component.translatable("gui.arenas_ld.equipment"),
            b -> openEquipmentScreen());
        equipment.horizontalSizing(Sizing.fixed(110));
        actions.child(equipment);

        actions.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        contentArea.child(actions);

        ButtonComponent save = smallButton(
            Component.translatable("gui.arenas_ld.save"),
            b -> onSave());
        save.horizontalSizing(Sizing.fixed(110));
        footerActions.child(save);

        if (footerError != null) {
            footerLabel.text(Component.literal(footerError));
        } else {
            footerLabel.text(Component.empty());
        }
    }

    private void loadFromBlockEntity() {
        if (menu.blockEntity == null) return;
        mobIdValue = menu.blockEntity.getMobId();
    }

    private void onSave() {
        ClientPlayNetworking.send(new ModPackets.UpdateBossSpawnerPayload(
            menu.blockEntity.getBlockPos(),
            mobIdValue
        ));
        this.onClose();
    }

    private void openAttributesScreen() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        this.minecraft.setScreen(new net.ledok.arenas_ld.screen.MobAttributesScreen(
            new net.ledok.arenas_ld.screen.MobAttributesScreenHandler(
                menu.containerId,
                minecraft.player.getInventory(),
                new net.ledok.arenas_ld.screen.MobAttributesData(menu.blockEntity.getBlockPos())),
            minecraft.player.getInventory(),
            Component.translatable("gui.arenas_ld.boss_attributes")));
    }

    private void openEquipmentScreen() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new net.ledok.arenas_ld.networking.ModPackets.OpenEquipmentEditorPayload(menu.blockEntity.getBlockPos()));
    }

    // ── UI Helpers ──────────────────────────────────────────────────────────────

    private FlowLayout searchableMobIdField(String caption, String initial, Consumer<String> onChange) {
        FlowLayout col = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        col.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        head.child(text(Component.literal(caption), INK_DIM));
        col.child(head);

        FlowLayout fieldRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldRow.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldRow.child(accent);

        TextBoxComponent field = Components.textBox(Sizing.expand(), initial == null ? "" : initial);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(onChange::accept);
        fieldRow.child(field);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(
            this.font, BuiltInRegistries.ENTITY_TYPE, field);
        fieldRow.child(dropdown.chevron());
        col.child(fieldRow);
        col.child(dropdown.panel());

        return col;
    }

    private FlowLayout textField(String caption, String initial, Consumer<String> onChange) {
        FlowLayout col = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        col.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        head.child(text(Component.literal(caption), INK_DIM));
        col.child(head);

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

    private FlowLayout sectionHeader(Component title) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.gap(8);
        LabelComponent titleLabel = Components.label(title);
        titleLabel.color(Color.ofArgb(INK_DIM));
        row.child(titleLabel);
        return row;
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

    private LabelComponent smallMeta(String text, int color) {
        LabelComponent label = Components.label(Component.literal(text));
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

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
