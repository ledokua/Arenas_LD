package net.ledok.arenas_ld.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

/**
 * Line render type that ignores the depth buffer so overlay outlines stay visible through walls.
 * Extends RenderStateShard only to reach the protected state shards used to build the composite
 * state; the type itself is never instantiated.
 */
public final class OverlayRenderType extends RenderStateShard {
    public static final RenderType LINES_THROUGH_WALLS = RenderType.create(
        "arenas_ld_overlay_lines",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        1536,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_LINES_SHADER)
            .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setOutputState(ITEM_ENTITY_TARGET)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setDepthTestState(NO_DEPTH_TEST)
            .createCompositeState(false)
    );

    private OverlayRenderType(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }
}
