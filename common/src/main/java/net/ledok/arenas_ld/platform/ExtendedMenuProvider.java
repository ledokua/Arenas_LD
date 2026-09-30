package net.ledok.arenas_ld.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

/**
 * A menu that ships opening data to the client with the open packet (Fabric's
 * ExtendedScreenHandlerFactory, NeoForge's openMenu extra-data writer). Open it through
 * {@link ArenasMenus#open}, never plain {@code player.openMenu}: that would send no data and the
 * client-side menu type could not build its menu.
 */
public interface ExtendedMenuProvider<D> extends MenuProvider {
    D getScreenOpeningData(ServerPlayer player);

    /** Must match the codec its menu type was created with. */
    StreamCodec<? super RegistryFriendlyByteBuf, D> openingDataCodec();
}
