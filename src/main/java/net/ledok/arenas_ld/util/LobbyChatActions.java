package net.ledok.arenas_ld.util;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Builds clickable chat components for lobby actions. Chat clicks can only run commands, so each
 * button targets the player-usable {@code /arenasld lobby …} subcommands, baking the controller's
 * dimension + position (and lobby id where needed) into the command string. Shared across
 * dungeon/raid/arena chat feedback.
 */
public final class LobbyChatActions {
    private LobbyChatActions() {
    }

    private static String dimAndPos(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    public static String readyCommand(ServerLevel level, BlockPos pos) {
        return "/arenasld lobby ready " + dimAndPos(level, pos);
    }

    public static String startCommand(ServerLevel level, BlockPos pos) {
        return "/arenasld lobby start " + dimAndPos(level, pos);
    }

    public static String openCommand(ServerLevel level, BlockPos pos) {
        return "/arenasld lobby open " + dimAndPos(level, pos);
    }

    public static String acceptCommand(ServerLevel level, BlockPos pos, UUID lobbyId) {
        return "/arenasld lobby accept " + dimAndPos(level, pos) + " " + lobbyId;
    }

    public static String declineCommand(ServerLevel level, BlockPos pos, UUID lobbyId) {
        return "/arenasld lobby decline " + dimAndPos(level, pos) + " " + lobbyId;
    }

    /** A clickable "[label]" chat button that runs the given command, with a hover tooltip. */
    public static MutableComponent button(Component label, int rgb, String command, Component hover) {
        return Component.literal("[").append(label).append("]").withStyle(style -> style
            .withColor(rgb)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }
}
