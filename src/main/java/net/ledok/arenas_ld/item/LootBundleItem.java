package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

public class LootBundleItem extends Item {
    public LootBundleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        LootBundleDataComponent data = stack.get(LootBundleDataComponent.LOOT_BUNDLE_DATA.get());
        if (data == null || data.lootTableId().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.arenas_ld.loot_bundle.empty_invalid"), true);
            return InteractionResultHolder.fail(stack);
        }

        ResourceLocation lootTableId = ResourceLocation.tryParse(data.lootTableId());
        if (lootTableId == null) {
            player.displayClientMessage(Component.translatable("message.arenas_ld.loot_bundle.invalid_id"), true);
            return InteractionResultHolder.fail(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTableId));

        LootParams.Builder builder = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player);

        LootParams lootParams = builder.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.GIFT);
        List<ItemStack> loot = lootTable.getRandomItems(lootParams);

        if (loot.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.arenas_ld.loot_bundle.empty"), true);
        } else {
            for (ItemStack lootStack : loot) {
                if (!player.getInventory().add(lootStack)) {
                    player.drop(lootStack, false);
                }
            }
        }

        stack.consume(1, player);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        LootBundleDataComponent data = stack.get(LootBundleDataComponent.LOOT_BUNDLE_DATA.get());
        if (data != null && !data.lootTableId().isEmpty()) {
            tooltipComponents.add(Component.translatable("tooltip.arenas_ld.loot_bundle.loot_table", data.lootTableId()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
