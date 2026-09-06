package net.ledok.arenas_ld.raid.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
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
import net.ledok.vectorlib.client.presentation.CanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.function.Consumer;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT_DARK;
import static net.ledok.arenas_ld.screen.ArenasUi.BG;
import static net.ledok.arenas_ld.screen.ArenasUi.DANGER;
import static net.ledok.arenas_ld.screen.ArenasUi.GOOD;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE_HI;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_MID;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;

/**
 * Minimal admin view for a raid boss spawner. The spawner is now a passive arena:
 * the controller owns all per-tier mechanics (HP/damage scaling, time limit,
 * loot, XP, regen, per-player HP scaling). This screen only sets the mob ID,
 * shows the linked respawn-point count, and opens the mob attributes /
 * equipment sub-screens.
 */
public class RaidBossSpawnerScreen extends CanvasHandledScreen<RaidBossSpawnerScreenHandler> {
    private String mobIdValue = "";

    private Flex contentArea;
    private Flex footerActions;
    private Label footerLabel;
    private String footerError;
    private boolean loaded = false;

    public RaidBossSpawnerScreen(RaidBossSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(600, 340), Placement.Screen.center());
        canvas.theme(ArenasUi.THEME);
        dimBackground(false);
        fillWindow();
    }

    /**
     * Swallow the inventory key so tapping E mid-edit doesn't kick the admin out.
     * Text fields get first crack at the key, so typing "e" in the mob-ID field
     * still works. Closing happens via × or Escape.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)
                && !(input.focusedNode() instanceof TextField)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        buildAll();
    }

    private void buildAll() {
        loadFromBlockEntity();
        canvas.clear();

        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);
        root.backgroundFill(BG);

        float shellWidth  = Math.max(440, Math.min(560, canvas.width() - 24));
        float shellHeight = Math.max(260, canvas.height() - 24);
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
        Flex header = Flex.row().gap(10).padding(Insets.of(8, 10, 8, 10)).alignItems(Align.CENTER);
        header.sizing(Sizing.fill(), Sizing.fixed(46));
        header.backgroundFill(PANEL_2);

        Flex mark = Flex.column();
        mark.sizing(Sizing.fixed(20), Sizing.fixed(20));
        mark.backgroundFill(ACCENT, ACCENT_DARK, 1);
        header.item(mark);

        Flex info = Flex.column().gap(3);
        info.sizing(Sizing.content(), Sizing.content());

        info.item(ArenasUi.text(Component.translatable("gui.arenas_ld.boss_spawner"), INK));

        Flex meta = Flex.row().gap(10).alignItems(Align.CENTER);
        meta.sizing(Sizing.content(), Sizing.content());
        meta.item(smallMeta("POS · X " + menu.blockEntity.getBlockPos().getX()
            + " · Y " + menu.blockEntity.getBlockPos().getY()
            + " · Z " + menu.blockEntity.getBlockPos().getZ(), INK_DIM));
        Flex live = Flex.row().gap(4).alignItems(Align.CENTER);
        live.sizing(Sizing.content(), Sizing.content());
        live.item(ArenasUi.text(Component.literal("●"), GOOD));
        live.item(smallMeta("LIVE", GOOD));
        meta.item(live);
        info.item(meta);
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

        contentArea.item(sectionHeader(Component.translatable("gui.arenas_ld.tab_general")));
        contentArea.item(ArenasUi.spacer(2));

        contentArea.item(searchableMobIdField(tr("gui.arenas_ld.mob_id"), mobIdValue, v -> mobIdValue = v));

        contentArea.item(ArenasUi.spacer(8));
        int linkedRespawns = menu.blockEntity.getRespawnPointOffsets().size();
        contentArea.item(ArenasUi.text(
            Component.translatable("gui.arenas_ld.respawn_points_linked_readonly", linkedRespawns),
            INK_MID));

        contentArea.item(ArenasUi.spacer(8));
        Flex actions = Flex.row().gap(6).alignItems(Align.CENTER);
        actions.sizing(Sizing.fill(), Sizing.content());

        actions.item(ArenasUi.button(
            Component.translatable("gui.arenas_ld.attributes"), 110, 18,
            this::openAttributesScreen));

        actions.item(ArenasUi.button(
            Component.translatable("gui.arenas_ld.equipment"), 110, 18,
            this::openEquipmentScreen));

        actions.spacer();
        contentArea.item(actions);

        footerActions.item(ArenasUi.button(
            Component.translatable("gui.arenas_ld.save"), 110, 18,
            this::onSave));

        if (footerError != null) {
            footerLabel.text(Component.literal(footerError));
        } else {
            footerLabel.text(Component.empty());
        }
    }

    private void loadFromBlockEntity() {
        if (loaded) return;
        if (menu.blockEntity == null) return;
        loaded = true;
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
        ClientPlayNetworking.send(
            new ModPackets.OpenEquipmentEditorPayload(menu.blockEntity.getBlockPos()));
    }

    // ── UI Helpers ──────────────────────────────────────────────────────────────

    private Flex searchableMobIdField(String caption, String initial, Consumer<String> onChange) {
        Flex col = Flex.column().gap(4);
        col.sizing(Sizing.fill(), Sizing.content());

        Flex head = Flex.row().alignItems(Align.CENTER);
        head.sizing(Sizing.fill(), Sizing.content());
        head.item(ArenasUi.text(Component.literal(caption), INK_DIM));
        col.item(head);

        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldRow.item(accent);

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(50, initial, 256);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.changeListeners.add(onChange::accept);
        fieldRow.item(field);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(BuiltInRegistries.ENTITY_TYPE, field);
        fieldRow.item(dropdown.chevron());
        col.item(fieldRow);
        col.item(dropdown.panel());

        return col;
    }

    private Flex textField(String caption, String initial, Consumer<String> onChange) {
        Flex col = Flex.column().gap(4);
        col.sizing(Sizing.fill(), Sizing.content());

        Flex head = Flex.row().alignItems(Align.CENTER);
        head.sizing(Sizing.fill(), Sizing.content());
        head.item(ArenasUi.text(Component.literal(caption), INK_DIM));
        col.item(head);

        Flex fieldWrap = Flex.row().alignItems(Align.CENTER);
        fieldWrap.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldWrap.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldWrap.item(accent);

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(50, initial, 256);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.changeListeners.add(onChange::accept);
        fieldWrap.item(field);

        col.item(fieldWrap);
        return col;
    }

    private Flex sectionHeader(Component title) {
        Flex row = Flex.row().gap(8).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(ArenasUi.text(title, INK_DIM));
        return row;
    }

    private net.ledok.vectorlib.client.canvas.TextNode smallMeta(String text, int color) {
        return ArenasUi.text(Component.literal(text), color);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
