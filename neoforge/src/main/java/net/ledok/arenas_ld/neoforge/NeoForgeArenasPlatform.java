package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.platform.ArenasPlatform;
import net.ledok.arenas_ld.platform.ExtendedMenuProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;

public final class NeoForgeArenasPlatform implements ArenasPlatform {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (FMLEnvironment.dist.isClient()) {
            PacketDistributor.sendToServer(payload);
        }
    }

    @Override
    public <T extends AbstractContainerMenu, D> MenuType<T> extendedMenuType(
            ExtendedMenuFactory<T, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        return IMenuTypeExtension.create((syncId, inventory, buf) -> factory.create(syncId, inventory, codec.decode(buf)));
    }

    @Override
    public <D> void openExtendedMenu(ServerPlayer player, ExtendedMenuProvider<D> provider) {
        D data = provider.getScreenOpeningData(player);
        player.openMenu(provider, buf -> provider.openingDataCodec().encode(buf, data));
    }
}
