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
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.item.LinkerItem;
import net.ledok.arenas_ld.item.SpawnerConfiguratorItem;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.LinkerModeDataComponent;
import net.ledok.arenas_ld.util.SpawnerSelectionDataComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws through-wall outline boxes for the selection held in a Spawner Configurator or Linker:
 * a gold box on the selected/main block and green boxes on its linked targets. Client-side only;
 * reads the selection from the held item's data component and the linked positions from the synced
 * block entity at the selected position. Renders with the depth test disabled so the boxes stay
 * visible through terrain.
 */
public final class SelectionOverlayRenderer {
    private static final float[] MAIN_COLOR = {0.96f, 0.69f, 0.26f};   // gold
    private static final float[] TARGET_COLOR = {0.53f, 0.83f, 0.42f}; // green
    private static final float[] RESPAWN_COLOR = {0.30f, 0.80f, 0.90f}; // cyan
    private static final float ALPHA = 0.95f;

    private SelectionOverlayRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SelectionOverlayRenderer::render);
    }

    private record Box(BlockPos pos, float[] color) {
    }

    private static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        List<Box> boxes = collectBoxes(mc, player);
        if (boxes.isEmpty()) {
            return;
        }

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
        for (Box box : boxes) {
            addBoxEdges(buffer, matrix, box.pos(), box.color());
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

    private static void addBoxEdges(BufferBuilder buffer, Matrix4f matrix, BlockPos pos, float[] color) {
        float x0 = pos.getX();
        float y0 = pos.getY();
        float z0 = pos.getZ();
        float x1 = x0 + 1;
        float y1 = y0 + 1;
        float z1 = z0 + 1;
        float r = color[0];
        float g = color[1];
        float b = color[2];

        // bottom face
        edge(buffer, matrix, x0, y0, z0, x1, y0, z0, r, g, b);
        edge(buffer, matrix, x1, y0, z0, x1, y0, z1, r, g, b);
        edge(buffer, matrix, x1, y0, z1, x0, y0, z1, r, g, b);
        edge(buffer, matrix, x0, y0, z1, x0, y0, z0, r, g, b);
        // top face
        edge(buffer, matrix, x0, y1, z0, x1, y1, z0, r, g, b);
        edge(buffer, matrix, x1, y1, z0, x1, y1, z1, r, g, b);
        edge(buffer, matrix, x1, y1, z1, x0, y1, z1, r, g, b);
        edge(buffer, matrix, x0, y1, z1, x0, y1, z0, r, g, b);
        // verticals
        edge(buffer, matrix, x0, y0, z0, x0, y1, z0, r, g, b);
        edge(buffer, matrix, x1, y0, z0, x1, y1, z0, r, g, b);
        edge(buffer, matrix, x1, y0, z1, x1, y1, z1, r, g, b);
        edge(buffer, matrix, x0, y0, z1, x0, y1, z1, r, g, b);
    }

    private static void edge(BufferBuilder buffer, Matrix4f matrix,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float r, float g, float b) {
        buffer.addVertex(matrix, ax, ay, az).setColor(r, g, b, ALPHA);
        buffer.addVertex(matrix, bx, by, bz).setColor(r, g, b, ALPHA);
    }

    private static List<Box> collectBoxes(Minecraft mc, LocalPlayer player) {
        List<Box> boxes = new ArrayList<>();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        ItemStack configurator = main.getItem() instanceof SpawnerConfiguratorItem ? main
            : off.getItem() instanceof SpawnerConfiguratorItem ? off : null;
        if (configurator != null) {
            collectConfigurator(mc, configurator, boxes);
        }

        ItemStack linker = main.getItem() instanceof LinkerItem ? main
            : off.getItem() instanceof LinkerItem ? off : null;
        if (linker != null) {
            collectLinker(mc, linker, boxes);
        }

        return boxes;
    }

    private static void collectConfigurator(Minecraft mc, ItemStack stack, List<Box> boxes) {
        SpawnerSelectionDataComponent data = stack.getOrDefault(
            DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
        Optional<BlockPos> posOpt = data.selectedSpawnerPos();
        if (posOpt.isEmpty() || !dimensionMatches(mc, data.selectedSpawnerDimension())) {
            return;
        }
        BlockPos spawnerPos = posOpt.get();
        BlockEntity be = mc.level.getBlockEntity(spawnerPos);

        List<BlockPos> spawnOffsets = List.of();
        List<BlockPos> respawnOffsets = List.of();
        if (be instanceof MobSpawnerBlockEntity mobSpawner) {
            spawnOffsets = mobSpawner.getEntityDefinition().spawnOffsets();
        } else if (be instanceof DungeonBossSpawnerBlockEntity bossSpawner) {
            spawnOffsets = bossSpawner.getEntityDefinition().spawnOffsets();
        } else if (be instanceof net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity raidBossSpawner) {
            spawnOffsets = raidBossSpawner.getEntityDefinition().spawnOffsets();
            respawnOffsets = raidBossSpawner.getRespawnPointOffsets();
        } else if (be instanceof RoomControllerBlockEntity room) {
            BlockPos respawnOffset = room.getRespawnOffset();
            if (respawnOffset != null) {
                respawnOffsets = List.of(respawnOffset);
            }
        } else {
            return;
        }

        boxes.add(new Box(spawnerPos, MAIN_COLOR));
        for (BlockPos offset : spawnOffsets) {
            boxes.add(new Box(spawnerPos.offset(offset), TARGET_COLOR));
        }
        for (BlockPos offset : respawnOffsets) {
            boxes.add(new Box(spawnerPos.offset(offset), RESPAWN_COLOR));
        }
    }

    private static void collectLinker(Minecraft mc, ItemStack stack, List<Box> boxes) {
        LinkerModeDataComponent data = stack.getOrDefault(
            DataComponentRegistry.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
        Optional<BlockPos> posOpt = data.mainSpawnerPos();
        if (posOpt.isEmpty() || !dimensionMatches(mc, data.mainSpawnerDimension())) {
            return;
        }
        BlockPos mainPos = posOpt.get();
        BlockEntity be = mc.level.getBlockEntity(mainPos);
        if (be == null) {
            return;
        }

        LinkerItem.Mode[] modes = LinkerItem.Mode.values();
        LinkerItem.Mode mode = modes[Math.floorMod(data.mode(), modes.length)];
        List<BlockPos> targets = new ArrayList<>();
        switch (mode) {
            case CONTROLLER_INSTANCE -> {
                if (!(be instanceof DungeonControllerBlockEntity controller)) {
                    return;
                }
                targets.addAll(controller.getInstances());
            }
            case DBS_ROOM -> {
                if (!(be instanceof DungeonBossSpawnerBlockEntity dbs)) {
                    return;
                }
                targets.addAll(dbs.getRooms());
            }
            case ROOM_SPAWNER -> {
                if (!(be instanceof RoomControllerBlockEntity room)) {
                    return;
                }
                targets.addAll(room.getSpawnerPositions());
            }
            case ROOM_DOOR -> {
                if (!(be instanceof RoomControllerBlockEntity room)) {
                    return;
                }
                targets.addAll(room.getDoorPositions());
            }
        }

        boxes.add(new Box(mainPos, MAIN_COLOR));
        for (BlockPos target : targets) {
            boxes.add(new Box(target, TARGET_COLOR));
        }
    }

    private static boolean dimensionMatches(Minecraft mc, Optional<ResourceKey<Level>> dimension) {
        return dimension.isEmpty() || dimension.get().equals(mc.level.dimension());
    }
}
