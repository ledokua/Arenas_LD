package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.item.DungeonToolItem;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.DungeonToolDataComponent;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.presentation.CanvasScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Dungeon Tool's mode picker, opened by RMB in air. Client-only (no menu): picking a mode
 * sends {@link ModPackets.SetDungeonToolModePayload} for the hand that opened the screen and
 * closes. The active mode is marked; shift+scroll cycling still works for muscle memory.
 *
 * <p>When the tool's source is a room controller, the screen also lists the room's linked
 * spawners (by their builder-given name when they have one). Picking one makes it the room's
 * acting spawner and switches the tool to MOB_SPAWN_POSITION, so world clicks edit that
 * spawner's spawn positions without walking over to it; the gear button opens the spawner's own
 * settings screen, as if the block were right-clicked.
 */
public class DungeonToolScreen extends CanvasScreen {

    private static final float W = 210;
    private static final float H = 229;
    private static final int SPAWNER_ROW_H = 18;
    private static final int SPAWNER_ROW_GAP = 3;
    private static final int SPAWNER_MAX_VISIBLE = 6;

    private final InteractionHand hand;

    public DungeonToolScreen(InteractionHand hand) {
        super(Component.translatable("screen.arenas_ld.dungeon_tool.title"),
            VectorCanvas.create(W, computeHeight(hand)), Placement.Screen.center());
        this.hand = hand;
        canvas.theme(ArenasUi.THEME);
        buildUi();
    }

    /** Vanilla-like at any GUI scale: 1:1 when the menu fits the window, shrunk to fit when not
     *  (this is the one fixed-size screen — the container screens reflow via fillWindow). */
    @Override
    protected void init() {
        super.init();
        placement(ArenasUi.fitCenter(canvas.width(), canvas.height(), width, height));
    }

    private void buildUi() {
        DungeonToolItem.Mode current = DungeonToolItem.selectedMode(currentData(hand));

        Flex root = canvas.add(Flex.column().gap(5).padding(Insets.of(10)));
        root.sizing(Sizing.fill(), Sizing.fill());
        root.background(ArenasUi.flatOutline(ArenasUi.PANEL, ArenasUi.HAIRLINE));

        root.item(ArenasUi.text(Component.translatable("screen.arenas_ld.dungeon_tool.title"), ArenasUi.INK));

        root.item(ArenasUi.sectionHeader(Component.translatable("screen.arenas_ld.dungeon_tool.section.link")));
        root.item(modeButton(DungeonToolItem.Mode.LINK, current));

        root.item(ArenasUi.sectionHeader(Component.translatable("screen.arenas_ld.dungeon_tool.section.doors")));
        root.item(modeButton(DungeonToolItem.Mode.ROOM_DOOR, current));

        root.item(ArenasUi.sectionHeader(Component.translatable("screen.arenas_ld.dungeon_tool.section.positions")));
        root.item(modeButton(DungeonToolItem.Mode.MOB_SPAWN_POSITION, current));
        root.item(modeButton(DungeonToolItem.Mode.ENTRANCE_POSITION, current));
        root.item(modeButton(DungeonToolItem.Mode.RESPAWN_POSITION, current));
        root.item(modeButton(DungeonToolItem.Mode.PROTECT_POSITION, current));

        buildSpawnerSection(root);
    }

    private Button modeButton(DungeonToolItem.Mode mode, DungeonToolItem.Mode current) {
        Component label = mode == current
            ? Component.literal("▶ ").append(mode.getName())
            : mode.getName();
        return new Button(W - 20, 18, label, () -> {
            ClientPlayNetworking.send(new ModPackets.SetDungeonToolModePayload(
                mode.ordinal(), hand == InteractionHand.MAIN_HAND));
            onClose();
        });
    }

    // ---- Room spawner list ----

    private void buildSpawnerSection(Flex root) {
        List<BlockPos> spawners = roomSpawners(hand);
        if (spawners == null) {
            return;
        }
        root.item(ArenasUi.sectionHeader(Component.translatable("screen.arenas_ld.dungeon_tool.section.spawners")));

        Flex list = Flex.column().gap(SPAWNER_ROW_GAP);
        list.sizing(Sizing.fill(), Sizing.content());
        if (spawners.isEmpty()) {
            Flex emptyRow = Flex.row().alignItems(Align.CENTER);
            emptyRow.sizing(Sizing.fill(), Sizing.fixed(SPAWNER_ROW_H));
            emptyRow.item(ArenasUi.text(Component.translatable("screen.arenas_ld.dungeon_tool.no_spawners"), ArenasUi.INK_DIM));
            list.item(emptyRow);
        } else {
            BlockPos selected = currentData(hand).spawnerPos().orElse(null);
            for (BlockPos pos : spawners) {
                list.item(spawnerRow(pos, pos.equals(selected)));
            }
        }

        if (spawners.size() > SPAWNER_MAX_VISIBLE) {
            ScrollPanel scroll = new ScrollPanel(100, SPAWNER_ROW_H, list);
            scroll.sizing(Sizing.fill(), Sizing.fixed(visibleListHeight(spawners.size())));
            scroll.barWidth(4);
            scroll.wheelStep(SPAWNER_ROW_H + SPAWNER_ROW_GAP);
            root.item(scroll);
        } else {
            root.item(list);
        }
    }

