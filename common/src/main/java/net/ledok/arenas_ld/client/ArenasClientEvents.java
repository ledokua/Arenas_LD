package net.ledok.arenas_ld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Loader-neutral client render hooks; client-only. Each loader's client glue fires them. */
public final class ArenasClientEvents {
    private ArenasClientEvents() {}

    /**
     * World-space context after translucent blocks. The pose stack is the level's (identity at
     * this stage); the camera rotation lives in RenderSystem's model-view matrix, so renderers
     * only translate by {@code -camera position}. Accessor names match Fabric's context.
     */
    public record WorldRenderContext(PoseStack matrixStack, Camera camera) {}

    public static final List<Consumer<WorldRenderContext>> AFTER_TRANSLUCENT = new CopyOnWriteArrayList<>();

    public static void fireAfterTranslucent(WorldRenderContext context) {
        for (Consumer<WorldRenderContext> listener : AFTER_TRANSLUCENT) {
            listener.accept(context);
        }
    }
}
