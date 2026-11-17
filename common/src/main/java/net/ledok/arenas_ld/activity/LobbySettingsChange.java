package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.rewards.RunModifierCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The owner changed a lobby's settings while others are in it (LeDok, UI rework D2: settings stay editable).
 * <ul>
 *   <li>The other members get a line naming what changed: tier, hardcore, modifiers on/off, visibility.</li>
 *   <li>A change to what the run will be (tier, hardcore, modifiers) un-readies everyone but the owner, so nobody
 *       starts on settings they didn't see. Visibility only notifies.</li>
 * </ul>
 */
public final class LobbySettingsChange {
    private LobbySettingsChange() {
    }

    /** The lobby to store after {@code before} → {@code after}: readies cleared if needed; members told. */
    public static Lobby apply(MinecraftServer server, Lobby before, Lobby after) {
        List<Component> changes = new ArrayList<>();
        boolean runChanged = false;
        if (before.selectedTier() != after.selectedTier()) {
            changes.add(Component.translatable("message.arenas_ld.lobby.changed.tier",
                Component.translatable("gui.arenas_ld.tier." + after.selectedTier().name().toLowerCase(Locale.ROOT))));
            runChanged = true;
        }
        if (before.hardcoreEnabled() != after.hardcoreEnabled()) {
            changes.add(Component.translatable(after.hardcoreEnabled()
                ? "message.arenas_ld.lobby.changed.hardcore_on" : "message.arenas_ld.lobby.changed.hardcore_off"));
            runChanged = true;
        }
        for (String id : after.modifiers()) {
            if (!before.modifiers().contains(id)) {
                changes.add(Component.literal("+").append(RunModifierCatalog.displayName(id)));
                runChanged = true;
            }
        }
        for (String id : before.modifiers()) {
            if (!after.modifiers().contains(id)) {
                changes.add(Component.literal("−").append(RunModifierCatalog.displayName(id)));
                runChanged = true;
            }
        }
        if (before.visibility() != after.visibility()) {
            changes.add(Component.translatable("message.arenas_ld.lobby.changed.visibility",
                Component.translatable("gui.arenas_ld.dungeon_controller.visibility."
                    + after.visibility().name().toLowerCase(Locale.ROOT))));
        }
        if (before.minLevel() != after.minLevel()) {
            changes.add(Component.translatable("message.arenas_ld.lobby.changed.min_level", after.minLevel()));
        }
        if (changes.isEmpty() || after.members().size() < 2) {
            return after;
        }
        Lobby result = after;
        if (runChanged) {
            for (UUID member : after.members()) {
                if (!member.equals(after.ownerUuid()) && result.readyMembers().contains(member)) {
                    result = result.withUnready(member);
                }
            }
        }
        MutableComponent list = Component.empty();
        for (int i = 0; i < changes.size(); i++) {
            if (i > 0) {
                list.append(" · ");
            }
            list.append(changes.get(i));
        }
        Component line = Component.translatable(runChanged
                ? "message.arenas_ld.lobby.changed_unready" : "message.arenas_ld.lobby.changed",
            after.ownerName(), list).withStyle(ChatFormatting.GOLD);
        for (UUID member : after.members()) {
            if (member.equals(after.ownerUuid())) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(line);
            }
        }
        return result;
    }
}