    private Flex spawnerRow(BlockPos pos, boolean selected) {
        Flex row = Flex.row().gap(SPAWNER_ROW_GAP).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(SPAWNER_ROW_H));

        Component label = spawnerLabel(pos);
        if (selected) {
            label = Component.literal("▶ ").append(label);
        }
        Button pick = new Button(W - 20 - 21, SPAWNER_ROW_H, label, () -> {
            ClientPlayNetworking.send(new ModPackets.SetDungeonToolSpawnerPayload(
                pos, hand == InteractionHand.MAIN_HAND));
            onClose();
        });
        pick.sizing(Sizing.expand(), Sizing.fixed(SPAWNER_ROW_H));
        row.item(pick);

        row.item(countBadge(pos));

        // Opens the spawner's own settings screen, as if the block were right-clicked.
        row.item(new Button(18, SPAWNER_ROW_H, Component.literal("⚙"), () -> {
            ClientPlayNetworking.send(new ModPackets.OpenBlockMenuPayload(pos));
            onClose();
        }));
        return row;
    }

    /** How many spawn positions the spawner has placed; dim zero, "?" for an unloaded chunk. */
    private static Flex countBadge(BlockPos pos) {
        int count = spawnPositionCount(pos);
        Flex badge = Flex.row().alignItems(Align.CENTER).justify(net.ledok.vectorlib.client.canvas.layout.Justify.CENTER);
        badge.sizing(Sizing.fixed(20), Sizing.fixed(SPAWNER_ROW_H));
        badge.backgroundFill(ArenasUi.PANEL_2, ArenasUi.HAIRLINE, 1);
        String text = count < 0 ? "?" : String.valueOf(count);
        badge.item(ArenasUi.text(Component.literal(text), count > 0 ? ArenasUi.INK : ArenasUi.INK_DIM));
        return badge;
    }

    /** -1 when the spawner's chunk is not loaded client-side. */
    private static int spawnPositionCount(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        BlockEntity be = level == null ? null : level.getBlockEntity(pos);
        if (be instanceof MobSpawnerBlockEntity spawner) {
            return spawner.getEntityDefinition().spawnOffsets().size();
        }
        if (be instanceof DungeonBossSpawnerBlockEntity dbs) {
            return dbs.getEntityDefinition().spawnOffsets().size();
        }
        return -1;
    }

    private static Component spawnerLabel(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        BlockEntity be = level == null ? null : level.getBlockEntity(pos);
        if (be instanceof MobSpawnerBlockEntity spawner && !spawner.getSpawnerName().isEmpty()) {
            return Component.literal(spawner.getSpawnerName());
        }
        if (be instanceof DungeonBossSpawnerBlockEntity) {
            return Component.translatable("screen.arenas_ld.dungeon_tool.boss_spawner", pos.toShortString());
        }
        return Component.literal(pos.toShortString());
    }

    /** The linked spawners of the tool's selected room, or null when the source isn't a room here. */
    @Nullable
    private static List<BlockPos> roomSpawners(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        RoomControllerBlockEntity room = DungeonToolItem.sourceRoom(mc.level, currentData(hand));
        return room == null ? null : room.getSpawnerPositions();
    }

    private static float visibleListHeight(int spawnerCount) {
        int rows = Math.max(1, Math.min(spawnerCount, SPAWNER_MAX_VISIBLE));
        return rows * SPAWNER_ROW_H + (rows - 1) * SPAWNER_ROW_GAP;
    }

    /** The canvas is fixed at construction, so the spawner section's height is decided up front. */
    private static float computeHeight(InteractionHand hand) {
        List<BlockPos> spawners = roomSpawners(hand);
        if (spawners == null) {
            return H;
        }
        // gap + section header (one text line) + gap + the list itself.
        return H + 5 + 10 + 5 + visibleListHeight(spawners.size());
    }

    private static DungeonToolDataComponent currentData(InteractionHand hand) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return DungeonToolDataComponent.DEFAULT;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof DungeonToolItem)) {
            return DungeonToolDataComponent.DEFAULT;
        }
        return stack.getOrDefault(DataComponentRegistry.DUNGEON_TOOL_DATA, DungeonToolDataComponent.DEFAULT);
    }
}
