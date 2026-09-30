package net.ledok.arenas_ld.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ledok.arenas_ld.client.ArenasClientEvents.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Flickering red wireframe boxes over upcoming mob spawn positions, driven by
 * {@link SpawnTelegraphStore} (fed by SpawnTelegraphPayload during a room's grace delay
 * and between waves). Same through-wall rendering as SelectionOverlayRenderer, and literally its pass:
 * the outline mesh needs a matching mode, vertex format, shader and line width to come out thick rather
 * than hairline, so the whole recipe lives in one place and this draws through it. Outlines only — a
 * telegraph marks a point a mob will appear at, not a volume, and a tint on every one of them would be
 * the thing a builder sees instead of the room.
 */
public final class SpawnTelegraphRenderer {
    private static final float[] TELEGRAPH_COLOR = {0.95f, 0.12f, 0.12f};

    private SpawnTelegraphRenderer() {
    }

    public static void register() {
        ArenasClientEvents.AFTER_TRANSLUCENT.add(SpawnTelegraphRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<BlockPos> positions = SpawnTelegraphStore.active(mc.level.getGameTime());
        if (positions.isEmpty()) {
            return;
        }

        // Flicker: oscillate alpha a few times per second.
        float alpha = 0.30f + 0.65f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 120.0));

        Vec3 cam = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = poseStack.last().pose();

        SelectionOverlayRenderer.beginOverlayPass();
        try {
            BufferBuilder buffer = SelectionOverlayRenderer.beginLines();
            for (BlockPos pos : positions) {
                SelectionOverlayRenderer.addBoxEdges(buffer, matrix, pos, TELEGRAPH_COLOR, alpha);
            }
            SelectionOverlayRenderer.drawMesh(buffer);
        } finally {
            // Unconditional: the pass saves the fog start into a static, so a throw in between would not
            // just leak depth and blend state for the frame, it would poison the next frame's save.
            SelectionOverlayRenderer.endOverlayPass();
        }
        poseStack.popPose();
    }
}
