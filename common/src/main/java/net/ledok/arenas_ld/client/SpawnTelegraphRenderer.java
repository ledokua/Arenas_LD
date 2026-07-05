package net.ledok.arenas_ld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Flickering red wireframe boxes over upcoming mob spawn positions, driven by
 * {@link SpawnTelegraphStore} (fed by SpawnTelegraphPayload during a room's grace delay
 * and between waves). Same through-wall rendering as SelectionOverlayRenderer.
 */
public final class SpawnTelegraphRenderer {
    private static final float[] TELEGRAPH_COLOR = {0.95f, 0.12f, 0.12f};

    private SpawnTelegraphRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SpawnTelegraphRenderer::render);
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

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance()
            .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (BlockPos pos : positions) {
            SelectionOverlayRenderer.addBoxEdges(buffer, matrix, pos, TELEGRAPH_COLOR, alpha);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }
}
