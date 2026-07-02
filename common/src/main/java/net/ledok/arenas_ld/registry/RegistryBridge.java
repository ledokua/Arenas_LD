package net.ledok.arenas_ld.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Loader-neutral registration entry point. Content classes register through this
 * instead of vanilla {@link Registry#register} so each platform can decide when
 * the actual registry insertion happens:
 * <ul>
 *   <li>Fabric: the default sink registers immediately (vanilla behaviour).</li>
 *   <li>NeoForge: the platform entrypoint swaps in a queueing sink before any
 *       content class is loaded, then drains the queue during RegisterEvent
 *       (registries are frozen outside that window).</li>
 * </ul>
 * Objects are still constructed eagerly in static initializers; only the
 * registry insertion is routed through the sink.
 */
public final class RegistryBridge {
    public interface Sink {
        <T> void register(Registry<T> registry, ResourceLocation id, T value);
    }

    private static Sink sink = new Sink() {
        @Override
        public <T> void register(Registry<T> registry, ResourceLocation id, T value) {
            Registry.register(registry, id, value);
        }
    };

    private RegistryBridge() {}

    /** Must be called by the platform entrypoint before any content class loads. */
    public static void setSink(Sink newSink) {
        sink = newSink;
    }

    public static <T, V extends T> V register(Registry<T> registry, ResourceLocation id, V value) {
        sink.register(registry, id, value);
        return value;
    }
}
