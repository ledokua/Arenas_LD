package net.ledok.arenas_ld.activity;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /arenasld instance add|check} (op level 2). Instances are copied with WorldEdit or Axiom;
 * {@code add} links a pasted copy to its activity's controller from anywhere (what the Dungeon Tool
 * does by clicking both blocks), and {@code check} verifies an instance's links (see
 * {@link InstanceCheck}).
 */
public final class InstanceCommands {
    /** Lines of problems shown per instance before the rest is summed up. */
    private static final int MAX_PROBLEM_LINES = 15;

    private static final SuggestionProvider<CommandSourceStack> ACTIVITY_IDS = (ctx, builder) ->
        SharedSuggestionProvider.suggestResource(
            ArenasApi.activities(ctx.getSource().getServer()).stream().map(ArenasApi.Activity::id), builder);

    private InstanceCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("instance")
            .requires(source -> source.hasPermission(2))
            .then(literal("add")
                .then(argument("activity", ResourceLocationArgument.id())
                    .suggests(ACTIVITY_IDS)
                    .then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> add(ctx, ctx.getSource().getLevel()))
                        .then(argument("dimension", DimensionArgument.dimension())
                            .executes(ctx -> add(ctx, DimensionArgument.getDimension(ctx, "dimension")))))))
            .then(literal("check")
                .then(literal("at")
                    .then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> checkAt(ctx, ctx.getSource().getLevel()))
                        .then(argument("dimension", DimensionArgument.dimension())
                            .executes(ctx -> checkAt(ctx, DimensionArgument.getDimension(ctx, "dimension"))))))
                .then(argument("activity", ResourceLocationArgument.id())
                    .suggests(ACTIVITY_IDS)
                    .executes(InstanceCommands::checkActivity)));
    }

    private static int add(CommandContext<CommandSourceStack> ctx, ServerLevel level) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "activity");
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        ActivityHost host = ActivityRegistry.resolve(source.getServer(), id);
        if (host == null) {
            source.sendFailure(Component.translatable("message.arenas_ld.activity.unknown", id.toString()));
            return 0;
        }
        BlockEntity spawner = level.getBlockEntity(pos);
        Block expected;
        Boolean added = null;
        switch (host.asBlockEntity()) {
            case DungeonControllerBlockEntity controller -> {
                expected = BlockRegistry.DUNGEON_BOSS_SPAWNER_BLOCK;
                if (controller.getInstances().contains(pos) && !controller.getInstanceDimension(pos).equals(level.dimension())) {
                    // A dungeon controller holds one instance per position, whatever the dimension.
                    source.sendFailure(Component.translatable("message.arenas_ld.instance.position_taken", id.toString(),
                        controller.getInstanceDimension(pos).location().toString()));
                    return 0;
                }
                if (spawner instanceof DungeonBossSpawnerBlockEntity) {
                    added = controller.addInstance(pos, level.dimension());
                }
            }
            case RaidControllerBlockEntity controller -> {
                expected = BlockRegistry.RAID_BOSS_SPAWNER_BLOCK;
                if (spawner instanceof RaidBossSpawnerBlockEntity) {
                    added = controller.addInstance(pos, level.dimension());
                }
            }
            case ArenaControllerBlockEntity controller -> {
                expected = BlockRegistry.ARENA_SPAWNER_BLOCK;
                if (spawner instanceof ArenaSpawnerBlockEntity) {
                    added = controller.addInstance(pos, level.dimension());
                }
            }
            default -> {
                return 0;
            }
        }
        String where = pos.toShortString() + " (" + level.dimension().location() + ")";
        if (added == null) {
            source.sendFailure(Component.translatable("message.arenas_ld.instance.wrong_block",
                expected.getName(), where));
            return 0;
        }
        if (!added) {
            source.sendFailure(Component.translatable("message.arenas_ld.instance.already_linked", where, id.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.arenas_ld.instance.added", where, id.toString()), true);
        InstanceCheck.Report report = InstanceCheck.check(level, pos);
        if (report != null) {
            send(source, report);
        }
        return 1;
    }

    private static int checkAt(CommandContext<CommandSourceStack> ctx, ServerLevel level) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        InstanceCheck.Report report = InstanceCheck.check(level, pos);
        if (report == null) {
            ctx.getSource().sendFailure(Component.translatable("message.arenas_ld.instance.not_an_instance",
                pos.toShortString() + " (" + level.dimension().location() + ")"));
            return 0;
        }
        send(ctx.getSource(), report);
        return report.errors() == 0 ? 1 : 0;
    }

    private static int checkActivity(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "activity");
        ActivityHost host = ActivityRegistry.resolve(server, id);
        if (host == null) {
            source.sendFailure(Component.translatable("message.arenas_ld.activity.unknown", id.toString()));
            return 0;
        }
        List<Instance> instances = instancesOf(host);
        if (instances.isEmpty()) {
            source.sendFailure(Component.translatable("message.arenas_ld.instance.none", id.toString()));
            return 0;
        }
        int clean = 0;
        for (Instance instance : instances) {
            ServerLevel level = server.getLevel(instance.dimension());
            String where = instance.pos().toShortString() + " (" + instance.dimension().location() + ")";
            InstanceCheck.Report report = level == null ? null : InstanceCheck.check(level, instance.pos());
            if (report == null) {
                source.sendFailure(Component.translatable("message.arenas_ld.instance.not_an_instance", where));
                continue;
            }
            send(source, report);
            if (report.errors() == 0) {
                clean++;
            }
        }
        int total = instances.size();
        int ok = clean;
        source.sendSuccess(() -> Component.translatable("message.arenas_ld.instance.checked", ok, total)
            .withStyle(ok == total ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        return clean;
    }

    private record Instance(BlockPos pos, ResourceKey<Level> dimension) {
    }

    private static List<Instance> instancesOf(ActivityHost host) {
        List<Instance> instances = new ArrayList<>();
        switch (host.asBlockEntity()) {
            case DungeonControllerBlockEntity c -> c.getInstances()
                .forEach(pos -> instances.add(new Instance(pos, c.getInstanceDimension(pos))));
            case RaidControllerBlockEntity c -> c.getInstances()
                .forEach(inst -> instances.add(new Instance(inst.spawnerPos(), inst.dimension())));
            case ArenaControllerBlockEntity c -> c.getInstances()
                .forEach(inst -> instances.add(new Instance(inst.spawnerPos(), inst.dimension())));
            default -> {
            }
        }
        return instances;
    }

    private static void send(CommandSourceStack source, InstanceCheck.Report report) {
        String where = report.pos().toShortString() + " (" + report.dimension().location() + ")";
        source.sendSuccess(() -> Component.translatable("message.arenas_ld.instance.check_header", report.what(), where)
            .withStyle(ChatFormatting.GOLD), false);
        Component linked = report.linkedTo().isEmpty()
            ? Component.translatable("message.arenas_ld.instance.not_linked").withStyle(ChatFormatting.YELLOW)
            : Component.translatable("message.arenas_ld.instance.linked_to",
                report.linkedTo().stream().map(ResourceLocation::toString).collect(Collectors.joining(", ")))
                .withStyle(ChatFormatting.GRAY);
        source.sendSuccess(() -> Component.literal(" ").append(linked), false);
        source.sendSuccess(() -> Component.literal(" ").append(report.summary()).withStyle(ChatFormatting.GRAY), false);
        List<InstanceCheck.Problem> problems = report.problems();
        for (int i = 0; i < Math.min(problems.size(), MAX_PROBLEM_LINES); i++) {
            InstanceCheck.Problem problem = problems.get(i);
            boolean error = problem.severity() == InstanceCheck.Severity.ERROR;
            MutableComponent line = Component.literal(error ? " ✖ " : " ! ").append(problem.message())
                .withStyle(error ? ChatFormatting.RED : ChatFormatting.YELLOW);
            source.sendSuccess(() -> line, false);
        }
        if (problems.size() > MAX_PROBLEM_LINES) {
            int more = problems.size() - MAX_PROBLEM_LINES;
            source.sendSuccess(() -> Component.translatable("message.arenas_ld.instance.more_problems", more)
                .withStyle(ChatFormatting.GRAY), false);
        }
        if (problems.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.arenas_ld.instance.no_problems")
                .withStyle(ChatFormatting.GREEN), false);
        }
    }
}
