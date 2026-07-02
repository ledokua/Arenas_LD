package net.ledok.arenas_ld.neoforge;

import net.ledok.arenas_ld.registry.RegistryBridge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects registrations made during mod construction (registries are frozen at
 * that point on NeoForge) and drains them into the matching {@link RegisterEvent}.
 */
final class NeoForgeRegistrySink implements RegistryBridge.Sink {
    private record Queued(ResourceLocation id, Object value) {}

    private final Map<ResourceKey<? extends Registry<?>>, List<Queued>> queue = new HashMap<>();

    @Override
    public <T> void register(Registry<T> registry, ResourceLocation id, T value) {
        queue.computeIfAbsent(registry.key(), k -> new ArrayList<>()).add(new Queued(id, value));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void onRegister(RegisterEvent event) {
        List<Queued> entries = queue.remove(event.getRegistryKey());
        if (entries == null) return;
        for (Queued entry : entries) {
            event.register((ResourceKey) event.getRegistryKey(), entry.id(), entry::value);
        }
    }
}
