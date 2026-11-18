package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.packet.SetRunSettingsPayload;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.rewards.RewardMath;
import net.ledok.arenas_ld.util.RunSettings;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.DANGER;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;

/**
 * The "Run settings" part of the dungeon and raid admin screens' General tab ({@link RunSettings}): "Rental kits only"
 * on/off and the base loot rolls (empty = the global {@code completion_loot_rolls}). Every change is saved at once,
 * like the breakable blocks list; the server's snapshot redraws the tab.
 */
public final class RunSettingsEditor {
    private RunSettingsEditor() {
    }

    /**
     * @param controllerPos the controller the admin screen edits
     * @param current       its settings, from the last snapshot
     * @param rollsDraft    what the rolls field holds, kept across rebuilds (null: show the saved value)
     * @param draftSink     receives the rolls field's text as it is typed
     */
    public static Flex build(BlockPos controllerPos, RunSettings current, String rollsDraft, Consumer<String> draftSink) {
        Flex col = Flex.column().gap(2);
        col.sizing(Sizing.fill(), Sizing.content());
        col.item(ArenasUi.text(Component.translatable("gui.arenas_ld.admin.run_settings.title"), INK_DIM));
        col.item(ArenasUi.spacer(2));

        Flex rentalRow = Flex.row().gap(6).alignItems(Align.CENTER);
        rentalRow.sizing(Sizing.fill(), Sizing.content());
        rentalRow.item(ArenasUi.text(Component.translatable(current.rentalRequired()
            ? "gui.arenas_ld.admin.run_settings.rental_required" : "gui.arenas_ld.admin.run_settings.rental_optional"), INK));
        rentalRow.item(ArenasUi.button(Component.translatable(current.rentalRequired()
            ? "gui.arenas_ld.admin.run_settings.allow_own_gear" : "gui.arenas_ld.admin.run_settings.require_rentals"), () ->
            send(controllerPos, current.withRentalMode(current.rentalRequired() ? RunSettings.OPTIONAL : RunSettings.REQUIRED))));
        col.item(rentalRow);
        col.item(ArenasUi.text(Component.translatable("gui.arenas_ld.admin.run_settings.rental_hint"), INK_DIM).maxWidth(470));
        col.item(ArenasUi.spacer(4));

        col.item(ArenasUi.text(Component.translatable("gui.arenas_ld.admin.run_settings.rolls"), INK));
        col.item(ArenasUi.text(Component.translatable("gui.arenas_ld.admin.run_settings.rolls_hint"), INK_DIM).maxWidth(470));
        Flex fieldRow = Flex.row().alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldRow.item(accent);
        String shown = rollsDraft != null ? rollsDraft
            : current.hasOwnLootRolls() ? RewardMath.number(current.baseLootRolls()) : "";
        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(100, shown, 16);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        var error = ArenasUi.label(100, Component.empty(), DANGER);
        error.sizing(Sizing.fill(), Sizing.content());
        field.changeListeners.add(value -> {
            draftSink.accept(value);
            error.text(Component.empty());
        });
        fieldRow.item(field);
        Runnable apply = () -> {
            String text = field.text().trim().replace(',', '.');
            double rolls;
            if (text.isEmpty()) {
                rolls = -1;
            } else {
                try {
                    rolls = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    rolls = Double.NaN;
                }
                if (!Double.isFinite(rolls) || rolls < 0 || rolls > RunSettings.MAX_ROLLS) {
                    error.text(Component.translatable("gui.arenas_ld.admin.run_settings.rolls_error", RunSettings.MAX_ROLLS));
                    return;
                }
            }
            draftSink.accept(null);
            error.text(Component.empty());
            send(controllerPos, current.withBaseLootRolls(rolls));
        };
        field.onSubmit(apply);
        fieldRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.admin.run_settings.rolls_set"), apply));
        fieldRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.admin.run_settings.rolls_default"), () -> {
            error.text(Component.empty());
            draftSink.accept(null);
            send(controllerPos, current.withBaseLootRolls(-1));
        }));
        col.item(fieldRow);
        col.item(error);
        return col;
    }

    private static void send(BlockPos controllerPos, RunSettings settings) {
        ArenasNetwork.sendToServer(new SetRunSettingsPayload(controllerPos, settings));
    }
}
