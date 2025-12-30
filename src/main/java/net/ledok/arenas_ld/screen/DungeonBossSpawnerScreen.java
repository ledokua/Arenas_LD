package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DungeonBossSpawnerScreen extends AbstractContainerScreen<DungeonBossSpawnerScreenHandler> {

    private EditBox mobIdField;
    private EditBox respawnTimeField;
    private EditBox dungeonCloseTimerField;
    private EditBox lootTableIdField;
    private EditBox perPlayerLootTableIdField;
    private EditBox exitPositionCoordsField;
    private EditBox triggerRadiusField;
    private EditBox battleRadiusField;
    private EditBox regenerationField;
    private EditBox enterPortalSpawnCoordsField;
    private EditBox enterPortalDestCoordsField;
    private EditBox skillExperienceField;
    private EditBox groupIdField;

    public DungeonBossSpawnerScreen(DungeonBossSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageHeight = 280;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 150;
        int fieldHeight = 20;
        int yOffset = 24;

        int col1X = (this.width / 2) - fieldWidth - 10;
        int y = 20;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.mob_id"), (button) -> {}, this.font));
        mobIdField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        mobIdField.setMaxLength(128);
        this.addRenderableWidget(mobIdField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.loot_table_id"), (button) -> {}, this.font));
        lootTableIdField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        lootTableIdField.setMaxLength(128);
        this.addRenderableWidget(lootTableIdField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.per_player_loot_table_id"), (button) -> {}, this.font));
        perPlayerLootTableIdField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        perPlayerLootTableIdField.setMaxLength(128);
        this.addRenderableWidget(perPlayerLootTableIdField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.group_id"), (button) -> {}, this.font));
        groupIdField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        groupIdField.setMaxLength(128);
        this.addRenderableWidget(groupIdField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.enter_portal_spawn"), (button) -> {}, this.font));
        enterPortalSpawnCoordsField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        enterPortalSpawnCoordsField.setMaxLength(32);
        this.addRenderableWidget(enterPortalSpawnCoordsField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.enter_portal_dest"), (button) -> {}, this.font));
        enterPortalDestCoordsField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        enterPortalDestCoordsField.setMaxLength(32);
        this.addRenderableWidget(enterPortalDestCoordsField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.exit_position"), (button) -> {}, this.font));
        exitPositionCoordsField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        exitPositionCoordsField.setMaxLength(32);
        this.addRenderableWidget(exitPositionCoordsField);
        y += yOffset * 1.7;

        int col2X = (this.width / 2) + 5;
        y = 20;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.respawn_time"), (button) -> {}, this.font));
        respawnTimeField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        respawnTimeField.setMaxLength(8);
        this.addRenderableWidget(respawnTimeField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.dungeon_close_timer"), (button) -> {}, this.font));
        dungeonCloseTimerField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        dungeonCloseTimerField.setMaxLength(8);
        this.addRenderableWidget(dungeonCloseTimerField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.trigger_radius"), (button) -> {}, this.font));
        triggerRadiusField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        triggerRadiusField.setMaxLength(4);
        this.addRenderableWidget(triggerRadiusField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.battle_radius"), (button) -> {}, this.font));
        battleRadiusField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        battleRadiusField.setMaxLength(4);
        this.addRenderableWidget(battleRadiusField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.regeneration"), (button) -> {}, this.font));
        regenerationField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        regenerationField.setMaxLength(4);
        this.addRenderableWidget(regenerationField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.skill_xp"), (button) -> {}, this.font));
        skillExperienceField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        skillExperienceField.setMaxLength(8);
        this.addRenderableWidget(skillExperienceField);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.attributes"), button -> {
            this.minecraft.setScreen(new MobAttributesScreen(new MobAttributesScreenHandler(menu.containerId, minecraft.player.getInventory(), new MobAttributesData(menu.blockEntity.getBlockPos())), minecraft.player.getInventory(), Component.translatable("gui.arenas_ld.boss_attributes")));
        }).bounds(this.width / 2 - 150, this.height - 70, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.equipment"), button -> {
            this.minecraft.setScreen(new EquipmentScreen(new EquipmentScreenHandler(menu.containerId, minecraft.player.getInventory(), new EquipmentScreenData(menu.blockEntity.getBlockPos())), minecraft.player.getInventory(), Component.translatable("gui.arenas_ld.boss_equipment")));
        }).bounds(this.width / 2 + 50, this.height - 70, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.save"), button -> onSave())
                .bounds(this.width / 2 - 50, this.height - 50, 100, 20)
                .build());

        loadBlockEntityData();
    }

    private void loadBlockEntityData() {
        if (menu.blockEntity != null) {
            mobIdField.setValue(menu.blockEntity.mobId);
            respawnTimeField.setValue(String.valueOf(menu.blockEntity.respawnTime));
            dungeonCloseTimerField.setValue(String.valueOf(menu.blockEntity.dungeonCloseTimer));
            lootTableIdField.setValue(menu.blockEntity.lootTableId);
            perPlayerLootTableIdField.setValue(menu.blockEntity.perPlayerLootTableId);
            exitPositionCoordsField.setValue(String.format("%d %d %d", menu.blockEntity.exitPositionCoords.getX(), menu.blockEntity.exitPositionCoords.getY(), menu.blockEntity.exitPositionCoords.getZ()));
            triggerRadiusField.setValue(String.valueOf(menu.blockEntity.triggerRadius));
            battleRadiusField.setValue(String.valueOf(menu.blockEntity.battleRadius));
            regenerationField.setValue(String.valueOf(menu.blockEntity.regeneration));
            enterPortalSpawnCoordsField.setValue(String.format("%d %d %d", menu.blockEntity.enterPortalSpawnCoords.getX(), menu.blockEntity.enterPortalSpawnCoords.getY(), menu.blockEntity.enterPortalSpawnCoords.getZ()));
            enterPortalDestCoordsField.setValue(String.format("%d %d %d", menu.blockEntity.enterPortalDestCoords.getX(), menu.blockEntity.enterPortalDestCoords.getY(), menu.blockEntity.enterPortalDestCoords.getZ()));
            skillExperienceField.setValue(String.valueOf(menu.blockEntity.skillExperiencePerWin));
            groupIdField.setValue(menu.blockEntity.groupId);
        }
    }

    private BlockPos parseCoords(String text) {
        try {
            String[] parts = text.split(" ");
            if (parts.length == 3) {
                return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
        } catch (NumberFormatException ignored) {
        }
        return BlockPos.ZERO;
    }

    private void onSave() {
        try {
            ClientPlayNetworking.send(new ModPackets.UpdateDungeonBossSpawnerPayload(
                    menu.blockEntity.getBlockPos(),
                    mobIdField.getValue(),
                    Integer.parseInt(respawnTimeField.getValue()),
                    Integer.parseInt(dungeonCloseTimerField.getValue()),
                    lootTableIdField.getValue(),
                    perPlayerLootTableIdField.getValue(),
                    parseCoords(exitPositionCoordsField.getValue()),
                    parseCoords(enterPortalSpawnCoordsField.getValue()),
                    parseCoords(enterPortalDestCoordsField.getValue()),
                    Integer.parseInt(triggerRadiusField.getValue()),
                    Integer.parseInt(battleRadiusField.getValue()),
                    Integer.parseInt(regenerationField.getValue()),
                    Integer.parseInt(skillExperienceField.getValue()),
                    groupIdField.getValue()
            ));
            this.onClose();
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format in one of the fields.");
        }
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.renderTooltip(context, mouseX, mouseY);
    }
}
