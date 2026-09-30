package net.ledok.arenas_ld.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * The loader-specific operations the shared code needs; each loader module provides one
 * implementation through {@code META-INF/services}. Common code never touches Fabric API or
 * NeoForge directly — events and packet receivers go through {@link ArenasEvents} and
 * {@link ArenasNetwork}, which the loader glue drains.
 */
public interface ArenasPlatform {
    ArenasPlatform INSTANCE = ServiceLoader.load(ArenasPlatform.class).findFirst()
            .orElseThrow(() -> new IllegalStateException("No Arenas_LD ArenasPlatform service"));

    boolean isModLoaded(String modId);

    /** The game's {@code config/} directory. */
    Path configDir();

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client to server; a no-op on a dedicated server. */
    void sendToServer(CustomPacketPayload payload);

    /** A menu type whose client-side menu is built from opening data sent with the open packet. */
    <T extends AbstractContainerMenu, D> MenuType<T> extendedMenuType(
            ExtendedMenuFactory<T, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec);

    /** Opens {@code provider}'s menu, sending its opening data. */
    <D> void openExtendedMenu(ServerPlayer player, ExtendedMenuProvider<D> provider);

    @FunctionalInterface
    interface ExtendedMenuFactory<T extends AbstractContainerMenu, D> {
        T create(int syncId, Inventory inventory, D data);
    }
}
