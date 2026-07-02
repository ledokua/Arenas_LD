package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.client.ArenasLdClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(ArenasLdMod.MOD_ID)
public final class ArenasLdModNeoForge {
    public ArenasLdModNeoForge(IEventBus modBus) {
        ArenasLdMod.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(ArenasLdClient::init));
        }
    }
}
