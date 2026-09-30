package net.ledok.arenas_ld.client.hud;

import net.ledok.arenas_ld.packet.LootRewardPayload;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.vectorlib.client.canvas.CanvasGroup;
import net.ledok.vectorlib.client.canvas.Colors;
import net.ledok.vectorlib.client.canvas.ImageNode;
import net.ledok.vectorlib.client.canvas.ItemStackNode;
import net.ledok.vectorlib.client.canvas.ShapeNode;
import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.presentation.CanvasOverlay;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The loot-reveal popup: reward item cards that pop in one by one across the top of the
 * screen (below the run-timer bar), with a counting-up gold line and an XP line. Cards are
 * tinted by item rarity and tagged when the item overflowed to the economy inbox or the
 * ground. Multiple rewards queue up and play back to back.
 */
public final class LootRevealHud {
    private static final float CARD = 30;
    private static final float GAP = 6;
    private static final float CARDS_Y = 13;
    /** Vertical gap between wrapped rows when no card carries a tag line below it. */
    private static final float ROW_GAP = 6;
    /** Horizontal gap between the Items and Effects sections on a single line. */
    private static final float SECTION_GAP = 14;
    private static final long CARD_STAGGER_MS = 130;
    private static final long CARD_POP_MS = 220;
    private static final long COUNT_UP_MS = 700;
    /** Long on purpose: builders asked for 10-15 s to look the loot over. A new room entering
     *  battle cuts it short via {@link net.ledok.arenas_ld.packet.LootHudClearPayload}. */
    private static final long HOLD_MS = 12_000;
    private static final long EXIT_MS = 250;

    private static final ArrayDeque<LootRewardPayload> QUEUE = new ArrayDeque<>();

    private static @Nullable LootRewardPayload active;
    private static long startedAtMs;
    private static @Nullable CanvasOverlay.Handle handle;
    private static VectorCanvas canvas;
    private static final List<CanvasGroup> cardGroups = new ArrayList<>();
    private static @Nullable TextNode currencyText;
    private static int revealedCards;
    private static boolean currencySoundPlayed;

    private LootRevealHud() {
    }

    public static void onPayload(LootRewardPayload payload) {
        if (payload.items().isEmpty() && payload.effects().isEmpty()
            && payload.currency() <= 0L && payload.skillXp() <= 0) {
            return;
        }
        QUEUE.addLast(payload);
    }

    public static void clear() {
        QUEUE.clear();
        dismiss();
    }

