package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.util.RewardCommands;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.minecraft.network.chat.Component;

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
 * The "Commands on win" list editor of the dungeon and raid tier tabs, drawn like the room
 * reward screen's command list: one row per command with a remove button, then a text field
 * and Add. Edits only change {@code commands}; the tier's Apply button saves them.
 */
public final class RewardCommandsEditor {
    private RewardCommandsEditor() {
    }

    /**
     * @param commands   the tier's edited command list (changed in place)
     * @param draft      what the add field holds, kept across rebuilds
     * @param draftSink  receives the add field's text as it is typed
     * @param rebuild    redraws the tab after an add or remove
     */
    public static Flex build(List<String> commands, String draft, Consumer<String> draftSink, Runnable rebuild) {
        Flex col = Flex.column().gap(2);
        col.sizing(Sizing.fill(), Sizing.content());
        col.item(ArenasUi.text(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.commands",
            commands.size(), RewardCommands.MAX_COMMANDS), INK_DIM));
        col.item(ArenasUi.text(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.commands_hint"), INK_DIM)
            .maxWidth(470));
        col.item(ArenasUi.spacer(2));
        for (int i = 0; i < commands.size(); i++) {
            if (i > 0) col.item(ArenasUi.hairline());
            col.item(commandRow(commands, i, rebuild));
        }
        col.item(ArenasUi.spacer(2));
        col.item(addRow(commands, draft, draftSink, rebuild));
        return col;
    }

    private static Flex commandRow(List<String> commands, int index, Runnable rebuild) {
        String command = commands.get(index);
        Flex row = Flex.row().gap(6).padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(20));
        row.backgroundFill(index % 2 == 0 ? ROW_BG : ROW_BG_ALT);

        Label text = ArenasUi.label(100, Component.literal(command), INK);
        text.sizing(Sizing.expand(), Sizing.content());
        text.pickable(true);
        text.tooltip(Component.literal(command));
        row.item(text);

        row.item(ArenasUi.button(Component.literal("✕"), 20, 16, () -> {
            commands.remove(index);
            rebuild.run();
        }));
        return row;
    }

    private static Flex addRow(List<String> commands, String draft, Consumer<String> draftSink, Runnable rebuild) {
        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);

        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldRow.item(accent);

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(100, draft, RewardCommands.MAX_LENGTH);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        IdSuggestionDropdown.attachFullValueTooltip(field);
        field.changeListeners.add(draftSink);
        fieldRow.item(field);

        Button add = ArenasUi.button(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.commands_add"),
            64, 18, () -> {
                String command = RewardCommands.clean(field.text());
                if (command.isEmpty() || commands.size() >= RewardCommands.MAX_COMMANDS) {
                    return;
                }
                commands.add(command);
                draftSink.accept("");
                rebuild.run();
            });
        add.enabled(commands.size() < RewardCommands.MAX_COMMANDS);
        fieldRow.item(add);
        return fieldRow;
    }
}
