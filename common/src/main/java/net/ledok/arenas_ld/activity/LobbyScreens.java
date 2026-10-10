package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.platform.ArenasMenus;
import net.ledok.arenas_ld.platform.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
 * Opening lobby screens (with the entry gate), and finding the controller and viewers behind them.
 * Every lobby screen opens through here — using the block, {@code /arenasld lobby open}, chat
 * buttons and {@code ArenasApi.openLobbies} — so the gate and the dimension binding always apply.
 */
public final class LobbyScreens {
    private LobbyScreens() {
    }

    /**
     * Opens a controller's lobby screen after asking the gate about the activity itself.
     *
     * @param remote true when not opened at the block: the screen then stays open at any distance
     */
    public static boolean open(ServerPlayer player, BlockEntity controller, boolean remote) {
        if (!(controller instanceof ActivityHost host)
            || !(controller instanceof ExtendedMenuProvider<?> provider)
            || !(controller.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        Component refusal = EntryGates.check(player, host, null);
        if (refusal != null) {
            player.sendSystemMessage(refusal);
            return false;
        }
        ArenasMenus.open(player, bound(provider, level.dimension(), remote));
        return true;
    }

    /** {@code ArenasApi.openLobbies}: by id, from anywhere. */
    public static boolean openRemote(ServerPlayer player, ResourceLocation activity) {
        ActivityHost host = ActivityRegistry.resolve(player.server, activity);
        return host != null && open(player, host.asBlockEntity(), true);
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
                if (menu instanceof LobbyMenuBase lobbyMenu) {
                    lobbyMenu.bind(dimension, remote);
                }
                return menu;
            }
        };
    }

    /**
     * The controller a lobby packet means: the one the player's open lobby screen is bound to (it
     * may be in another dimension), else the block at {@code pos} in the player's own level.
     */
    public static <T extends BlockEntity> @Nullable T controller(ServerPlayer player, BlockPos pos, Class<T> type) {
        if (player.containerMenu instanceof LobbyMenuBase menu && menu.boundDimension() != null
            && pos.equals(menu.lobbyPos())) {
            // The open screen decides; never fall through to the same position in another dimension.
            ServerLevel level = player.server.getLevel(menu.boundDimension());
            BlockEntity be = level != null && level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
            return type.isInstance(be) ? type.cast(be) : null;
        }
        BlockEntity be = player.level().getBlockEntity(pos);
        return type.isInstance(be) ? type.cast(be) : null;
    }

    /** Players whose open lobby screen shows this controller. */
    public static List<ServerPlayer> viewers(BlockEntity controller) {
        List<ServerPlayer> viewers = new ArrayList<>();
        if (!(controller.getLevel() instanceof ServerLevel level)) {
            return viewers;
        }
        MinecraftServer server = level.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof LobbyMenuBase menu)) {
                continue;
            }
            // An unbound menu (opened around LobbyScreens) can only be at the block, in this level.
            boolean shows = menu.boundDimension() == null
                ? player.level() == level && menu.lobbyPos().equals(controller.getBlockPos())
                : menu.shows(level.dimension(), controller.getBlockPos());
            if (shows) {
                viewers.add(player);
            }
        }
        return viewers;
    }
}
