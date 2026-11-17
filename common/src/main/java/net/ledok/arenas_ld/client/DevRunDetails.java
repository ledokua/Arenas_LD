package net.ledok.arenas_ld.client;

import net.ledok.arenas_ld.packet.RunDetailsPayload;
import net.ledok.arenas_ld.packet.RunHudPayload;
import net.ledok.arenas_ld.screen.RunDetailsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Development only (off unless {@code -Darenas_ld.devRunDetails=live|end}): a few seconds into a world, opens the run
 * details screen with made-up numbers, to look at its layout without playing a run.
 */
public final class DevRunDetails {
    private static int ticks;
    private static boolean shown;

    private DevRunDetails() {
    }

    public static void tick() {
        String mode = System.getProperty("arenas_ld.devRunDetails");
        Minecraft mc = Minecraft.getInstance();
        if (mode == null || shown || mc.player == null || mc.screen != null || ++ticks < 100) {
            return;
        }
        shown = true;
        boolean end = "end".equals(mode);
        UUID self = mc.player.getUUID();
        List<Component> rewards = end ? List.of(Component.literal("6 loot rolls · 840 currency · 1,200 XP"),
            Component.literal("Loot: base 3 · Hardcore ×2 → 6 (rolled 6)"),
            Component.literal("Currency: base 400 · Hard ×1.5 · Hardcore ×2 · Rental ×0.7 → 840")) : List.of();
        var stateA = end ? RunDetailsPayload.State.WON : RunDetailsPayload.State.ALIVE;
        List<RunDetailsPayload.Row> rows = List.of(
            new RunDetailsPayload.Row(mc.player.getScoreboardName(), stateA, 0, -1, end ? -1 : 31, end ? -1 : 40,
                12_400, 3_100, 820, 420, 37, Component.empty(), self, "wizards:arcane_spell_book", 42, 120, rewards, ItemStack.EMPTY),
            new RunDetailsPayload.Row("Tend1tnuy", end ? RunDetailsPayload.State.WON : RunDetailsPayload.State.ALIVE, 1, -1,
                end ? -1 : 6, end ? -1 : 36, 9_800, 5_600, 1_900, 0, 21, Component.empty(),
                UUID.fromString("00000000-0000-0000-0000-000000000002"), "skills:warrior", 38, 0,
                end ? List.of(Component.literal("3 loot rolls · 600 currency · 1,000 XP")) : List.of(), ItemStack.EMPTY),
            new RunDetailsPayload.Row("LunarApostle", end ? RunDetailsPayload.State.FORFEITED : RunDetailsPayload.State.DOWNED,
                2, end ? -1 : 7, -1, -1, 1_200, 900, 150, 6_300, 3, Component.empty(),
                UUID.fromString("00000000-0000-0000-0000-000000000003"), "paladins:holy_book", 40, 5_100, List.of(),
                new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK)));
        RunDetailsScreen.onPayload(new RunDetailsPayload(Component.literal("Wither"), Component.literal("Hard"), true,
            List.of(Component.literal("Prime"), Component.literal("Glass cannon")),
            Component.literal("Room 3 · Kill the boss"), end ? 0 : 9_240, 12_000, 378, end ? RunHudPayload.NO_BOSS_HP : 63,
            Component.literal("3 loot rolls · 400 currency · 1,000 XP — Picked: ×2 loot"),
            end ? Component.literal("Win") : Component.empty(),
            List.of(Component.literal("Top damage: " + mc.player.getScoreboardName() + " (12.4k)"),
                Component.literal("Top healer: LunarApostle (6.3k)")),
            List.of(Component.literal("Wither at 75% (2:10)"), Component.literal("Wither killed at 6:18")),
            rows, -1, end, false, !end, end));
    }
}
