package net.ledok.arenas_ld.screen;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.ledok.arenas_ld.dungeon.blockentity.EntityDefinition;
import net.ledok.arenas_ld.networking.SummonDataHandlers;
import net.ledok.arenas_ld.packet.SummonDataPayloads;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.Modal;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static net.ledok.arenas_ld.screen.ArenasUi.DANGER;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL;

/**
 * The summon "Extra data" editor (4.30.0), a modal on a spawner screen:
 * <ul>
 *   <li>extra mob data as SNBT, merged into the mob before Arenas' attributes and scaling ({@code {Size:3}} is a size-4
 *       slime, as with /summon);</li>
 *   <li>for slimes and magma cubes only: the split parts' strength in percent (100 = the parent's health and attack
 *       scaled by size).</li>
 * </ul>
 * Dungeon mob and boss spawners and raid boss spawners save through {@link SummonDataPayloads.Set}; the arena spawner
 * edits a row of its own mob list and saves with the rest of it.
 */
public final class SummonDataEditor {
    private static final int W = 420;

    private SummonDataEditor() {
    }

    /** "Extra data…" for a dungeon mob spawner, dungeon boss spawner or raid boss spawner (its synced copy). */
    public static Button button(Supplier<VectorCanvas> canvas, BlockPos spawnerPos, float width) {
        return ArenasUi.button(Component.translatable("gui.arenas_ld.summon_data.open"), width, 18, () -> {
            var level = Minecraft.getInstance().level;
            EntityDefinition def = level == null ? null : SummonDataHandlers.definition(level.getBlockEntity(spawnerPos));
            if (def != null) {
                open(canvas.get(), def.mobId(), def.nbt(), def.splitStrength(),
                    (nbt, strength) -> ArenasNetwork.sendToServer(new SummonDataPayloads.Set(spawnerPos, nbt, strength)));
            }
        });
    }

    /** Whether this mob splits when it dies (a slime or magma cube, modded ones included). */
    public static boolean splits(String mobId) {
        var level = Minecraft.getInstance().level;
        return level != null && EntityType.byString(mobId).map(type -> type.create(level) instanceof Slime).orElse(false);
    }

    /** Opens the editor; {@code onSave} gets the SNBT (trimmed) and the strength once both are valid. */
    public static void open(VectorCanvas canvas, String mobId, String nbt, int strength, BiConsumer<String, Integer> onSave) {
        Modal[] modal = new Modal[1];
        Flex panel = Flex.column().gap(4).padding(Insets.of(10));
        panel.sizing(Sizing.fixed(W), Sizing.content());
        panel.backgroundFill(PANEL, HAIRLINE, 1);
        panel.item(ArenasUi.text(Component.translatable("gui.arenas_ld.summon_data.title", mobId), INK));
        panel.item(TextNode.of(Component.translatable("gui.arenas_ld.summon_data.nbt_hint")).color(INK_DIM).shadow(false)
            .maxWidth(W - 20));
        TextField nbtField = ArenasUi.textField(W - 20, nbt, 8192);
        nbtField.hint("{Size:3}");
        panel.item(nbtField);

        boolean splits = splits(mobId);
        TextField strengthField = ArenasUi.textField(44, String.valueOf(strength), 4);
        if (splits) {
            panel.item(ArenasUi.spacer(2));
            Flex row = Flex.row().gap(6).alignItems(Align.CENTER);
            row.sizing(Sizing.fill(), Sizing.content());
            row.item(ArenasUi.text(Component.translatable("gui.arenas_ld.summon_data.split"), INK));
            row.item(strengthField);
            row.item(ArenasUi.text(Component.literal("%"), INK));
            panel.item(row);
            panel.item(TextNode.of(Component.translatable("gui.arenas_ld.summon_data.split_hint")).color(INK_DIM)
                .shadow(false).maxWidth(W - 20));
        }

        Label error = ArenasUi.label(W - 20, Component.empty(), DANGER);
        panel.item(error);

        Flex footer = Flex.row().gap(6).alignItems(Align.CENTER);
        footer.sizing(Sizing.fill(), Sizing.content());
        footer.item(ArenasUi.button(Component.translatable("gui.arenas_ld.summon_data.save"), 80, 18, () -> {
            String text = nbtField.text().trim();
            if (!text.isEmpty()) {
                try {
                    TagParser.parseTag(text);
                } catch (CommandSyntaxException e) {
                    error.text(Component.translatable("gui.arenas_ld.summon_data.bad_nbt", e.getMessage()));
                    return;
                }
            }
            int value = strength;
            if (splits) {
                try {
                    value = Integer.parseInt(strengthField.text().trim());
                } catch (NumberFormatException e) {
                    value = -1;
                }
                if (value < 1 || value > 1000) {
                    error.text(Component.translatable("gui.arenas_ld.summon_data.bad_split"));
                    return;
                }
            }
            onSave.accept(text, value);
            modal[0].close();
        }));
        footer.item(ArenasUi.button(Component.translatable("gui.arenas_ld.summon_data.cancel"), 80, 18, () -> modal[0].close()));
        panel.item(footer);

        modal[0] = Modal.open(canvas, panel);
    }
}
