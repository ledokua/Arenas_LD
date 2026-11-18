package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static net.ledok.arenas_ld.screen.ArenasParchment.DANGER;
import static net.ledok.arenas_ld.screen.ArenasParchment.GOOD;
import static net.ledok.arenas_ld.screen.ArenasParchment.WARN;

/**
 * The lobby screens' instance status pills (raid and arena). The server only resends the controller when
 * an instance changes state, so a cooldown pill counts itself down from the snapshot it came with and
 * turns FREE at 0 ({@link #refresh}, once a second from the screen's tick).
 */
public final class InstancePills {
    private final List<Pill> pills = new ArrayList<>();

    /** Forgets the old pills; call before building the strip again. */
    public void clear() {
        pills.clear();
    }

    /** A pill for one instance, as of the snapshot ({@code cooldownTicks} left then). */
    public Flex pill(InstanceStatus status, int cooldownTicks) {
        Flex tag = Flex.row().padding(Insets.of(4, 4, 2, 4)).justify(Justify.CENTER).alignItems(Align.CENTER);
        tag.sizing(Sizing.content(), Sizing.content());
        TextNode label = ArenasParchment.text(Component.empty(), GOOD);
        tag.item(label);
        Pill pill = new Pill(tag, label, status, cooldownTicks);
        pills.add(pill);
        pill.show(0L);
        return tag;
    }

    /** Shows each pill as of {@code ticksSinceSnapshot} after the snapshot it was built from. */
    public void refresh(long ticksSinceSnapshot) {
        for (Pill pill : pills) {
            pill.show(ticksSinceSnapshot);
        }
    }

    public static Component statusText(InstanceStatus status, long ticksLeft) {
        if (status == InstanceStatus.COOLDOWN && ticksLeft > 0) {
            return Component.translatable("gui.arenas_ld.instance.status.cooldown", (ticksLeft + 19) / 20);
        }
        String key = status == InstanceStatus.RUNNING ? "running" : "free";
        return Component.translatable("gui.arenas_ld.instance.status." + key);
    }

    private static int color(InstanceStatus status) {
        return switch (status) {
            case FREE -> GOOD;
            case RUNNING -> WARN;
            case COOLDOWN -> DANGER;
        };
    }

    private record Pill(Flex tag, TextNode label, InstanceStatus status, int cooldownTicks) {
        void show(long ticksSinceSnapshot) {
            InstanceStatus shown = status;
            long left = cooldownTicks - Math.max(0L, ticksSinceSnapshot);
            if (status == InstanceStatus.COOLDOWN && left <= 0) {
                shown = InstanceStatus.FREE;
            }
            // Round up, so the pill reads 1s (not 0s) in its last second.
            int color = color(shown);
            label.text(statusText(status, left)).color(color);
            tag.backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        }
    }
}