    /** Per-frame driver, called from the shared HUD render hook. */
    public static void tick() {
        if (active == null) {
            LootRewardPayload next = QUEUE.pollFirst();
            if (next == null) {
                return;
            }
            begin(next);
        }
        long elapsed = System.currentTimeMillis() - startedAtMs;
        int cards = cardGroups.size();
        long allCardsAt = cards == 0 ? 0 : (cards - 1) * CARD_STAGGER_MS + CARD_POP_MS;

        // Pop cards in one at a time.
        for (int i = revealedCards; i < cards; i++) {
            long cardStart = i * CARD_STAGGER_MS;
            if (elapsed < cardStart) {
                break;
            }
            cardGroups.get(i).visible(true);
            revealedCards = i + 1;
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.1f + i * 0.08f, 0.55f));
        }
        for (int i = 0; i < revealedCards; i++) {
            CanvasGroup group = cardGroups.get(i);
            float t = Mth.clamp((elapsed - i * CARD_STAGGER_MS) / (float) CARD_POP_MS, 0, 1);
            float ease = 1 - (1 - t) * (1 - t);
            group.transform.pivot(CARD / 2, CARD / 2).scale(0.5f + 0.5f * ease);
        }

        // Gold counts up once the cards are in.
        if (currencyText != null && active != null && elapsed >= allCardsAt) {
            float t = Mth.clamp((elapsed - allCardsAt) / (float) COUNT_UP_MS, 0, 1);
            long shown = t >= 1 ? active.currency()
                : (long) Math.floor(active.currency() * (double) (1 - (1 - t) * (1 - t)));
            currencyText.text(Component.translatable("hud.arenas_ld.loot.currency",
                String.format("%,d", shown)));
            if (!currencySoundPlayed) {
                currencySoundPlayed = true;
                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f, 0.5f));
            }
        }

        // Hold, then slide out.
        long exitStart = allCardsAt + (currencyText != null ? COUNT_UP_MS : 0) + HOLD_MS;
        if (elapsed >= exitStart) {
            float t = Mth.clamp((elapsed - exitStart) / (float) EXIT_MS, 0, 1);
            canvas.transform.pivot(canvas.width() / 2, 0)
                .translate(0, -t * t * canvas.height())
                .scale(1 - 0.3f * t);
            if (t >= 1) {
                dismiss();
            }
        } else {
            canvas.transform.pivot(canvas.width() / 2, 0).translate(0, 0).scale(1);
        }
    }

    private static void begin(LootRewardPayload payload) {
        active = payload;
        startedAtMs = System.currentTimeMillis();
        revealedCards = 0;
        currencySoundPlayed = false;
        cardGroups.clear();
        currencyText = null;

        int itemCount = payload.items().size();
        int effectCount = payload.effects().size();
        int cards = itemCount + effectCount;
        // With effects in the popup, each section gets an "Items:"/"Effects:" caption; an
        // items-only reward keeps the plain card row.
        boolean labeled = effectCount > 0;
        var font = Minecraft.getInstance().font;
        List<Section> sections = new ArrayList<>(2);
        if (itemCount > 0) {
            Component label = labeled ? Component.translatable("hud.arenas_ld.loot.items") : null;
            List<java.util.function.Consumer<CanvasGroup>> builders = new ArrayList<>(itemCount);
            for (LootRewardPayload.Entry entry : payload.items()) {
                builders.add(group -> buildCard(group, entry));
            }
            sections.add(new Section(label, label == null ? 0 : font.width(label) + 5, builders));
        }
        if (effectCount > 0) {
            Component label = Component.translatable("hud.arenas_ld.loot.effects");
            List<java.util.function.Consumer<CanvasGroup>> builders = new ArrayList<>(effectCount);
            for (MobEffectInstance effect : payload.effects()) {
                builders.add(group -> buildEffectCard(group, effect));
            }
            sections.add(new Section(label, font.width(label) + 5, builders));
        }

        // Effect cards always show their duration below, so they need the tall text offset.
        boolean anyTag = effectCount > 0 || payload.items().stream()
            .anyMatch(e -> e.destination() != LootRewardPayload.Destination.INVENTORY);
        float rowStride = CARD + (anyTag ? 13 : ROW_GAP);
        // Rows never run off the screen: at GUI scale 3+ a big reward wraps onto centered rows.
        float maxRowW = Math.max(CARD, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24);

        List<Placed> placed = new ArrayList<>();
        List<Placed> labels = new ArrayList<>();
        float inlineW = 0;
        for (Section section : sections) {
            inlineW += section.labelW() + cardsWidth(section.cards().size());
        }
        inlineW += sections.size() > 1 ? SECTION_GAP : 0;
        int rows;
        float contentW;
        if (inlineW <= maxRowW) {
            // Everything fits on one line: "Items: [..]  Effects: [..]" side by side.
            rows = cards > 0 ? 1 : 0;
            contentW = inlineW;
            float x = 0;
            for (Section section : sections) {
                if (section.label() != null) {
                    labels.add(new Placed(section.label(), null, x, 0));
                    x += section.labelW();
                }
                for (var builder : section.cards()) {
                    placed.add(new Placed(null, builder, x, 0));
                    x += CARD + GAP;
                }
                x += SECTION_GAP - GAP;
            }
        } else {
            // Sections stack; each wraps into rows centered under its card area.
            rows = 0;
            contentW = 0;
            List<float[]> sectionSpans = new ArrayList<>(); // [labelW, areaW] per section, for centering
            for (Section section : sections) {
                int perRow = Math.max(1, (int) ((maxRowW - section.labelW() + GAP) / (CARD + GAP)));
                float areaW = cardsWidth(Math.min(perRow, section.cards().size()));
                contentW = Math.max(contentW, section.labelW() + areaW);
                sectionSpans.add(new float[]{section.labelW(), areaW});
            }
            for (int si = 0; si < sections.size(); si++) {
                Section section = sections.get(si);
                float labelW = sectionSpans.get(si)[0];
                float areaW = sectionSpans.get(si)[1];
                float blockX = (contentW - (labelW + areaW)) / 2;
                int perRow = Math.max(1, (int) ((maxRowW - labelW + GAP) / (CARD + GAP)));
                if (section.label() != null) {
                    labels.add(new Placed(section.label(), null, blockX, rows * rowStride));
                }
                List<java.util.function.Consumer<CanvasGroup>> list = section.cards();
                for (int from = 0; from < list.size(); from += perRow) {
                    int to = Math.min(list.size(), from + perRow);
                    float rowW = cardsWidth(to - from);
                    float x = blockX + labelW + (areaW - rowW) / 2;
                    for (int i = from; i < to; i++) {
                        placed.add(new Placed(null, list.get(i), x, rows * rowStride));
                        x += CARD + GAP;
                    }
                    rows++;
                }
            }
        }

        float width = Math.max(170, contentW + 20);
        float textY = rows > 0 ? CARDS_Y + (rows - 1) * rowStride + CARD + (anyTag ? 13 : 4) : CARDS_Y;
        float height = textY + (payload.currency() > 0 ? 15 : 0) + (payload.skillXp() > 0 ? 12 : 0) + 2;

        canvas = VectorCanvas.create(width, height);
        canvas.theme(ArenasUi.THEME);

        TextNode header = canvas.add(TextNode.of(headerFor(payload.source()))
            .color(ArenasUi.INK_MID).shadow(true).align(TextNode.Align.CENTER));
        header.at(width / 2, 0);

        float originX = (width - contentW) / 2;
        for (Placed label : labels) {
            canvas.add(TextNode.of(label.label()).color(ArenasUi.INK_MID).shadow(true))
                .at(originX + label.x(), CARDS_Y + label.y() + 11);
        }
        for (Placed card : placed) {
            CanvasGroup group = canvas.add(CanvasGroup.create());
            group.at(originX + card.x(), CARDS_Y + card.y());
            card.builder().accept(group);
            group.visible(false);
            cardGroups.add(group);
        }

        float y = textY;
        if (payload.currency() > 0) {
            currencyText = canvas.add(TextNode.of(Component.translatable("hud.arenas_ld.loot.currency", "0"))
                .color(ArenasUi.WARN).shadow(true).scale(1.4f).align(TextNode.Align.CENTER));
            currencyText.at(width / 2, y);
            y += 15;
        }
        if (payload.skillXp() > 0) {
            TextNode xpText = canvas.add(TextNode.of(Component.translatable("hud.arenas_ld.loot.xp",
                    String.format("%,d", payload.skillXp())))
                .color(ArenasUi.INFO).shadow(true).align(TextNode.Align.CENTER));
            xpText.at(width / 2, y);
        }

        // Sits below the run-timer bar's full footprint (its canvas is 50 tall + 6 margin).
        handle = CanvasOverlay.show(canvas, Placement.Screen.top(62));
    }

    /** A captioned run of cards (items or effects); {@code label} is null for an uncaptioned run. */
    private record Section(@Nullable Component label, float labelW,
                           List<java.util.function.Consumer<CanvasGroup>> cards) {}

    /** A card builder or a caption at an offset from the content origin. */
    private record Placed(@Nullable Component label, java.util.function.Consumer<CanvasGroup> builder,
                          float x, float y) {}

    private static float cardsWidth(int count) {
        return count > 0 ? count * CARD + (count - 1) * GAP : 0;
    }

    private static void buildCard(CanvasGroup group, LootRewardPayload.Entry entry) {
        ItemStack stack = entry.stack();
        int rarity = rarityColor(stack);

        ShapeNode bg = group.add(Shapes.roundRect(0, 0, CARD, CARD, 4)
            .fill(Colors.withAlpha(ArenasUi.ROW_BG, 0xE6)));
        bg.stroke(rarity, 1.5f);
        group.add(ItemStackNode.of(stack, (CARD - 22) / 2, (CARD - 22) / 2, 22));

        if (stack.getCount() > 1) {
            TextNode count = group.add(TextNode.of("x" + stack.getCount())
                .color(ArenasUi.INK).shadow(true).align(TextNode.Align.RIGHT));
            count.at(CARD - 2, CARD - 10);
        }

        if (entry.destination() != LootRewardPayload.Destination.INVENTORY) {
            boolean inbox = entry.destination() == LootRewardPayload.Destination.INBOX;
            TextNode tag = group.add(TextNode.of(Component.translatable(
                    inbox ? "hud.arenas_ld.loot.to_inbox" : "hud.arenas_ld.loot.to_ground"))
                .color(inbox ? ArenasUi.INFO : ArenasUi.WARN).shadow(true).align(TextNode.Align.CENTER));
            tag.at(CARD / 2, CARD + 3);
        }
    }

    /** An effect reward card: category-colored frame, effect icon, level numeral, duration below. */
    private static void buildEffectCard(CanvasGroup group, MobEffectInstance effect) {
        int frame = switch (effect.getEffect().value().getCategory()) {
            case BENEFICIAL -> ArenasUi.GOOD;
            case HARMFUL -> ArenasUi.DANGER;
            default -> ArenasUi.HAIRLINE_HI;
        };
        ShapeNode bg = group.add(Shapes.roundRect(0, 0, CARD, CARD, 4)
            .fill(Colors.withAlpha(ArenasUi.ROW_BG, 0xE6)));
        bg.stroke(frame, 1.5f);

        effect.getEffect().unwrapKey().ifPresent(key -> {
            ResourceLocation id = key.location();
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png");
            group.add(ImageNode.of(texture, (CARD - 22) / 2, (CARD - 22) / 2, 22, 22, 18, 18));
        });

        int level = effect.getAmplifier() + 1;
        if (level > 1) {
            TextNode levelText = group.add(TextNode.of(levelNumeral(level))
                .color(ArenasUi.INK).shadow(true).align(TextNode.Align.RIGHT));
            levelText.at(CARD - 2, CARD - 10);
        }

        TextNode duration = group.add(TextNode.of(formatDuration(effect))
            .color(ArenasUi.INK_MID).shadow(true).align(TextNode.Align.CENTER));
        duration.at(CARD / 2, CARD + 3);
    }

    /** "II" for level 2, like enchantments; plain digits past X where vanilla has no numeral. */
    private static Component levelNumeral(int level) {
        return level <= 10 ? Component.translatable("enchantment.level." + level)
            : Component.literal(String.valueOf(level));
    }

    /** m:ss like the inventory's effect list; "∞" for infinite effects. */
    private static Component formatDuration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) {
            return Component.literal("∞");
        }
        int seconds = effect.getDuration() / 20;
        return Component.literal(String.format("%d:%02d", seconds / 60, seconds % 60));
    }

    private static Component headerFor(LootRewardPayload.Source source) {
        return Component.translatable(switch (source) {
            case DUNGEON_WIN -> "hud.arenas_ld.loot.dungeon_win";
            case ROOM_CLEAR -> "hud.arenas_ld.loot.room_clear";
            case RAID_WIN -> "hud.arenas_ld.loot.raid_win";
            case ARENA_WAVE -> "hud.arenas_ld.loot.arena_wave";
            case ARENA_SUMMARY -> "hud.arenas_ld.loot.arena_summary";
        });
    }

    private static int rarityColor(ItemStack stack) {
        Integer color = stack.getRarity().color().getColor();
        if (color == null || (color & 0xFFFFFF) == 0xFFFFFF) {
            return ArenasUi.HAIRLINE_HI; // common items: quiet frame instead of white
        }
        return 0xFF000000 | color;
    }

    private static void dismiss() {
        if (handle != null) {
            handle.hide();
            handle = null;
        }
        active = null;
        cardGroups.clear();
        currencyText = null;
    }
}
