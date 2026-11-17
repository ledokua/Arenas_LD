package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.rewards.RunModifierOption;
import net.ledok.vectorlib.client.canvas.ItemStackNode;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Widget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static net.ledok.arenas_ld.screen.ArenasParchment.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasParchment.GOOD;
import static net.ledok.arenas_ld.screen.ArenasParchment.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasParchment.INK;
import static net.ledok.arenas_ld.screen.ArenasParchment.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasParchment.INK_MID;
import static net.ledok.arenas_ld.screen.ArenasParchment.ROW_BG;

/**
 * The lobby's run modifier switches, hardcore among them (dungeon, raid and arena lobby screens): one row
 * per modifier the server offers, with its icon, name, description, reward effect and an On/Off button the
 * lobby owner can press; everyone else sees the state. A row that conflicts with a picked modifier turns red and
 * names it. The rebuild after a toggle comes with the snapshot.
 */
public final class RunModifierToggles {
    private static final int CONFLICT_BG = 0xFFD42A1E;
    private static final int CONFLICT_BORDER = 0xFF7A0E08;
    private static final int CONFLICT_INK = 0xFF4A0E0A;

    private RunModifierToggles() {
    }

    /** Nothing (an empty column) when the server offers none. */
    public static Flex build(BlockPos controller, List<RunModifierOption> options, Set<String> active, boolean isOwner,
                             Component pickedLine) {
        Flex col = Flex.column().gap(3);
        col.sizing(Sizing.fill(), Sizing.content());
        if (options.isEmpty()) {
            return col;
        }
        col.item(ArenasParchment.text(Component.translatable("gui.arenas_ld.run_modifiers.title"), INK_DIM));
        for (RunModifierOption option : options) {
            col.item(row(controller, option, active.contains(option.id()), isOwner, conflict(option, active, options)));
        }
        if (!pickedLine.getString().isEmpty()) {
            var line = ArenasParchment.label(100, pickedLine, ACCENT).tooltipWhenCut(true);
            line.sizing(Sizing.fill(), Sizing.content());
            col.item(line);
        }
        return col;
    }

    /** The name of a picked modifier {@code option} conflicts with, or null. */
    private static @Nullable Component conflict(RunModifierOption option, Set<String> active, List<RunModifierOption> options) {
        for (String other : option.conflicts()) {
            if (!other.equals(option.id()) && active.contains(other)) {
                return options.stream().filter(o -> o.id().equals(other)).findFirst()
                    .map(RunModifierOption::name).orElse(Component.literal(other));
            }
        }
        return null;
    }

    /**
     * One switch row. A row that conflicts with a picked modifier is strong red with a dark red switch and
     * "Conflict: …" next to it (LeDok's mockup); pressing it still asks the server, which refuses in chat.
     */
    private static Flex row(BlockPos controller, RunModifierOption option, boolean on, boolean isOwner,
                            @Nullable Component conflictsWith) {
        boolean conflict = conflictsWith != null;
        Flex row = Flex.row().gap(8).padding(Insets.of(5, 8, 5, 8)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        if (conflict) {
            row.backgroundFill(CONFLICT_BG, CONFLICT_BORDER, 1);
        } else {
            row.backgroundFill(on ? ROW_BG : (ROW_BG & 0x00FFFFFF) | 0x99000000, on ? ACCENT : HAIRLINE, 1);
        }

        row.item(new Icon(option.icon()));

        Flex text = Flex.column().gap(1);
        text.sizing(Sizing.expand(), Sizing.content());
        text.item(ArenasParchment.text(option.name(), conflict || on ? INK : INK_MID));
        if (!option.description().getString().isEmpty()) {
            var description = ArenasParchment.label(100, option.description(), conflict ? CONFLICT_INK : INK_DIM)
                .tooltipWhenCut(true);
            description.sizing(Sizing.fill(), Sizing.content());
            text.item(description);
        }
        text.item(ArenasParchment.text(option.rewards(), conflict ? INK : GOOD));
        row.item(text);

        if (conflict) {
            var why = ArenasParchment.label(120, Component.translatable("gui.arenas_ld.run_modifiers.conflict", conflictsWith),
                INK).tooltipWhenCut(true);
            why.sizing(Sizing.content(), Sizing.content());
            row.item(why);
        }
        Button toggle = ArenasParchment.button(Component.translatable(on
            ? "gui.arenas_ld.dungeon_controller.ui.hardcore.on" : "gui.arenas_ld.dungeon_controller.ui.hardcore.off"),
            44, 18, () -> ArenasNetwork.sendToServer(
                new net.ledok.arenas_ld.packet.SetLobbyModifierPayload(controller, option.id(), !on)));
        if (conflict) {
            toggle.style(ArenasParchment.CONFLICT_WIDGETS);
        }
        toggle.enabled(isOwner);
        row.item(toggle);
        return row;
    }

    /** The modifier's icon item, 16 units. */
    private static final class Icon extends Widget {
        private final ItemStack stack;

        Icon(String itemId) {
            super(16, 16);
            ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
            this.stack = id == null || !BuiltInRegistries.ITEM.containsKey(id)
                ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
        }

        @Override
        protected void rebuild() {
            if (!stack.isEmpty()) {
                add(ItemStackNode.of(stack, 0, 0, 16).decorations(false));
            }
        }
    }
}
