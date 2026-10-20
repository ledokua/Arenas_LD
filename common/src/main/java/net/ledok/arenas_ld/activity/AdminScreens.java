package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.screen.ArenaControllerAdminMenuProvider;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminMenuProvider;
import net.ledok.arenas_ld.platform.ArenasMenus;
import net.ledok.arenas_ld.platform.ExtendedMenuProvider;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.screen.RaidControllerAdminMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Opening controller admin screens, and finding the controller and viewers behind them. Every admin
 * screen opens through here ({@code /arenasld admin}, {@code ArenasApi.openAdmin}), so the op check
 * and the dimension binding always apply. The counterpart of {@link LobbyScreens}.
 */
public final class AdminScreens {
    private AdminScreens() {
    }

    /**
     * Opens a controller's admin screen.
     *
     * @param remote true when not opened at the block: the screen then stays open at any distance
     * @return null when opened, else the reason it was refused
     */
    public static @Nullable Component open(ServerPlayer player, BlockEntity controller, boolean remote) {
        if (!player.hasPermissions(2)) {
            return Component.translatable("message.arenas_ld.admin.no_permission");
        }
        if (!(controller.getLevel() instanceof ServerLevel level)) {
            return Component.translatable("gui.arenas_ld.admin.failure.no_controller");
        }
        ExtendedMenuProvider<?> provider = switch (controller) {
            case DungeonControllerBlockEntity dungeon -> new DungeonControllerAdminMenuProvider(dungeon);
            case RaidControllerBlockEntity raid -> new RaidControllerAdminMenuProvider(raid);
            case ArenaControllerBlockEntity arena -> new ArenaControllerAdminMenuProvider(arena);
            default -> null;
        };
        if (provider == null) {
            return Component.translatable("gui.arenas_ld.admin.failure.no_controller");
        }
        ArenasMenus.open(player, bound(provider, level.dimension(), remote));
        return null;
    }

    private static <D> ExtendedMenuProvider<D> bound(ExtendedMenuProvider<D> delegate, ResourceKey<Level> dimension,
                                                     boolean remote) {
        return new ExtendedMenuProvider<>() {
            @Override
            public D getScreenOpeningData(ServerPlayer player) {
                return delegate.getScreenOpeningData(player);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, D> openingDataCodec() {
                return delegate.openingDataCodec();
            }

            @Override
            public Component getDisplayName() {
                return delegate.getDisplayName();
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                AbstractContainerMenu menu = delegate.createMenu(syncId, inventory, player);
                if (menu instanceof AdminMenuBase adminMenu) {
                    adminMenu.bind(dimension, remote);
                }
                return menu;
            }
        };
    }

    /**
     * The controller an admin packet means: the one the player's open admin screen is bound to (it
     * may be in another dimension). Null when no admin screen for {@code pos} is open: every admin
     * screen opens bound through here, so a packet without one is stale (the screen was closed, say
     * because its controller was broken) and must not land on a controller at the same position in
     * the player's own dimension. Callers still check {@code hasPermissions(2)}.
     */
    public static <T extends BlockEntity> @Nullable T controller(ServerPlayer player, BlockPos pos, Class<T> type) {
        if (!(player.containerMenu instanceof AdminMenuBase menu) || menu.boundDimension() == null
            || !pos.equals(menu.controllerPos())) {
            return null;
        }
        ServerLevel level = player.server.getLevel(menu.boundDimension());
        BlockEntity be = level != null && level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
        return type.isInstance(be) ? type.cast(be) : null;
    }

    /** Ops whose open admin screen shows this controller: who gets its admin snapshots. */
    public static List<ServerPlayer> viewers(BlockEntity controller) {
        List<ServerPlayer> viewers = new ArrayList<>();
        if (!(controller.getLevel() instanceof ServerLevel level)) {
            return viewers;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof AdminMenuBase menu) || !player.hasPermissions(2)) {
                continue;
            }
            // An unbound menu (opened around AdminScreens) can only be at the block, in this level.
            boolean shows = menu.boundDimension() == null
                ? player.level() == level && menu.controllerPos().equals(controller.getBlockPos())
                : menu.shows(level.dimension(), controller.getBlockPos());
            if (shows) {
                viewers.add(player);
            }
        }
        return viewers;
    }
}
