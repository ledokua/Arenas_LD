package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.MoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.RemoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.SetCloseTimerSecondsPayload;
import net.ledok.arenas_ld.dungeon.packet.SetCooldownTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetInviteExpiryTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetMaxPartySizePayload;
import net.ledok.arenas_ld.dungeon.packet.SetTierConfigPayload;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DungeonControllerAdminScreen extends AbstractContainerScreen<DungeonControllerAdminScreenHandler> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 230;

    private enum Tab { INSTANCES, GENERAL, NORMAL, HARD, HELL }

    private final List<net.minecraft.core.BlockPos> instances;
    private final Set<net.minecraft.core.BlockPos> activeRuns;
    private final Map<net.minecraft.core.BlockPos, Integer> cooldowns;
    private final Set<net.minecraft.core.BlockPos> pendingRemovals;
    private final Map<DifficultyTier, TierConfig> tierConfigs;
    private final Map<DifficultyTier, List<LeaderboardEntry>> leaderboards;
    private Tab currentTab = Tab.INSTANCES;

    private EditBox cooldownField;
    private EditBox closeTimerField;
    private EditBox maxPartyField;
    private EditBox inviteExpiryField;

    private EditBox tierHealthField;
    private EditBox tierDamageField;
    private EditBox tierLootField;
    private EditBox tierTimeField;

    public DungeonControllerAdminScreen(DungeonControllerAdminScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = this.imageHeight + 1000;
        this.instances = new ArrayList<>(handler.getInstances());
        this.activeRuns = handler.getActiveRunInstances();
        this.cooldowns = handler.getInstanceCooldownTimers();
        this.pendingRemovals = handler.getPendingRemovals();
        this.tierConfigs = new EnumMap<>(handler.getTierConfigs());
        this.leaderboards = new EnumMap<>(handler.getTopLeaderboards());
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int x = leftPos;
        int y = topPos;

        addTabButton(x + 8, y + 24, Tab.INSTANCES, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.instances"));
        addTabButton(x + 70, y + 24, Tab.GENERAL, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.general"));
        addTabButton(x + 132, y + 24, Tab.NORMAL, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.normal"));
        addTabButton(x + 194, y + 24, Tab.HARD, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.hard"));
        addTabButton(x + 256, y + 24, Tab.HELL, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.hell"));

        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
            .bounds(x + WIDTH - 22, y + 6, 16, 14).build());

        switch (currentTab) {
            case INSTANCES -> buildInstancesTab(x, y);
            case GENERAL -> buildGeneralTab(x, y);
            case NORMAL -> buildTierTab(x, y, DifficultyTier.NORMAL);
            case HARD -> buildTierTab(x, y, DifficultyTier.HARD);
            case HELL -> buildTierTab(x, y, DifficultyTier.HELL);
        }
    }

    private void addTabButton(int x, int y, Tab tab, Component label) {
        Button button = addRenderableWidget(Button.builder(label, b -> {
            currentTab = tab;
            rebuildWidgets();
        }).bounds(x, y, 60, 18).build());
        button.active = currentTab != tab;
    }

    private void buildInstancesTab(int x, int y) {
        int rowY = y + 72;
        for (int i = 0; i < instances.size() && i < 7; i++) {
            final int index = i;
            final net.minecraft.core.BlockPos pos = instances.get(i);
            addRenderableWidget(Button.builder(Component.literal("^"), b -> {
                int target = Math.max(0, index - 1);
                if (target != index) {
                    ClientPlayNetworking.send(new MoveDungeonInstancePayload(menu.getBlockPos(), index, target));
                    net.minecraft.core.BlockPos moved = instances.remove(index);
                    instances.add(target, moved);
                    rebuildWidgets();
                }
            }).bounds(x + WIDTH - 56, rowY - 2, 14, 14).build()).active = index > 0;
            addRenderableWidget(Button.builder(Component.literal("v"), b -> {
                int target = Math.min(instances.size() - 1, index + 1);
                if (target != index) {
                    ClientPlayNetworking.send(new MoveDungeonInstancePayload(menu.getBlockPos(), index, target));
                    net.minecraft.core.BlockPos moved = instances.remove(index);
                    instances.add(target, moved);
                    rebuildWidgets();
                }
            }).bounds(x + WIDTH - 40, rowY - 2, 14, 14).build()).active = index < instances.size() - 1;
            addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                ClientPlayNetworking.send(new RemoveDungeonInstancePayload(menu.getBlockPos(), pos));
                instances.remove(index);
                rebuildWidgets();
            }).bounds(x + WIDTH - 24, rowY - 2, 14, 14).build());
            rowY += 18;
        }
    }

    private void buildGeneralTab(int x, int y) {
        cooldownField = new EditBox(font, x + 120, y + 58, 60, 16, Component.empty());
        cooldownField.setValue(Integer.toString(menu.getCooldownTicks() / 20));
        addRenderableWidget(cooldownField);
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b -> {
            Integer value = parseInt(cooldownField);
            if (value != null) {
                ClientPlayNetworking.send(new SetCooldownTicksPayload(menu.getBlockPos(), value * 20));
            }
        }).bounds(x + 186, y + 57, 54, 18).build());

        closeTimerField = new EditBox(font, x + 120, y + 84, 60, 16, Component.empty());
        closeTimerField.setValue(Integer.toString(menu.getCloseTimerSeconds()));
        addRenderableWidget(closeTimerField);
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b -> {
            Integer value = parseInt(closeTimerField);
            if (value != null) {
                ClientPlayNetworking.send(new SetCloseTimerSecondsPayload(menu.getBlockPos(), value));
            }
        }).bounds(x + 186, y + 83, 54, 18).build());

        maxPartyField = new EditBox(font, x + 120, y + 110, 60, 16, Component.empty());
        maxPartyField.setValue(Integer.toString(menu.getMaxPartySize()));
        addRenderableWidget(maxPartyField);
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b -> {
            Integer value = parseInt(maxPartyField);
            if (value != null) {
                ClientPlayNetworking.send(new SetMaxPartySizePayload(menu.getBlockPos(), value));
            }
        }).bounds(x + 186, y + 109, 54, 18).build());

        inviteExpiryField = new EditBox(font, x + 120, y + 136, 60, 16, Component.empty());
        inviteExpiryField.setValue(Integer.toString(menu.getInviteExpiryTicks() / 20));
        addRenderableWidget(inviteExpiryField);
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b -> {
            Integer value = parseInt(inviteExpiryField);
            if (value != null) {
                ClientPlayNetworking.send(new SetInviteExpiryTicksPayload(menu.getBlockPos(), value * 20));
            }
        }).bounds(x + 186, y + 135, 54, 18).build());
    }

    private void buildTierTab(int x, int y, DifficultyTier tier) {
        TierConfig config = tierConfigs.getOrDefault(tier, TierConfig.defaultFor(tier));

        tierHealthField = new EditBox(font, x + 140, y + 58, 86, 16, Component.empty());
        tierHealthField.setValue(Double.toString(config.healthMultiplier()));
        addRenderableWidget(tierHealthField);

        tierDamageField = new EditBox(font, x + 140, y + 78, 86, 16, Component.empty());
        tierDamageField.setValue(Double.toString(config.damageMultiplier()));
        addRenderableWidget(tierDamageField);

        tierLootField = new EditBox(font, x + 100, y + 98, 170, 16, Component.empty());
        tierLootField.setValue(config.perPlayerLootTable());
        addRenderableWidget(tierLootField);

        tierTimeField = new EditBox(font, x + 140, y + 118, 86, 16, Component.empty());
        tierTimeField.setValue(Integer.toString(config.dungeonTimeSeconds()));
        addRenderableWidget(tierTimeField);

        addRenderableWidget(Button.builder(
            Component.translatable(config.hardcoreDefault()
                ? "gui.arenas_ld.dungeon_controller_admin.hardcore.on"
                : "gui.arenas_ld.dungeon_controller_admin.hardcore.off"),
            b -> {
                TierConfig current = tierConfigs.getOrDefault(tier, TierConfig.defaultFor(tier));
                TierConfig updated = new TierConfig(
                    current.healthMultiplier(),
                    current.damageMultiplier(),
                    current.perPlayerLootTable(),
                    current.dungeonTimeSeconds(),
                    !current.hardcoreDefault()
                );
                tierConfigs.put(tier, updated);
                rebuildWidgets();
            }
        ).bounds(x + 140, y + 138, 120, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller_admin.button.apply_all"), b -> {
            Double health = parseDouble(tierHealthField);
            Double damage = parseDouble(tierDamageField);
            Integer seconds = parseInt(tierTimeField);
            if (health == null || damage == null || seconds == null) {
                return;
            }
            TierConfig current = tierConfigs.getOrDefault(tier, TierConfig.defaultFor(tier));
            TierConfig updated = new TierConfig(
                health,
                damage,
                tierLootField.getValue(),
                seconds,
                current.hardcoreDefault()
            );
            tierConfigs.put(tier, updated);
            ClientPlayNetworking.send(new SetTierConfigPayload(menu.getBlockPos(), tier, updated));
        }).bounds(x + 272, y + 118, 58, 36).build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF121620);
        guiGraphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 20, 0xFF1A2130);
        guiGraphics.fill(x + 6, y + 46, x + WIDTH - 6, y + HEIGHT - 8, 0xFF0E1320);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;
        guiGraphics.drawString(font, title, x + 8, y + 8, 0xFFFFFF, false);

        switch (currentTab) {
            case INSTANCES -> renderInstancesText(guiGraphics, x, y);
            case GENERAL -> renderGeneralText(guiGraphics, x, y);
            case NORMAL -> renderTierTab(guiGraphics, x, y, DifficultyTier.NORMAL);
            case HARD -> renderTierTab(guiGraphics, x, y, DifficultyTier.HARD);
            case HELL -> renderTierTab(guiGraphics, x, y, DifficultyTier.HELL);
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderInstancesText(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.drawString(font,
            Component.translatable("gui.arenas_ld.dungeon_controller_admin.active_runs_count", activeRuns.size(), instances.size()),
            x + 8,
            y + 50,
            0xC0C8E0,
            false);

        int rowY = y + 72;
        for (int i = 0; i < instances.size() && i < 7; i++) {
            net.minecraft.core.BlockPos pos = instances.get(i);
            Component status;
            int color;
            if (pendingRemovals.contains(pos)) {
                status = Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.pending_removal");
                color = ChatFormatting.RED.getColor();
            } else if (activeRuns.contains(pos)) {
                status = Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.running");
                color = 0x66DD66;
            } else if (cooldowns.containsKey(pos)) {
                status = Component.translatable(
                    "gui.arenas_ld.dungeon_controller_admin.instance_status.cooldown",
                    formatTicks(cooldowns.get(pos))
                );
                color = 0xE8CC66;
            } else {
                status = Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.idle");
                color = 0xA0A0A0;
            }
            guiGraphics.drawString(font, (i + 1) + ". " + pos.toShortString(), x + 10, rowY, 0xE0E0E0, false);
            guiGraphics.drawString(font, status, x + 150, rowY, color, false);
            rowY += 18;
        }

        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.use_linker_hint"), x + 10, y + HEIGHT - 18, 0x808AA6, false);
    }

    private void renderGeneralText(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.label.cooldown"), x + 10, y + 62, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.label.close_timer"), x + 10, y + 88, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.label.max_party"), x + 10, y + 114, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.label.invite_expiry"), x + 10, y + 140, 0xC0C8E0, false);
    }

    private void renderTierTab(GuiGraphics guiGraphics, int x, int y, DifficultyTier tier) {
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tier_header", tier.name()), x + 10, y + 50, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tier.health"), x + 10, y + 62, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tier.damage"), x + 10, y + 82, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tier.loot"), x + 10, y + 102, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tier.time"), x + 10, y + 122, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tier.hardcore"), x + 10, y + 142, 0xC0C8E0, false);

        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.leaderboard"), x + 10, y + 162, 0xC0C8E0, false);
        List<LeaderboardEntry> top10 = leaderboards.getOrDefault(tier, List.of()).stream()
            .sorted(Comparator.comparingInt(LeaderboardEntry::timeSeconds))
            .limit(10)
            .toList();

        if (top10.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.leaderboard.empty"), x + 10, y + 174, 0x808AA6, false);
            return;
        }

        int rowY = y + 174;
        for (int i = 0; i < top10.size() && i < 4; i++) {
            LeaderboardEntry entry = top10.get(i);
            guiGraphics.drawString(font,
                Component.literal((i + 1) + ". " + entry.playerName() + " - " + entry.timeSeconds() + "s"),
                x + 10,
                rowY,
                0xE0E0E0,
                false
            );
            rowY += 12;
        }
    }

    private Integer parseInt(EditBox field) {
        try {
            return Integer.parseInt(field.getValue());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double parseDouble(EditBox field) {
        try {
            return Double.parseDouble(field.getValue());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        int rem = seconds % 60;
        return String.format("%d:%02d", minutes, rem);
    }
}
