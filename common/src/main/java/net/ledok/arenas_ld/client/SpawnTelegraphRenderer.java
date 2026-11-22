package net.ledok.arenas_ld.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ledok.arenas_ld.client.ArenasClientEvents.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.ledok.arenas_ld.packet.RunHudPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
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

    private static final float[] ZONE_COLOR = {0.35f, 1.0f, 0.45f};
    private static @Nullable RunHudPayload.GoalZone goalZone;
    private static @Nullable ClientLevel goalLevel;
    private static long goalExpires;

    public static void onRunHud(RunHudPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        goalZone = payload.goalZone();
        goalLevel = goalZone == null ? null : level;
        goalExpires = level == null ? 0 : level.getGameTime() + 60;
    }

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
        var zone = goalLevel == mc.level && mc.level.getGameTime() <= goalExpires ? goalZone : null;
        if (zone != null && !zone.dimension().equals(mc.level.dimension())) zone = null;
        if (positions.isEmpty() && zone == null) {
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
            if (zone != null) {
                SelectionOverlayRenderer.addBoundsEdges(buffer, matrix, zone.min(), zone.max(), ZONE_COLOR, 0.75f, 0.01f);
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
