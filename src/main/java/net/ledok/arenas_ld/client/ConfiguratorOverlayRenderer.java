package net.ledok.arenas_ld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.item.SpawnerConfiguratorItem;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.SpawnerSelectionDataComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Draws outline boxes for the spawner currently selected by a held Spawner Configurator
 * and for each of that spawner's configured spawn positions. Client-side only; reads the
 * selection from the held item's data component and the spawn offsets from the synced
 * block entity.
 */
public final class ConfiguratorOverlayRenderer {
    private static final float[] SPAWNER_COLOR = {0.96f, 0.69f, 0.26f}; // gold
    private static final float[] POSITION_COLOR = {0.53f, 0.83f, 0.42f}; // green
    private static final float ALPHA = 0.9f;

    private ConfiguratorOverlayRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ConfiguratorOverlayRenderer::render);
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        ItemStack configurator = findConfigurator(player);
        if (configurator == null) {
            return;
        }

        SpawnerSelectionDataComponent data = configurator.getOrDefault(
            DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
        Optional<BlockPos> posOpt = data.selectedSpawnerPos();
        if (posOpt.isEmpty()) {
            return;
        }
        Optional<ResourceKey<Level>> dimOpt = data.selectedSpawnerDimension();
        if (dimOpt.isPresent() && !dimOpt.get().equals(mc.level.dimension())) {
            return;
        }

        BlockPos spawnerPos = posOpt.get();
        BlockEntity be = mc.level.getBlockEntity(spawnerPos);
        if (!(be instanceof MobSpawnerBlockEntity spawner)) {
            return;
        }

        MultiBufferSource consumers = context.consumers();
        if (consumers == null) {
            return;
        }
        VertexConsumer lines = consumers.getBuffer(RenderType.lines());
        Vec3 cam = context.camera().getPosition();

        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        LevelRenderer.renderLineBox(poseStack, lines, new AABB(spawnerPos),
            SPAWNER_COLOR[0], SPAWNER_COLOR[1], SPAWNER_COLOR[2], ALPHA);

        for (BlockPos offset : spawner.getEntityDefinition().spawnOffsets()) {
            BlockPos cell = spawnerPos.offset(offset);
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(cell),
                POSITION_COLOR[0], POSITION_COLOR[1], POSITION_COLOR[2], ALPHA);
        }

        poseStack.popPose();

        if (consumers instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(RenderType.lines());
        }
    }

    private static ItemStack findConfigurator(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof SpawnerConfiguratorItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof SpawnerConfiguratorItem) {
            return off;
        }
        return null;
    }
}
