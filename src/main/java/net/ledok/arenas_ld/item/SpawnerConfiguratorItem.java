package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.SpawnerSelectionDataComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class SpawnerConfiguratorItem extends Item {
    public SpawnerConfiguratorItem(Properties properties) {
        super(properties);
    }

    public enum Mode {
        SPAWNER_SELECTION("item.arenas_ld.configurator.mode.spawner_selection"),
        EXIT_POSITION("item.arenas_ld.configurator.mode.exit_position"),
        ENTER_PORTAL_POSITION("item.arenas_ld.configurator.mode.enter_portal_position"),
        ENTER_PORTAL_DESTINATION("item.arenas_ld.configurator.mode.enter_portal_destination");

        private final String translationKey;

        Mode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getName() {
            return Component.translatable(translationKey);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (world.isClientSide || player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (!player.isCreative() && !player.hasPermissions(2)) {
            return InteractionResult.PASS;
        }

        SpawnerSelectionDataComponent data = stack.getOrDefault(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
        Mode currentMode = Mode.values()[data.mode()];
        BlockEntity clickedBlockEntity = world.getBlockEntity(clickedPos);

        // Handle spawner selection regardless of current mode
        if (clickedBlockEntity instanceof BossSpawnerBlockEntity || clickedBlockEntity instanceof DungeonBossSpawnerBlockEntity) {
            stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, new SpawnerSelectionDataComponent(data.mode(), Optional.of(clickedPos)));
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawner_selected", clickedPos.toShortString()));
            return InteractionResult.SUCCESS;
        }

        Optional<BlockPos> selectedSpawnerPosOpt = data.selectedSpawnerPos();
        if (selectedSpawnerPosOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.no_spawner_selected"));
            return InteractionResult.FAIL;
        }

        BlockPos selectedSpawnerPos = selectedSpawnerPosOpt.get();
        BlockEntity selectedBlockEntity = world.getBlockEntity(selectedSpawnerPos);

        if (!(selectedBlockEntity instanceof BossSpawnerBlockEntity) && !(selectedBlockEntity instanceof DungeonBossSpawnerBlockEntity)) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.invalid_spawner"));
            stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, new SpawnerSelectionDataComponent(data.mode(), Optional.empty()));
            return InteractionResult.FAIL;
        }

        // Calculate relative position
        BlockPos relativePos = clickedPos.subtract(selectedSpawnerPos);

        switch (currentMode) {
            case EXIT_POSITION:
                if (selectedBlockEntity instanceof BossSpawnerBlockEntity bossSpawner) {
                    bossSpawner.setExitPortalCoords(relativePos);
                } else if (selectedBlockEntity instanceof DungeonBossSpawnerBlockEntity dungeonBossSpawner) {
                    dungeonBossSpawner.setExitPositionCoords(relativePos);
                }
                player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.exit_pos_set", clickedPos.toShortString()));
                break;
            case ENTER_PORTAL_POSITION:
                if (selectedBlockEntity instanceof BossSpawnerBlockEntity bossSpawner) {
                    bossSpawner.setEnterPortalSpawnCoords(relativePos);
                } else if (selectedBlockEntity instanceof DungeonBossSpawnerBlockEntity dungeonBossSpawner) {
                    dungeonBossSpawner.setEnterPortalSpawnCoords(relativePos);
                }
                player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.enter_portal_pos_set", clickedPos.toShortString()));
                break;
            case ENTER_PORTAL_DESTINATION:
                if (selectedBlockEntity instanceof BossSpawnerBlockEntity bossSpawner) {
                    bossSpawner.setEnterPortalDestCoords(relativePos);
                } else if (selectedBlockEntity instanceof DungeonBossSpawnerBlockEntity dungeonBossSpawner) {
                    dungeonBossSpawner.setEnterPortalDestCoords(relativePos);
                }
                player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.enter_portal_dest_set", clickedPos.toShortString()));
                break;
            default:
                return InteractionResult.PASS;
        }

        world.sendBlockUpdated(selectedSpawnerPos, selectedBlockEntity.getBlockState(), selectedBlockEntity.getBlockState(), 3);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!world.isClientSide && player.isShiftKeyDown()) {
            BlockHitResult hitResult = getPlayerPOVHitResult(world, player, ClipContext.Fluid.NONE);
            if (hitResult.getType() == BlockHitResult.Type.MISS) {
                stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
                player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.cleared_selection"));
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        SpawnerSelectionDataComponent data = stack.getOrDefault(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
        Mode currentMode = Mode.values()[data.mode()];

        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.configurator.mode", currentMode.getName()).withStyle(net.minecraft.ChatFormatting.GRAY));

        data.selectedSpawnerPos().ifPresent(pos -> {
            tooltipComponents.add(Component.translatable("tooltip.arenas_ld.configurator.selected_spawner", pos.toShortString()).withStyle(net.minecraft.ChatFormatting.GOLD));
        });

        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.shift_scroll").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
