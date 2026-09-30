package net.ledok.arenas_ld.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

/** The one way to open the mod's menus, so data-carrying menus always get their opening data. */
public final class ArenasMenus {
    private ArenasMenus() {}

    /** Server side only; a client-side {@code player} is ignored (the server opens the menu). */
    public static void open(Player player, MenuProvider provider) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (provider instanceof ExtendedMenuProvider<?> extended) {
            ArenasPlatform.INSTANCE.openExtendedMenu(serverPlayer, extended);
        } else {
            serverPlayer.openMenu(provider);
        }
    }
}
