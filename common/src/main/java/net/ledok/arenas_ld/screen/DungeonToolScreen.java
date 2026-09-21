package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.item.DungeonToolItem;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.DungeonToolDataComponent;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.presentation.CanvasScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The Dungeon Tool's mode picker, opened by RMB in air. Client-only (no menu): picking a mode
 * sends {@link ModPackets.SetDungeonToolModePayload} for the hand that opened the screen and
 * closes. The active mode is marked; shift+scroll cycling still works for muscle memory.
 */
public class DungeonToolScreen extends CanvasScreen {

    private static final float W = 210;
    private static final float H = 229;

    private final InteractionHand hand;

    public DungeonToolScreen(InteractionHand hand) {
        super(Component.translatable("screen.arenas_ld.dungeon_tool.title"),
            VectorCanvas.create(W, H), Placement.Screen.center());
        this.hand = hand;
        canvas.theme(ArenasUi.THEME);
        buildUi();
    }

    private void buildUi() {
        DungeonToolItem.Mode current = currentMode();

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

    private DungeonToolItem.Mode currentMode() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return DungeonToolItem.Mode.LINK;
        }
        ItemStack stack = player.getItemInHand(hand);
        DungeonToolDataComponent data = stack.getOrDefault(
            DataComponentRegistry.DUNGEON_TOOL_DATA, DungeonToolDataComponent.DEFAULT);
        return DungeonToolItem.selectedMode(data);
    }
}
