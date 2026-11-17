package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.packet.SetBreakableBlocksPayload;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.util.BreakableBlocks;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;
import static net.ledok.arenas_ld.screen.ArenasUi.ROW_BG;
import static net.ledok.arenas_ld.screen.ArenasUi.ROW_BG_ALT;

/**
 * The "Breakable blocks" part of the dungeon and raid admin screens' General tab: the controller's own list
 * ({@link BreakableBlocks}), whether it adds to the global {@code run_breakable_blocks} or replaces it, one row
 * per entry with a remove button, and a block id field (or {@code #tag}) with Add. Every change is saved at
 * once; the server's snapshot redraws the tab.
 */
public final class BreakableBlocksEditor {
    private static final int MAX_ENTRIES = 64;

    private BreakableBlocksEditor() {
    }

    /**
     * @param controllerPos the controller the admin screen edits
     * @param current       its list, from the last snapshot
     * @param draft         what the add field holds, kept across rebuilds
     * @param draftSink     receives the add field's text as it is typed
     */
    public static Flex build(BlockPos controllerPos, BreakableBlocks current, String draft, Consumer<String> draftSink) {
        Flex col = Flex.column().gap(2);
        col.sizing(Sizing.fill(), Sizing.content());
        col.item(ArenasUi.text(Component.translatable("gui.arenas_ld.admin.breakable.title", current.blocks().size()), INK_DIM));
        col.item(ArenasUi.text(Component.translatable("gui.arenas_ld.admin.breakable.hint"), INK_DIM).maxWidth(470));
        col.item(ArenasUi.spacer(2));

        Flex modeRow = Flex.row().gap(6).alignItems(Align.CENTER);
        modeRow.sizing(Sizing.fill(), Sizing.content());
        modeRow.item(ArenasUi.text(Component.translatable(current.replaces()
            ? "gui.arenas_ld.admin.breakable.mode_replace" : "gui.arenas_ld.admin.breakable.mode_add"), INK));
        modeRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.admin.breakable.switch_mode"), 120, 16, () ->
            send(controllerPos, current.replaces() ? BreakableBlocks.ADD : BreakableBlocks.REPLACE, current.blocks())));
        col.item(modeRow);
        col.item(ArenasUi.spacer(2));

        List<String> blocks = current.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) col.item(ArenasUi.hairline());
            col.item(entryRow(controllerPos, current, i));
        }
        col.item(ArenasUi.spacer(2));
        col.item(addRow(controllerPos, current, draft, draftSink));
        return col;
    }

    private static Flex entryRow(BlockPos controllerPos, BreakableBlocks current, int index) {
        String entry = current.blocks().get(index);
        Flex row = Flex.row().gap(6).padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(20));
        row.backgroundFill(index % 2 == 0 ? ROW_BG : ROW_BG_ALT);

        Label text = ArenasUi.label(100, Component.literal(entry), INK);
        text.sizing(Sizing.expand(), Sizing.content());
        row.item(text);

        row.item(ArenasUi.button(Component.literal("✕"), 20, 16, () -> {
            List<String> next = new ArrayList<>(current.blocks());
            next.remove(index);
            send(controllerPos, current.mode(), next);
        }));
        return row;
    }

    private static Flex addRow(BlockPos controllerPos, BreakableBlocks current, String draft, Consumer<String> draftSink) {
        Flex wrap = Flex.column();
        wrap.sizing(Sizing.fill(), Sizing.content());
        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldRow.item(accent);

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(100, draft, 128);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.changeListeners.add(draftSink);
        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(BuiltInRegistries.BLOCK, field);
        fieldRow.item(field);
        fieldRow.item(dropdown.chevron());

        boolean full = current.blocks().size() >= MAX_ENTRIES;
        var add = ArenasUi.button(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.commands_add"),
            64, 18, () -> {
                String entry = field.text().trim();
                if (entry.isEmpty() || current.blocks().contains(entry) || current.blocks().size() >= MAX_ENTRIES) {
                    return;
                }
                List<String> next = new ArrayList<>(current.blocks());
                next.add(entry);
                draftSink.accept("");
                send(controllerPos, current.mode(), next);
            });
        add.enabled(!full);
        fieldRow.item(add);
        wrap.item(fieldRow);
        wrap.item(dropdown.panel());
        return wrap;
    }

    private static void send(BlockPos controllerPos, String mode, List<String> blocks) {
        ArenasNetwork.sendToServer(new SetBreakableBlocksPayload(controllerPos, mode, blocks));
    }
}
