package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.registry.RegistryBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(ArenasLdMod.MOD_ID)
public final class ArenasLdModNeoForge {
    private boolean contentRegistered;

    public ArenasLdModNeoForge(IEventBus modBus) {
        // Registries are frozen during mod construction on NeoForge, and vanilla
        // Item/Block constructors create intrusive holders that assert this. All
        // registries are unfrozen for the duration of the RegisterEvent phase
        // (GameData.postRegisterEvents), so the content classes are class-loaded
        // on the FIRST RegisterEvent; the sink queues their registrations and
        // drains each registry's entries when its event fires.
        NeoForgeRegistrySink registrySink = new NeoForgeRegistrySink();
        RegistryBridge.setSink(registrySink);

        ArenasLdMod.initRuntime();

        modBus.addListener((net.neoforged.neoforge.registries.RegisterEvent event) -> {
            if (!contentRegistered) {
                contentRegistered = true;
                ArenasLdMod.registerContent();
            }
            registrySink.onRegister(event);
        });
        NeoForgeLivingEntityHandlers.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ArenasLdClientNeoForge.register(modBus);
        }
    }
}
