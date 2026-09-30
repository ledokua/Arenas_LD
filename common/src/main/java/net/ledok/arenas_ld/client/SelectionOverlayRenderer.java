package net.ledok.arenas_ld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ledok.arenas_ld.client.ArenasClientEvents.WorldRenderContext;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.PhaseBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.RoomGraph;
import net.ledok.arenas_ld.item.DungeonToolItem;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.DungeonToolDataComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Draws through-wall outline boxes for the selection held in a Dungeon Tool. Everything attached to the
 * selected block is drawn at once — links, doors, respawn points, entrances, protect and mob spawn
 * positions — no matter which tool mode is active; the mode only decides which of those draws at full
 * alpha (see {@link Kind} for the color legend). Client-side only: the selection comes from the held
 * item's data component and every position comes from the synced block entity at the selected position,
 * so nothing here reads state the client does not have. Renders with the depth test disabled so the
 * boxes stay visible through terrain.
 *
 * <p>Three cues separate the kinds beyond hue, because a dense room is dozens of boxes and hue alone
 * does not survive a thin line seen through a wall: the selection draws a doubled outline, each kind
 * sits at its own {@linkplain Kind#inset depth} so stored positions read as smaller than the blocks they
 * belong to and no two kinds ever share an edge, and every box tints its whole block through that
 * block's own faces, in a {@linkplain Kind#fillColor fill variant} of its color chosen to survive being
 * a wash rather than a line, at a {@linkplain Kind#fillAlpha strength} chosen for how often that kind
 * stacks along a view ray.
 *
 * <h2>The two passes</h2>
 * Filled faces go down first and the outlines on top of them. With the depth test off there is no
 * sorting but submission order, so the order is the design: a door's bright violet edge has to win over
 * its own tint or the door stops reading as a door. Both passes are hand-driven ({@link Tesselator} into
 * {@link BufferUploader}) rather than routed through a {@link net.minecraft.client.renderer.RenderType},
 * because the after-translucent hook ({@link ArenasClientEvents#AFTER_TRANSLUCENT}) runs after vanilla has flushed its buffer source —
 * so the hook deliberately offers no buffer source — and because a see-through type cannot be built
 * from vanilla's shards anyway: {@code RenderStateShard.NO_DEPTH_TEST} is a literal no-op in 1.21.1
 * (both its setup and its clear short-circuit on {@code func == 519}), so a render type declaring it
 * silently inherits whatever depth state the previous stage happened to leave behind.
 *
 * <h2>Why tinting every kind does not pile up</h2>
 * Boxes in this overlay share blocks constantly. Every position mode stores {@code clickedPos.above()},
 * so an entrance and a respawn point clicked on the same floor block are the <em>same</em>
 * {@link BlockPos} drawn twice at different insets; a selected Room Controller draws its spawners and
 * those spawners' own spawn positions, which routinely land on each other; and the selection can
 * coincide with any of them. The depth test is off, so nothing rejects a stack. Three rules keep the
 * tint to a number that can be written down instead:
 *
 * <ol>
 *   <li><b>One fill per block cell.</b> {@link #resolveFills} hands each cell to exactly one kind, and a
 *       kind that loses a cell keeps its outline and loses only its tint. Nothing is hidden by that: the
 *       outlines are what carry the legend, so a block three kinds claim still draws three concentric
 *       squares, it is simply washed once. The order ({@link Kind#fillPriority}) is the selection, then
 *       doors, then the active mode's own kind, then the rest outermost-in: the selection because
 *       wherever it lands it is what the cell means; doors for the reason in the next rule; the active
 *       kind because the entire point of the {@link #DIM_ALPHA} tier is that the mode's own targets read
 *       first, and ranking purely by geometry would wash the block a builder is placing in some other
 *       kind's dimmed color; and outermost-in for the rest, because the big boxes are the ones that
 *       describe a <em>volume</em> while the inset ones are points inside it.</li>
 *   <li><b>A door group is all of its cells or none of them.</b> A door is the one fill here that is not
 *       a cube: {@link #exteriorCells} drops each cell's face toward an in-group neighbor, so the group
 *       is a closed surface only while all of it is present. Keeping one cell and dropping the one next
 *       to it would leave a gap that the culling in the next rule turns into a see-through hole rather
 *       than a smaller doorway — the far face behind the gap points away from the ray and is culled.
 *       So a group that cannot take every one of its cells takes none and keeps its outline, exactly as
 *       an oversized one does, and doors outrank every kind but the selection so that almost never
 *       comes up.</li>
 *   <li><b>Back-face culling, for the fill pass only.</b> Every fill is a closed surface, so culling
 *       leaves exactly one layer per time a view ray enters one. That is what makes the tint alphas mean
 *       what they say instead of half of what they say, and it is why {@link #VOLUME_FILL_ALPHA} is 0.19
 *       and not the old 0.10: {@code 1-(1-0.10)^2 = 0.19}, so a door and the selection composite to
 *       exactly the value they always did, concave groups included, while every other kind joins them
 *       for free.</li>
 * </ol>
 *
 * <p>Coincident and nested boxes are therefore pinned at one layer however many of them there are: the
 * case this overlay's geometry used to threaten — a protect position inside a room-linked spawner's box,
 * under the selection — is one cell, one fill, one layer, the same wash a lone doorway has always had.
 * What no per-cell rule can touch is genuinely separate cells lined up along one view ray, which
 * composite to {@code 1-(1-a)^n}, and that case is structural rather than rare: every stored position is
 * {@code clickedPos.above()}, so a room's markers form a sheet one block thick sitting just above its
 * floor. A builder standing on that floor looks over the sheet — their eye is 0.62 of a block above its
 * top — but tilting down to inspect it is a 3.5 degree change, and the ray is then inside the sheet from
 * 10 blocks out to 26, crossing every marker in the line; standing one level below it, horizontal is
 * enough. That is why the tint strength is per kind rather than one number. The four kinds that mark a
 * real block in the world tint at {@link #VOLUME_FILL_ALPHA}; the four that mark a stored position — the
 * numerous ones, the ones that make up that sheet — tint at {@link #POINT_FILL_ALPHA}, where eight on one
 * ray hide 57% of what is behind them instead of 82%, and the dimmed 0.063 tier hides 41%. If a builder
 * ever reports a haze, {@link #POINT_FILL_ALPHA} is the dial, not the palette.
 *
 * <p>Fills composite in rank order rather than depth order, since this pass neither tests nor writes
 * depth: a far protect cell draws over a near doorway cell on the same ray. How much of the background
 * survives is order-independent, so none of the numbers above move; only the resulting hue does, by
 * under five levels out of 255 for the worst mix in this legend.
 *
 * <h2>Where the thickness comes from</h2>
 * Not from {@code RenderSystem.lineWidth} on its own, which in 1.21.1 does not reach the driver at all:
 * {@code GlStateManager} has no {@code glLineWidth}, and {@code RenderSystem.lineWidth} only stores a
 * value that {@code ShaderInstance.setDefaultUniforms} forwards to a {@code LineWidth} uniform — and
 * only when the draw mode is {@code LINES} or {@code LINE_STRIP}. The outline pass therefore draws in
 * {@link VertexFormat.Mode#LINES} with {@link DefaultVertexFormat#POSITION_COLOR_NORMAL} and the
 * {@code rendertype_lines} shader, which is real geometry: {@code BufferBuilder} duplicates every vertex
 * in that mode, the shared quad index buffer turns each pair into a quad, and the vertex shader spreads
 * that quad perpendicular to the segment <em>in screen space</em> by {@code LineWidth / ScreenSize}.
 * Width is then a constant number of framebuffer pixels at any range, identical on every driver.
 *
 * <p>That screen-space expansion is also why there is no distance ramp on {@link #LINE_WIDTH}: a
 * world-space thickness (each edge extruded into a thin box) would get thinner with distance, which is
 * backwards, and {@code LineWidth} is a uniform — one value per draw call — so a per-box width would
 * cost one draw call per box. The one thing that does scale is the display: the uniform is in
 * framebuffer pixels, so {@link #lineWidth()} scales against a 1920px reference the way vanilla's own
 * {@code LineStateShard} does, or the overlay would hairline on a 4K monitor.
 *
 * <h2>GL state contract</h2>
 * {@link #beginOverlayPass()} sets and {@link #endOverlayPass()} restores, in full: fog start (saved and
 * put back verbatim — {@code rendertype_lines} fogs where {@code position_color} did not, and the level's
 * fog is still live at this stage, so an unmodified overlay would fade out at range and tint underwater),
 * depth test (off, restored on), depth mask (off, restored on), cull (off, restored on), blend (on with
 * the default func, restored off), and the line width (restored to 1.0). The shader is left set, exactly
 * as before; every stage that follows (clouds, weather, world border, debug) calls
 * {@code RenderSystem.setShader} itself.
 *
 * <p>Both callers pair the two through {@code try/finally}, which is what makes "restores, in full" a
 * contract rather than a hope. The saved fog start is the reason it matters more than it looks: it lives in
 * a static, so a throw that skipped the restore would leave the live value at {@code Float.MAX_VALUE}, and
 * the <em>next</em> frame would dutifully save that as the original — unfogged clouds and weather for the
 * rest of the session, from one bad frame.
 *
 * <p>Culling is the one item the two passes disagree about, so the pass owns "off" and {@link #render}
 * borrows "on" for the fills alone, through a nested {@code try/finally} of its own. The outlines need it
 * off: {@code LINES} expands to triangles whose winding depends on which way the segment happens to run,
 * so half the edges of every box would drop out with culling on. The old {@code DEBUG_LINES} pass was
 * immune because GL never culls actual lines, which is exactly the sort of assumption that quietly stops
 * holding when the mode changes — vanilla's own {@code RenderType.lines()} carries {@code NO_CULL} for
 * the same reason. The fills want the opposite, for the reason in the stacking rule above; their quads
 * are wound the way every vanilla block face is (see {@link #face}), and Minecraft never calls
 * {@code glFrontFace} or {@code glCullFace}, so the GL defaults decide and they decide correctly.
 *
 * <p>Depth test is the one item that is a convention rather than a true restore: the flag lives in
 * {@code GlStateManager.DEPTH}, which is private with no getter, so its prior value cannot be read back.
 * Restoring it <em>on</em> is safe here because every stage vanilla runs after this event establishes its
 * own depth state before drawing — clouds through their render type's {@code setupRenderState}, weather
 * and the world border with explicit {@code enableDepthTest} calls. The depth mask is switched off for
 * the same reason it is documented here at all: the depth test being off already means GL writes no
 * depth, but a filled pass is exactly the change that would make a future "fix" here corrupt the cloud
 * and weather sort, so it is now explicit instead of implied.
 */
public final class SelectionOverlayRenderer {
    private static final float[] MAIN_COLOR = {0.96f, 0.69f, 0.26f}; // gold - the selected block itself
    /**
     * The fills do not reuse the outline colors, because a color picked to read as a thin bright line is
     * the wrong color for a low-alpha wash: a line only has to differ from its background, a wash has to
     * shift it. Outline gold over sandstone moved blue by 0.06, so the selection all but vanished on
     * desert builds. Every fill variant is therefore the same hue pushed darker and more saturated, so
     * the wash moves luminance as well as hue and roughly doubles the shift; the outlines on top are
     * untouched, so the legend in {@link Kind} still describes what is drawn. This one and
     * {@link Kind#DOOR}'s are unchanged from when they were the only two.
     *
     * <p>The variants are also spread further apart in hue than the outlines are, because a wash is
     * judged by the direction it pulls the background and near neighbors collapse into each other at
     * these alphas in a way they never do at full alpha. Gold is the fixed point the others move around:
     * entrance orange goes red-side of it and mob-spawn yellow goes green-side, which is the tightest
     * pair left and still a 0.3 split in the green channel — and those two are point kinds, tinting at
     * {@link #POINT_FILL_ALPHA} against the selection's {@link #VOLUME_FILL_ALPHA}, so they differ in
     * strength as well as hue. The selection is in any case the one box wearing a doubled outline, so it
     * is never identified by its tint alone.
     */
    private static final float[] MAIN_FILL_COLOR = {1.00f, 0.60f, 0.05f};
    /** The active mode's own targets, and the selection. */
    private static final float ALPHA = 0.95f;
    /**
     * Everything else attached to the selection, so the active mode still reads first. Not lower than
     * this: the cool colors blend away against a bright background (sky seen through a ceiling) below
     * roughly 0.6, and an attachment that cannot be seen reads as an attachment that is not there.
     */
    private static final float DIM_ALPHA = 0.60f;
    /**
     * Outline thickness in framebuffer pixels at a 1080px-tall viewport, scaled up from there by
     * {@link #lineWidth()}. The yardstick a builder actually compares this against is vanilla's own
     * block-highlight outline, which is {@code max(2.5, width/1920 * 2.5)} — four is a decisive 1.6x
     * that, where three would be a 1.2x nobody reads as "thicker".
     *
     * <p>Every gap in this class is sized against this number and has to move with it: at 1080p and the
     * default 70 vertical FOV one block is {@code 771/d} pixels at {@code d} blocks, so a world-space gap
     * {@code g} survives out to {@code g * 771 / LINE_WIDTH} blocks before the two lines meet. That is
     * what sets the {@link #INSET_STEP} and {@link #SELECTION_INFLATE_PER_BLOCK}. Raising the width alone
     * silently merges both.
     */
    private static final float LINE_WIDTH = 4.0f;
    /**
     * The gap between two neighboring {@linkplain Kind#inset kind depths}, and a function of
     * {@link #LINE_WIDTH} rather than a taste choice: at 1080p a 0.08 gap is {@code 0.08 * 771 / d}
     * pixels, so it stays wider than a four-pixel line out to 15 blocks and stacked kinds stay concentric
     * over the range a builder works in. Two kinds share an edge past that. Do not shrink the step or
     * widen the line without redoing that sum — the old 0.04 step was sized against the one-pixel
     * hairlines this overlay used to draw, and at the present width it would merge three stacked kinds
     * into one muddy band from 10 blocks out.
     */
    private static final float INSET_STEP = 0.08f;
    /**
     * Tint strength for a cell that marks a real block in the world — a link, either room marker, a
     * doorway — and for the selection, at full {@link #ALPHA}; a dimmed box scales this down in the same
     * proportion. Deliberately far below the outline alphas: the fill exists to say "this volume is the
     * thing the outline names", not to hide what a builder is placing behind it.
     *
     * <p>This is what lands on screen, not a per-face value: the fill pass culls back faces (see the
     * class javadoc), so a view ray through a closed fill is tinted once per time it enters one, and a
     * cell is only ever filled by one kind. 0.19 is not a fresh taste call either — it is exactly
     * {@code 1-(1-0.10)^2}, the composite the old two-layer, unculled, 0.10-per-face fill landed on, so
     * from anywhere outside the volume doors and the selection are pixel-identical to what they were
     * when only they were filled. Concave door groups included: a ray crossing two arms drew four faces
     * at 0.10 for 0.34, and now draws two front faces at 0.19 for the same 0.34. From <em>inside</em> a
     * fill they are not identical, and deliberately so: culling makes a volume the camera is within draw
     * nothing at all, where the unculled pass still showed its far faces at point-blank range. That is
     * the improvement, and it is why the camera-inside-cell skip in {@link #addFill} is now a guard
     * rather than the mechanism.
     */
    private static final float VOLUME_FILL_ALPHA = 0.19f;
    /**
     * Tint strength for a cell that marks a stored <em>position</em> rather than a block — mob and boss
     * spawns, entrances, respawn points, protect targets. Half the volume value, because these are the
     * kinds that stack along a view ray: they are the numerous ones (a Room Controller draws every
     * linked spawner's every spawn offset), and every one of them is stored at {@code clickedPos.above()},
     * so a room's markers sit in one sheet a block above its floor that a builder only has to tilt a few
     * degrees to look along — lining eight of them up is a glance, not a contrived case (see the class
     * javadoc). At 0.10 those eight hide 57% of what is behind them instead of 82%, which is a wash rather
     * than a curtain. The outline, four framebuffer pixels of near-opaque color, is what identifies these
     * kinds; their tint only has to say which block.
     */
    private static final float POINT_FILL_ALPHA = 0.10f;
    /**
     * Hard ceiling on the fill pass, in cells, since neither the number of links a controller holds nor
     * the number of spawn positions a room reaches is bounded by anything this class controls. Six quads
     * per cell is 3072 quads, 12288 vertices, about 192 KB of vertex data in one draw call — and culling
     * throws away the back-facing half before rasterizing. That is the whole pass, doors included: a door
     * contributes its group's cells like any other kind, at no more than six faces each.
     *
     * <p>512 is far past anything real (a heavy room is a few hundred cells with its spawners, their
     * spawn positions, its respawn points and its doorways). When it does bite, {@link #resolveFills}
     * has already spent it in rank order, so what goes untinted is whatever ranks last — the inactive
     * inset kinds, never the selection and never the mode's own targets. A door group is the one thing
     * the cut cannot land inside: half a group is a hole rather than a smaller doorway, so a group that
     * does not fit whole is dropped whole and keeps its outline, exactly as an oversized one does.
     * Outlines are not capped and never disappear.
     */
    private static final int FILL_CELL_BUDGET = 512;
    /** All six faces of a cell; see {@link Fill#faceMask}. */
    private static final int ALL_FACES = 0b111111;
    /** Above every {@link Kind}: wherever the selection lands, the selection is what the cell means. */
    private static final int SELECTION_FILL_PRIORITY = 0;
    /**
     * Doors, above every other {@link Kind} including the active one. Not a claim that a doorway matters
     * more than what a builder is placing, but the only fill whose cells are a shared closed surface: a
     * cell lost to another kind is a hole in it (see {@link #resolveFills}), where a cell lost anywhere
     * else is just one block washed in a neighbor's color. Losing a doorway's tint to the mode's own kind
     * would cost the whole group, not one cell, so the door keeps it and the single cell underneath gives
     * it up.
     */
    private static final int DOOR_FILL_PRIORITY = 1;
    /** The active mode's own kind, so the tint agrees with the {@link #DIM_ALPHA} tier the outlines use. */
    private static final int ACTIVE_FILL_PRIORITY = 2;
    /** Everything else, ranked outward-in from here by {@linkplain Kind#inset depth}. */
    private static final int INSET_FILL_PRIORITY = 3;
    /**
     * The selection draws twice, the second box pushed outside the block, giving a doubled outline that
     * reads as the anchor. Pulsing the alpha would work too, but flicker already means "mobs spawn here
     * imminently" ({@link SpawnTelegraphRenderer}) and a static outline is calmer to build against.
     * A second pass at a wider {@code LineWidth} is now genuinely available — the outline pass really
     * does control its own thickness (see the class javadoc) — but it would cost a second draw call and
     * a second mesh to say what the gap already says, and the gap survives being seen through a wall
     * where a few extra pixels of the same color do not.
     *
     * <p>This is the minimum gap, held at close range; {@link #selectionOutset} widens it with distance,
     * since a fixed gap shrinks to under two pixels past about 40 blocks and the two outlines merge back
     * into one ordinary-looking line exactly when the selection is hardest to find. Chosen so the floor
     * meets the ramp exactly at 10 blocks rather than stepping down into it.
     */
    private static final float SELECTION_INFLATE = 0.12f;
    /**
     * Widest the doubled outline opens up. Past half a block it reaches into the neighboring blocks, which
     * is fine and deliberate: the ramp only gets here beyond 58 blocks, where a one-block selection is 13
     * pixels across and "which block exactly" is the inner outline's job anyway, not this one's.
     */
    private static final float SELECTION_INFLATE_MAX = 0.7f;
    /**
     * Blocks of outset per block of camera distance, so the doubling survives at range: this holds the two
     * gold outlines {@code 0.012 * 771 = 9.3} pixels apart at 1080p, leaving 5 pixels of background
     * between them at {@link #LINE_WIDTH}. It scales with the line width, not with the viewport — at the
     * old 0.006 a four-pixel line would close the gap entirely and the doubling would say nothing.
     */
    private static final double SELECTION_INFLATE_PER_BLOCK = 0.012;
    /** Second, deeper outline on a door whose anchor is no longer a phase block: reads as hatched. */
    private static final float BROKEN_DOOR_INSET = -0.14f;

    /** Cached because {@link Direction#values()} allocates, and this runs per block face. */
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * The overlay's color legend. One color per relationship, shared by every block type: a room's
     * respawn points and a raid boss spawner's respawn points are the same cyan, and so on. Each constant
     * carries its outline color, then its {@linkplain #fillColor fill variant} of that color, then that
     * fill's {@linkplain #fillAlpha strength}, then its inset — the inset nesting each kind at its own
     * depth inside the block, so an entrance and a respawn point placed on the same block read as two
     * concentric squares instead of one muddy blended box. Every position mode stores
     * {@code clickedPos.above()}, so coincident positions are routine, not a corner case; the inset is
     * also what ranks two inactive kinds for the tint, since only one of them gets it (see
     * {@link #fillPriority}).
     *
     * <p>The tint itself is always the whole block, whatever the kind's inset: the insets exist to keep
     * the <em>outlines</em> concentric, and a wash shrunk to the PROTECT outline would be an eighth of a
     * block face — a smudge under a line rather than a filled block. So all eight kinds get the same
     * side fill the doors and the selection have always had, and what varies between them is the color
     * and the strength, not the size.
     *
     * <p>The step between the insets is {@link #INSET_STEP}, and it is sized against
     * {@link #LINE_WIDTH} — see that constant before moving any of these.
     */
    private enum Kind {
        /** A block the selection links to: a controller's instance, a boss spawner's room, a room's
         *  spawner. The fill is a pure green pushed toward blue rather than the outline's softer green,
         *  because the background it most often has to beat is a grass floor: the outline green over
         *  plains grass moves luminance by 7 levels out of 255, which is nothing, and this moves it by
         *  12 while widening the split against MOB_SPAWN yellow — the pair on screen most often. */
        LINK(0.53f, 0.83f, 0.42f, 0.00f, 1.00f, 0.35f, VOLUME_FILL_ALPHA, 0.00f),          // green
        /** The boss spawner's marked start room. Azure, not a second green: it is drawn among the other
         *  rooms, which are all LINK green, and two greens are one green at this line width. The fill
         *  drops the red out entirely, which is what separates it from DOOR violet as a wash. */
        START_ROOM(0.25f, 0.45f, 1.00f, 0.00f, 0.30f, 1.00f, VOLUME_FILL_ALPHA, 0.00f),    // azure
        /** The boss spawner's marked final (boss) room. Filled toward magenta rather than straight red,
         *  because the wash it shares a boss spawner's screen with is ENTRANCE orange, which starts from
         *  nearly the same red: the separation has to be bought in blue, and at the outline's 1.00/0.00
         *  /0.45 there was not enough of it. The two also differ in strength now, one being a volume kind
         *  and the other a point kind, but hue should not have been leaning on that. */
        FINAL_ROOM(1.00f, 0.35f, 0.60f, 0.85f, 0.00f, 0.65f, VOLUME_FILL_ALPHA, 0.00f),    // rose
        /** A room door, boxed and tinted over its whole flood-filled phase-block group. Outline violet is
         *  close to sky blue and a doorway is usually seen against sky or a lit opening — at the tint
         *  alpha it moved green by 0.04, which is nothing, hence the much bluer fill. The one kind whose
         *  fill is many cells, which is why it gets {@link #DOOR_FILL_PRIORITY} to itself. */
        DOOR(0.55f, 0.50f, 0.95f, 0.40f, 0.25f, 1.00f, VOLUME_FILL_ALPHA, 0.00f),          // violet
        /** A player respawn point. The fill drops red to zero: against LINK green's fill, which has
         *  little blue, the pair is a 0.6 split in the blue channel and survives as a wash. */
        RESPAWN(0.30f, 0.80f, 0.90f, 0.00f, 0.80f, 0.95f, POINT_FILL_ALPHA, -0.24f),       // cyan
        /** The absolute entrance position players are teleported to. Filled red-side of the selection's
         *  gold, which is the neighbor it has to stay clear of. */
        ENTRANCE(0.95f, 0.45f, 0.20f, 1.00f, 0.22f, 0.00f, POINT_FILL_ALPHA, -0.16f),      // orange
        /** A room objective's PROTECT target spawn. The innermost box, so it is the one the inset step
         *  is bounded by: at -0.32 a single block is still 0.36 across, a readable square rather than
         *  four borders touching. Being innermost, it is also the first tint to lose its cell to another
         *  kind whenever neither is the active one, which is the right way round — a protect target is a
         *  point in someone else's volume. */
        PROTECT(0.90f, 0.30f, 0.85f, 0.95f, 0.00f, 0.95f, POINT_FILL_ALPHA, -0.32f),       // magenta
        /** A mob or boss spawn position, on the spawner that owns it. Yellow rather than white: these
         *  are the most numerous boxes on screen and white washes out over sand, snow or open sky. The
         *  fill leans green-side of gold for the same reason ENTRANCE leans red-side of it. Most
         *  numerous also means most stacked, which is what {@link #POINT_FILL_ALPHA} is sized for. */
        MOB_SPAWN(1.00f, 0.95f, 0.20f, 0.85f, 0.90f, 0.00f, POINT_FILL_ALPHA, -0.08f),     // yellow
        /** A spawn position of a room's spawner other than the picked acting one. Red so the two are
         *  told apart by hue, not only by the dim tier: full-strength yellow is what the next click
         *  edits, red belongs to the other spawners. Never any mode's own kind, so it always sits in
         *  the dim tier and ranks by inset — one step deeper than MOB_SPAWN, so a block shared by two
         *  spawners shows both outlines instead of stacking them. */
        OTHER_MOB_SPAWN(1.00f, 0.28f, 0.22f, 0.95f, 0.12f, 0.08f, POINT_FILL_ALPHA, -0.12f); // red

        private final float[] color;
        private final float[] fillColor;
        private final float fillAlpha;
        private final float inset;

        Kind(float r, float g, float b, float fr, float fg, float fb, float fillAlpha, float inset) {
            this.color = new float[]{r, g, b};
            this.fillColor = new float[]{fr, fg, fb};
            this.fillAlpha = fillAlpha;
            this.inset = inset;
        }

        /**
         * Rank for {@link #resolveFills}: which kind keeps the tint when several land on one block, lower
         * being stronger. Doors first, because a door's cells are one closed surface and a cell taken out
         * of it is a hole rather than a smaller doorway; then the mode's own kind, so the tint follows
         * the {@link #DIM_ALPHA} tier the outlines already follow and a builder is never shown the block
         * they are placing washed in a dimmed neighbor's color; then the rest outward-in, read straight
         * off the inset rather than stored as a second number, so that part of the rule cannot drift away
         * from the geometry it describes. The two room markers count as active in every mode (see
         * {@link #isActive}), so they rank with the mode's own kind rather than with LINK at inset 0 —
         * which can only matter in theory, since all three come out of one mutually exclusive list.
         */
        private int fillPriority(DungeonToolItem.Mode mode) {
            if (this == DOOR) {
                return DOOR_FILL_PRIORITY;
            }
            if (isActive(this, mode)) {
                return ACTIVE_FILL_PRIORITY;
            }
            return INSET_FILL_PRIORITY + Math.round(-inset / INSET_STEP);
        }

    }

    /** How many rounds {@link #resolveFills} has to run. Derived, so a new kind cannot be forgotten. */
    private static final int LOWEST_FILL_PRIORITY = lowestFillPriority();

    private static int lowestFillPriority() {
        // The deepest inset rank, which is the lowest any kind can reach: DOOR and the active kind only
        // ever rank above it, so neither has to be asked here and no mode has to be picked to ask with.
        int lowest = SELECTION_FILL_PRIORITY;
        for (Kind kind : Kind.values()) {
            lowest = Math.max(lowest, INSET_FILL_PRIORITY + Math.round(-kind.inset / INSET_STEP));
        }
        return lowest;
    }

    private SelectionOverlayRenderer() {
    }

    public static void register() {
        ArenasClientEvents.AFTER_TRANSLUCENT.add(SelectionOverlayRenderer::render);
    }

    /**
     * Inclusive block-coordinate bounds; a single block is {@code min == max}. {@code inflate} pushes the
     * outline that many blocks outward in every direction, negative values pulling it inward.
     */
    private record Box(BlockPos min, BlockPos max, float[] color, float alpha, float inflate) {
    }

    /**
     * One block cell's worth of translucent tint, drawn under the outlines. The cell is the unit the
     * whole stacking rule works in — one cell, one fill — so there is no multi-block fill: a door hands
     * in one of these per cell of its group instead of a bounding box, since a bounding box is a claim
     * about a volume and a filled one over an L-shaped or arched group would paint its empty corner as
     * doorway.
     *
     * <p>{@code faceMask} selects which of {@link #DIRECTIONS} to draw — {@link #ALL_FACES} for a stored
     * position, a link, a room or the selection, and only the group-exterior ones for a cell of a door,
     * so the faces a door shares with itself never double up. There is no inset on any of it: every fill
     * is the whole block, for the reason in {@link Kind}'s javadoc.
     *
     * <p>{@code alpha} is the owning box's outline alpha, not the fill's, so the mode dimming carries
     * through without a second alpha tier to sync; {@code tint} is the owning kind's
     * {@linkplain Kind#fillAlpha strength}, and {@link #addFill} multiplies the two. {@code priority} is
     * the kind's {@linkplain Kind#fillPriority rank}. {@code group} is 0 for every kind but
     * {@link Kind#DOOR}, whose cells share an id so {@link #resolveFills} can keep a group whole; see
     * {@link Sink#addDoor} for where the id comes from and why a group's cells are contiguous.
     */
    private record Fill(BlockPos pos, int faceMask, float[] color, float alpha, float tint,
                        int priority, int group) {
    }

    /** One cell of a door group and the outward faces it contributes, as an {@link #ALL_FACES} bit set. */
    private record Cell(BlockPos pos, int faceMask) {
    }

    /** The frame's geometry, split by pass: fills go down first, outlines on top. */
    private record Overlay(List<Box> boxes, List<Fill> fills) {
        boolean isEmpty() {
            return boxes.isEmpty() && fills.isEmpty();
        }
    }

    /**
     * Inclusive block-coordinate bounds of a flood-filled door group, cached between frames, plus the
     * group's exterior faces per cell when it was small enough to fill ({@link #DOOR_FILL_CELL_LIMIT});
     * null {@code cells} means outline only. {@code intact} is false when the stored anchor is no longer
     * a phase block — a doorway that was rebuilt or moved — which {@link RoomGraph#expandDoorGroup}
     * reports as a lone-anchor group.
     */
    private record Bounds(BlockPos min, BlockPos max, boolean intact, @Nullable List<Cell> cells) {
    }

    private static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        Vec3 cam = context.camera().getPosition();
        Overlay overlay = collectBoxes(mc, player, cam);
        if (overlay.isEmpty()) {
            return;
        }

        PoseStack poseStack = context.matrixStack();
        poseStack.pushPose();
        // The pop is unconditional too: vanilla's PoseStack outlives this event and every stage after it
        // draws through the same one, so an unbalanced push would survive a throw that the GL restore
        // below handles cleanly and corrupt the rest of the frame instead.
        try {
            poseStack.translate(-cam.x, -cam.y, -cam.z);
            Matrix4f matrix = poseStack.last().pose();

            // The cell the camera sits in. With the fill pass culling back faces, a volume the camera is
            // inside already draws nothing — every face reachable from in there points away from the eye
            // — so this is a guard behind that rather than the mechanism it was when the pass was
            // unculled: it is what keeps a flipped winding convention from painting the inside of the
            // builder's own head. Exact now that every fill is the whole block, and one skipped cell
            // never the whole fill.
            BlockPos camBlock = BlockPos.containing(cam);

            beginOverlayPass();
            try {
                // Fills first, outlines second: with the depth test off, submission order is the only
                // sort there is. The two passes share one Tesselator, so each has to be built and drawn
                // before the next begins — a second begin() while a builder is open discards it.
                if (!overlay.fills().isEmpty()) {
                    // Borrowed from the pass contract for this draw call only, and handed straight back:
                    // the outlines below need culling off or half their edges vanish, while the fills
                    // need it on or every closed volume tints twice. Nested try/finally for the same
                    // reason the outer one exists — a throw here must not leak cull state into the rest
                    // of the frame. Do not move the outline draw inside this block: it would silently
                    // eat half of every box's edges.
                    RenderSystem.enableCull();
                    try {
                        BufferBuilder buffer = beginFills();
                        for (Fill fill : overlay.fills()) {
                            addFill(buffer, matrix, fill, camBlock);
                        }
                        drawMesh(buffer);
                    } finally {
                        RenderSystem.disableCull();
                    }
                }
                if (!overlay.boxes().isEmpty()) {
                    BufferBuilder buffer = beginLines();
                    for (Box box : overlay.boxes()) {
                        addBoundsEdges(buffer, matrix, box.min(), box.max(),
                            box.color(), box.alpha(), box.inflate());
                    }
                    drawMesh(buffer);
                }
            } finally {
                // Unconditional, because beginOverlayPass() saves state into a static: on a throw between
                // the two, the next frame would save the *modified* fog start as if it were the original
                // and leave clouds and weather unfogged for the rest of the session.
                endOverlayPass();
            }
        } finally {
            poseStack.popPose();
        }
    }

    // ---- Shared pass plumbing (SpawnTelegraphRenderer draws through this too) ----

    /**
     * Fog start as it was when {@link #beginOverlayPass()} ran. A static is enough: this is render-thread
     * only and the two listeners at this stage each open and close their pass inside their own callback,
     * so the passes never interleave.
     */
    private static float savedFogStart;

    /**
     * Through-wall, blended, unculled, depth-write-free, unfogged. Always pair with
     * {@link #endOverlayPass()}; see the class javadoc for exactly what is set, what is put back, and why
     * culling has to be off for the outlines even though the fill pass turns it back on for itself.
     */
    static void beginOverlayPass() {
        // linear_fog returns its input untouched below fogStart, so pushing the start out is a complete
        // and reversible way to switch fog off for these two passes — cheaper and narrower than
        // FogRenderer.setupNoFog(), which would also have to be undone by re-deriving the level's fog.
        savedFogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** Restores everything {@link #beginOverlayPass()} touched, in reverse. */
    static void endOverlayPass() {
        RenderSystem.lineWidth(1.0F);
        RenderSystem.setShaderFogStart(savedFogStart);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** Opens the outline mesh. The format, mode and shader have to agree — see the class javadoc. */
    static BufferBuilder beginLines() {
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(lineWidth());
        return Tesselator.getInstance()
            .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
    }

    /** Opens the filled-face mesh. {@code position_color} carries no fog and discards alpha-zero. */
    private static BufferBuilder beginFills() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        return Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    /**
     * Builds and draws, and releases the shared Tesselator buffer: {@code drawWithShader} closes the
     * {@link MeshData} for us on both the success and the throw path, so the next {@code begin()} starts
     * from offset zero.
     */
    static void drawMesh(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * {@link #LINE_WIDTH} in the units the shader actually wants. The {@code ScreenSize} uniform is fed
     * from the window's framebuffer size, so the width is in framebuffer pixels and a fixed value halves
     * on a 2x display, which is why it scales at all.
     *
     * <p>Against <em>height</em>, though, where vanilla's {@code LineStateShard} uses
     * {@code max(2.5, width/1920 * 2.5)}. Minecraft's FOV option is the vertical one, so the number of
     * pixels a block covers is set by the framebuffer height alone; scaling the line off the width
     * instead only tracks it at 16:9. On an ultrawide the width term runs ahead of the geometry and the
     * line comes out a third fatter relative to what it is outlining, which is exactly where the nested
     * inset boxes have the least room to give. Height keeps the ratio — and therefore every gap computed
     * against {@link #LINE_WIDTH} — identical at 1080p, 1440p, 4K and 21:9 alike.
     *
     * <p>The floor is deliberate: below 1080p tall the line stops thinning, because a two-pixel overlay
     * is not an overlay. Small windows pay for that in gap distances, and the insets absorb it.
     */
    private static float lineWidth() {
        int framebufferHeight = Minecraft.getInstance().getWindow().getHeight();
        return Math.max(LINE_WIDTH, framebufferHeight / 1080.0f * LINE_WIDTH);
    }

    // ---- Geometry ----

    /** Shared with SpawnTelegraphRenderer, hence package-private and alpha-parameterized. */
    static void addBoxEdges(BufferBuilder buffer, Matrix4f matrix, BlockPos pos, float[] color, float alpha) {
        addBoundsEdges(buffer, matrix, pos, pos, color, alpha, 0.0f);
    }

    /**
     * Outline of the block region spanning {@code min}..{@code max} inclusive, pushed {@code inflate}
     * blocks outward on every side.
     */
    private static void addBoundsEdges(BufferBuilder buffer, Matrix4f matrix, BlockPos min, BlockPos max,
                                       float[] color, float alpha, float inflate) {
        float x0 = min.getX() - inflate;
        float y0 = min.getY() - inflate;
        float z0 = min.getZ() - inflate;
        float x1 = max.getX() + 1 + inflate;
        float y1 = max.getY() + 1 + inflate;
        float z1 = max.getZ() + 1 + inflate;
        float r = color[0];
        float g = color[1];
        float b = color[2];

        // bottom face
        edge(buffer, matrix, x0, y0, z0, x1, y0, z0, r, g, b, alpha);
        edge(buffer, matrix, x1, y0, z0, x1, y0, z1, r, g, b, alpha);
        edge(buffer, matrix, x1, y0, z1, x0, y0, z1, r, g, b, alpha);
        edge(buffer, matrix, x0, y0, z1, x0, y0, z0, r, g, b, alpha);
        // top face
        edge(buffer, matrix, x0, y1, z0, x1, y1, z0, r, g, b, alpha);
        edge(buffer, matrix, x1, y1, z0, x1, y1, z1, r, g, b, alpha);
        edge(buffer, matrix, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
        edge(buffer, matrix, x0, y1, z1, x0, y1, z0, r, g, b, alpha);
        // verticals
        edge(buffer, matrix, x0, y0, z0, x0, y1, z0, r, g, b, alpha);
        edge(buffer, matrix, x1, y0, z0, x1, y1, z0, r, g, b, alpha);
        edge(buffer, matrix, x1, y0, z1, x1, y1, z1, r, g, b, alpha);
        edge(buffer, matrix, x0, y0, z1, x0, y1, z1, r, g, b, alpha);
    }

    private static void edge(BufferBuilder buffer, Matrix4f matrix,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float r, float g, float b, float alpha) {
        // rendertype_lines widens a segment by transforming both Position and Position + Normal and
        // spreading the quad perpendicular to the screen-space difference, so Normal must be the
        // segment's unit direction — and every element of the format must be set or BufferBuilder throws
        // "Missing elements in vertex". Every edge here is axis-aligned, so exactly one delta is non-zero
        // and signum is that unit direction without a square root. No edge is ever degenerate: the
        // deepest inset (PROTECT at -0.32) still leaves a single block 0.36 across, so the shader never
        // normalizes a zero vector. The pose is a pure camera translation, which leaves normals alone.
        float nx = Math.signum(bx - ax);
        float ny = Math.signum(by - ay);
        float nz = Math.signum(bz - az);
        buffer.addVertex(matrix, ax, ay, az).setColor(r, g, b, alpha).setNormal(nx, ny, nz);
        buffer.addVertex(matrix, bx, by, bz).setColor(r, g, b, alpha).setNormal(nx, ny, nz);
    }

    /**
     * One cell's faces, on the block's own bounds. Skipped entirely when the camera is in this block —
     * see {@link #render} for why that is a guard behind back-face culling rather than the thing that
     * makes standing in a doorway comfortable, and why the test being the whole block is now exactly
     * right rather than merely conservative. The outline is drawn either way, so a skipped cell loses
     * only its wash.
     *
     * <p>No outward offset on any of this. An earlier revision nudged the faces out by a hair against a
     * supposed shimmer from {@code rendertype_lines}' {@code VIEW_SHRINK}, which was a misreading: that
     * factor scales the view-space position uniformly about the camera, so it cancels in the perspective
     * divide ({@code ndc.x = a*s*Px / (-s*Pz)}) and moves nothing on screen — it biases {@code ndc.z}
     * only, and this pass neither tests nor writes depth. The offset bought nothing and cost a sliver of
     * double-blend along every shared edge of a per-block fill, so the faces sit on the block bounds.
     */
    private static void addFill(BufferBuilder buffer, Matrix4f matrix, Fill fill, BlockPos camBlock) {
        BlockPos pos = fill.pos();
        if (pos.equals(camBlock)) {
            return;
        }
        float r = fill.color()[0];
        float g = fill.color()[1];
        float b = fill.color()[2];
        // Carry the mode dimming into the tint, so a door drawn dim is tinted dim.
        float alpha = fill.tint() * fill.alpha() / ALPHA;

        float x0 = pos.getX();
        float y0 = pos.getY();
        float z0 = pos.getZ();
        float x1 = x0 + 1;
        float y1 = y0 + 1;
        float z1 = z0 + 1;
        int mask = fill.faceMask();
        for (int i = 0; i < DIRECTIONS.length; i++) {
            // The mask is built from Direction.ordinal(), which is by definition this array's index.
            if ((mask & (1 << i)) != 0) {
                face(buffer, matrix, DIRECTIONS[i], x0, y0, z0, x1, y1, z1, r, g, b, alpha);
            }
        }
    }

    /**
     * One face of the box spanning {@code x0,y0,z0}..{@code x1,y1,z1}. Wound counter-clockwise seen from
     * outside, exactly like a vanilla block face, which is what lets {@link #render} cull back faces on
     * the fill pass and get one tint layer per volume instead of two.
     */
    private static void face(BufferBuilder buffer, Matrix4f matrix, Direction dir,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float r, float g, float b, float alpha) {
        switch (dir) {
            case DOWN -> quad(buffer, matrix, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, alpha);
            case UP -> quad(buffer, matrix, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
            case NORTH -> quad(buffer, matrix, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, alpha);
            case SOUTH -> quad(buffer, matrix, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
            case WEST -> quad(buffer, matrix, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, alpha);
            case EAST -> quad(buffer, matrix, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, alpha);
        }
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float alpha) {
        buffer.addVertex(matrix, ax, ay, az).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, bx, by, bz).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, cx, cy, cz).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, dx, dy, dz).setColor(r, g, b, alpha);
    }

    // ---- Collection ----

    private static Overlay collectBoxes(Minecraft mc, LocalPlayer player, Vec3 cam) {
        Overlay overlay = new Overlay(new ArrayList<>(), new ArrayList<>());
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        ItemStack tool = main.getItem() instanceof DungeonToolItem ? main
            : off.getItem() instanceof DungeonToolItem ? off : null;
        if (tool != null) {
            collectDungeonTool(mc, tool, cam, overlay);
        }
        return overlay;
    }

    /**
     * Everything attached to the selected block, whatever the mode, then the selection on top of it.
     * The selection draws last so its gold outline wins wherever a target box lands on the same block.
     */
    private static void collectDungeonTool(Minecraft mc, ItemStack stack, Vec3 cam, Overlay overlay) {
        DungeonToolDataComponent data = stack.getOrDefault(
            DataComponentRegistry.DUNGEON_TOOL_DATA, DungeonToolDataComponent.DEFAULT);
        Optional<BlockPos> posOpt = data.sourcePos();
        if (posOpt.isEmpty() || !dimensionMatches(mc, data.sourceDimension())) {
            return;
        }
        BlockPos sourcePos = posOpt.get();
        BlockEntity be = mc.level.getBlockEntity(sourcePos);
        // Loaded but empty: the selected block was broken or replaced, and the tool will reject the next
        // click. A gold box on plain stone is worse than showing nothing. An unloaded chunk is different
        // — the attachments are unreadable, but the selection still draws so it stays findable.
        if (be == null && mc.level.isLoaded(sourcePos)) {
            return;
        }
        // A room source in MOB_SPAWN_POSITION mode acts through its picked spawner. The room's
        // full overlay stays — doors, respawn points, every linked spawner and its spawn
        // positions — and the picked spawner gets a second gold selection on top of it (added
        // after the room's own, below), marking where world clicks land.
        BlockPos pickedPos = null;
        if (DungeonToolItem.selectedMode(data) == DungeonToolItem.Mode.MOB_SPAWN_POSITION
                && be instanceof net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity room) {
            BlockEntity picked = DungeonToolItem.resolveRoomSpawner(mc.level, room, data);
            if (picked != null) {
                pickedPos = picked.getBlockPos();
            }
        }
        List<Box> boxes = overlay.boxes();
        List<Fill> fills = overlay.fills();
        if (be != null) {
            Sink sink = new Sink(mc, DungeonToolItem.selectedMode(data), boxes, fills);
            collectAttachments(sink, sourcePos, be, pickedPos);
            // The selection keeps its mode-agnostic attachments, so a mode that owns none of them (any
            // controller outside LINK mode, for one) would leave the whole overlay dimmed, which reads as
            // a lost selection. Drop the dimming instead. Must run before the gold boxes are appended.
            if (noneActive(boxes)) {
                boxes.replaceAll(box -> new Box(box.min(), box.max(), box.color(), ALPHA, box.inflate()));
                // Only the alpha: the ranks stay as they were, since with nothing active none of them is
                // ACTIVE_FILL_PRIORITY in the first place and the order is already the geometric one.
                fills.replaceAll(fill -> new Fill(fill.pos(), fill.faceMask(), fill.color(), ALPHA,
                    fill.tint(), fill.priority(), fill.group()));
            }
        }
        // The selection is tinted like everything else, under both gold outlines, which still carry the
        // doubling that identifies it — and it outranks every kind for its own cell, so a spawn position
        // or a protect target stored on the selected block never washes the anchor a different color.
        float outset = selectionOutset(cam, sourcePos);
        fills.add(new Fill(sourcePos, ALL_FACES, MAIN_FILL_COLOR, ALPHA, VOLUME_FILL_ALPHA,
            SELECTION_FILL_PRIORITY, 0));
        boxes.add(new Box(sourcePos, sourcePos, MAIN_COLOR, ALPHA, 0.0f));
        boxes.add(new Box(sourcePos, sourcePos, MAIN_COLOR, ALPHA, outset));
        // The acting spawner gets the same doubled-gold treatment as the source: both are
        // selected — the room as the tool's source, the spawner as where clicks apply.
        if (pickedPos != null) {
            float pickedOutset = selectionOutset(cam, pickedPos);
            fills.add(new Fill(pickedPos, ALL_FACES, MAIN_FILL_COLOR, ALPHA, VOLUME_FILL_ALPHA,
                SELECTION_FILL_PRIORITY, 0));
            boxes.add(new Box(pickedPos, pickedPos, MAIN_COLOR, ALPHA, 0.0f));
            boxes.add(new Box(pickedPos, pickedPos, MAIN_COLOR, ALPHA, pickedOutset));
        }
        resolveFills(fills);
    }

    /**
     * Scratch for {@link #resolveFills}, reused between frames for the same reason {@link #savedFogStart}
     * is a static: this is render-thread only and runs once per frame, so a busy room stops rehashing its
     * claim set after the first one. Both are emptied again on the way out so a frame's positions and
     * fills are not held alive until the next.
     */
    private static final Set<BlockPos> fillClaims = new HashSet<>();
    private static final List<Fill> fillKeep = new ArrayList<>();

    /**
     * Hands every block cell to exactly one kind, and caps the pass. Walks the frame's candidate fills
     * once per priority rank — the selection, then doors, then the active mode's own kind, then the rest
     * outward-in — and keeps the first fill to claim each cell; a kind that loses a cell keeps its
     * outline and loses only its tint. See the class javadoc for why that order. Rewrites the list in
     * place, since {@link Overlay} hands out the same list the {@link Sink} filled.
     *
     * <p>A door group is committed whole or not at all, the one exception to "first come". Each cell's
     * {@linkplain #exteriorCells face mask} already drops the faces it shares with an in-group neighbor,
     * so the group is a closed surface only while all of it is there: keep one cell, drop the one beside
     * it, and the kept cell has a gap on that side which back-face culling shows as a hole straight
     * through the doorway — the far face behind the gap points away from the ray and is culled, so there
     * is nothing behind the gap to see. A group that cannot take every cell it needs takes none of them
     * and keeps its outline, which is exactly what an oversized group already does.
     *
     * <p>A loop per rank rather than a sort: there are eight of them, the list is a few hundred entries
     * on a busy room, and this way the {@link #FILL_CELL_BUDGET} cut lands on the kinds that rank last
     * instead of on whatever the block entity happened to emit last.
     */
    private static void resolveFills(List<Fill> candidates) {
        Set<BlockPos> claimed = fillClaims;
        List<Fill> kept = fillKeep;
        claimed.clear();
        kept.clear();
        for (int rank = SELECTION_FILL_PRIORITY;
             rank <= LOWEST_FILL_PRIORITY && kept.size() < FILL_CELL_BUDGET; rank++) {
            for (int i = 0; i < candidates.size(); i++) {
                Fill fill = candidates.get(i);
                if (fill.priority() != rank) {
                    continue;
                }
                if (fill.group() == 0) {
                    if (claimed.add(fill.pos())) {
                        kept.add(fill);
                        if (kept.size() == FILL_CELL_BUDGET) {
                            break;
                        }
                    }
                    continue;
                }
                // A door group's cells share an id and are contiguous (see Sink#addDoor), so the run ends
                // at the first entry that is not one of them, and the whole group is decided at once.
                int end = i + 1;
                while (end < candidates.size() && candidates.get(end).group() == fill.group()) {
                    end++;
                }
                claimGroup(candidates, i, end, kept, claimed);
                i = end - 1;
                if (kept.size() == FILL_CELL_BUDGET) {
                    break;
                }
            }
        }
        candidates.clear();
        candidates.addAll(kept);
        claimed.clear();
        kept.clear();
    }

    /**
     * Takes every cell of one door group or none of them; see {@link #resolveFills} for why half a group
     * is worse than none of it. Both tests have to pass before anything is committed, so a group that
     * fails the second one has not already spent part of the budget on the first.
     */
    private static void claimGroup(List<Fill> candidates, int from, int to,
                                   List<Fill> kept, Set<BlockPos> claimed) {
        if (kept.size() + (to - from) > FILL_CELL_BUDGET) {
            return;
        }
        for (int i = from; i < to; i++) {
            if (claimed.contains(candidates.get(i).pos())) {
                return;
            }
        }
        for (int i = from; i < to; i++) {
            Fill cell = candidates.get(i);
            claimed.add(cell.pos());
            kept.add(cell);
        }
    }

    private static boolean noneActive(List<Box> boxes) {
        for (Box box : boxes) {
            if (box.alpha() == ALPHA) {
                return false;
            }
        }
        return true;
    }

    /** Keeps the doubled selection outline a roughly constant number of pixels apart at any range. */
    private static float selectionOutset(Vec3 cam, BlockPos pos) {
        double dist = Math.sqrt(cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        return (float) Mth.clamp(dist * SELECTION_INFLATE_PER_BLOCK, SELECTION_INFLATE, SELECTION_INFLATE_MAX);
    }

    /** Per-frame collection target: turns a {@link Kind} into a colored, mode-dimmed box and its tint. */
    private record Sink(Minecraft mc, DungeonToolItem.Mode mode, List<Box> boxes, List<Fill> fills) {
        /** A block the selection owns: its outline at the kind's inset, and the whole block tinted under it. */
        void add(Kind kind, BlockPos pos) {
            float alpha = isActive(kind, mode) ? ALPHA : DIM_ALPHA;
            boxes.add(new Box(pos, pos, kind.color, alpha, kind.inset));
            fills.add(new Fill(pos, ALL_FACES, kind.fillColor, alpha, kind.fillAlpha,
                kind.fillPriority(mode), 0));
        }

        /**
         * Outline with no tint of its own, for the two boxes that are not a cell: a door's bounding box,
         * whose tint comes from the group's cells at their exact shape, and the hatching over a broken
         * one, which is a marker on a box that is already filled.
         */
        void addOutline(Kind kind, BlockPos min, BlockPos max, float inflate) {
            boxes.add(new Box(min, max, kind.color, isActive(kind, mode) ? ALPHA : DIM_ALPHA, inflate));
        }

        /** Offsets are stored relative to the block that owns them. */
        void addOffsets(Kind kind, BlockPos origin, List<BlockPos> offsets) {
            for (BlockPos offset : offsets) {
                add(kind, origin.offset(offset));
            }
        }

        /**
         * A door: its violet outline, the tint that fills the doorway under it cell by cell, and — when
         * the stored anchor is no longer a phase block — the hatched inset box. The broken marker stays
         * line-only and stays inside the fill, so it still reads as hatching over a tinted doorway rather
         * than becoming a second stacked tint; drawing it in a tenth color instead would let a rebuilt
         * doorway report the room as correctly wired when it is not.
         *
         * <p>The tint only goes down when the exact shape survived {@link #doorGroups} — a group too big
         * for that keeps its outline and nothing else. That is the honest answer: the outline was only
         * ever claiming "somewhere in here", while a filled bounding box claims the whole volume is
         * doorway, and the groups that miss the limit are precisely the phase-block walls where it is not.
         */
        void addDoor(Bounds door) {
            float alpha = isActive(Kind.DOOR, mode) ? ALPHA : DIM_ALPHA;
            addOutline(Kind.DOOR, door.min(), door.max(), Kind.DOOR.inset);
            List<Cell> cells = door.cells();
            if (cells != null) {
                // The group id is the list position the group starts at, plus one so that zero can keep
                // meaning "not a group". Unique because a group that takes an id appends at least one
                // fill before the next one can take theirs, and contiguous for the same reason — which is
                // what lets resolveFills find a group's cells by scanning forward from the first of them.
                int group = fills.size() + 1;
                for (Cell cell : cells) {
                    fills.add(new Fill(cell.pos(), cell.faceMask(), Kind.DOOR.fillColor, alpha,
                        Kind.DOOR.fillAlpha, Kind.DOOR.fillPriority(mode), group));
                }
            }
            if (!door.intact()) {
                addOutline(Kind.DOOR, door.min(), door.max(), BROKEN_DOOR_INSET);
            }
        }
    }

    /** The active mode's own targets draw at full alpha; the rest of the attachments are dimmed. */
    private static boolean isActive(Kind kind, DungeonToolItem.Mode mode) {
        // The two room markers are single boxes answering a single question ("where does this dungeon
        // start and end"), so they are never the bulk that dimming exists to hold back.
        if (kind == Kind.START_ROOM || kind == Kind.FINAL_ROOM) {
            return true;
        }
        return switch (mode) {
            case LINK -> kind == Kind.LINK;
            case ROOM_DOOR -> kind == Kind.DOOR;
            case MOB_SPAWN_POSITION -> kind == Kind.MOB_SPAWN;
            case ENTRANCE_POSITION -> kind == Kind.ENTRANCE;
            case RESPAWN_POSITION -> kind == Kind.RESPAWN;
            case PROTECT_POSITION -> kind == Kind.PROTECT;
        };
    }

    /**
     * Every link and stored position the selected block owns. Offsets resolve against the selection,
     * which is known to be in the player's dimension; links that carry their own dimension are filtered
     * against it, since controller instances and entrances may point at another one.
     */
    private static void collectAttachments(Sink out, BlockPos sourcePos, BlockEntity be,
                                           @Nullable BlockPos pickedSpawner) {
        Minecraft mc = out.mc();
        if (be instanceof DungeonControllerBlockEntity controller) {
            for (BlockPos instance : controller.getInstances()) {
                if (mc.level.dimension().equals(controller.getInstanceDimension(instance))) {
                    out.add(Kind.LINK, instance);
                }
            }
        } else if (be instanceof RaidControllerBlockEntity controller) {
            for (RaidControllerBlockEntity.RaidInstanceState instance : controller.getInstances()) {
                if (mc.level.dimension().equals(instance.dimension())) {
                    out.add(Kind.LINK, instance.spawnerPos());
                }
            }
        } else if (be instanceof ArenaControllerBlockEntity controller) {
            for (ArenaControllerBlockEntity.ArenaInstanceState instance : controller.getInstances()) {
                if (mc.level.dimension().equals(instance.dimension())) {
                    out.add(Kind.LINK, instance.spawnerPos());
                }
            }
        } else if (be instanceof DungeonBossSpawnerBlockEntity dbs) {
            BlockPos startRoom = dbs.getAbsoluteStartRoomPos();
            BlockPos finalRoom = dbs.getAbsoluteFinalRoomPos();
            for (BlockPos room : dbs.getRooms()) {
                // A single room can be marked both start and final; final wins, it is the rarer marker.
                Kind kind = room.equals(finalRoom) ? Kind.FINAL_ROOM
                    : room.equals(startRoom) ? Kind.START_ROOM : Kind.LINK;
                out.add(kind, room);
            }
            out.addOffsets(Kind.MOB_SPAWN, sourcePos, dbs.getEntityDefinition().spawnOffsets());
            addEntrance(out, dbs.getEntranceOffset(), dbs.getEntranceDimension(), dbs.getAbsoluteEntrancePos());
        } else if (be instanceof RoomControllerBlockEntity room) {
            // Offset getters, not the absolute ones: those build a fresh list and a fresh BlockPos per
            // entry on every frame, and the Sink resolves offsets against the owner anyway.
            for (BlockPos spawnerOffset : room.getSpawnerOffsets()) {
                BlockPos spawnerPos = sourcePos.offset(spawnerOffset);
                out.add(Kind.LINK, spawnerPos);
                // With an acting spawner picked, its spawn positions keep the full-strength
                // yellow the mode edits; every other spawner's turn red (and, never being the
                // mode's own kind, sit in the dim tier), so the two are unmistakable.
                if (pickedSpawner == null || spawnerPos.equals(pickedSpawner)) {
                    out.addOffsets(Kind.MOB_SPAWN, spawnerPos, linkedSpawnOffsets(mc, spawnerPos));
                } else {
                    out.addOffsets(Kind.OTHER_MOB_SPAWN, spawnerPos, linkedSpawnOffsets(mc, spawnerPos));
                }
            }
            for (Bounds door : doorGroups(mc, sourcePos, room.getDoorOffsets())) {
                out.addDoor(door);
            }
            out.addOffsets(Kind.RESPAWN, sourcePos, room.getRespawnPointOffsets());
            BlockPos protectPos = room.getProtectPos();
            if (protectPos != null) {
                out.add(Kind.PROTECT, protectPos);
            }
        } else if (be instanceof MobSpawnerBlockEntity spawner) {
            out.addOffsets(Kind.MOB_SPAWN, sourcePos, spawner.getEntityDefinition().spawnOffsets());
        } else if (be instanceof RaidBossSpawnerBlockEntity spawner) {
            out.addOffsets(Kind.MOB_SPAWN, sourcePos, spawner.getEntityDefinition().spawnOffsets());
            out.addOffsets(Kind.RESPAWN, sourcePos, spawner.getRespawnPointOffsets());
            addEntrance(out, spawner.getEntranceOffset(), spawner.getEntranceDimension(),
                spawner.getAbsoluteEntrancePos());
        } else if (be instanceof ArenaSpawnerBlockEntity spawner) {
            // Arena mobs are placed procedurally around the spawner, so there are no stored spawn positions.
            out.addOffsets(Kind.RESPAWN, sourcePos, spawner.getRespawnPointOffsets());
            addEntrance(out, spawner.getEntranceOffset(), spawner.getEntranceDimension(),
                spawner.getAbsoluteEntrancePos());
        }
    }

    /**
     * Spawn positions of a spawner a room links to. A room stores mob and boss spawners in one list and
     * tells them apart by block entity type, exactly as the room does when it spawns a wave; an empty
     * list also covers the spawner whose chunk the client has not loaded.
     */
    private static List<BlockPos> linkedSpawnOffsets(Minecraft mc, BlockPos spawnerPos) {
        if (!mc.level.isLoaded(spawnerPos)) {
            return List.of();
        }
        BlockEntity linked = mc.level.getBlockEntity(spawnerPos);
        if (linked instanceof MobSpawnerBlockEntity mobSpawner) {
            return mobSpawner.getEntityDefinition().spawnOffsets();
        }
        if (linked instanceof DungeonBossSpawnerBlockEntity bossSpawner) {
            return bossSpawner.getEntityDefinition().spawnOffsets();
        }
        return List.of();
    }

    /**
     * An entrance has no "unset" flag: the offset defaults to {@link BlockPos#ZERO}, so every
     * never-configured spawner in the Overworld would otherwise paint an orange box on itself and read
     * as configured. Skipping ZERO costs one true case — the tool stores {@code clickedPos.above()}, so
     * clicking the block directly <em>under</em> the spawner also stores ZERO, and that entrance is real:
     * the run teleports players onto the spawner's own block, it just draws nothing here. Making it
     * visible needs an {@code Optional<BlockPos>} offset on the three spawners, a block entity change.
     */
    private static void addEntrance(Sink out, BlockPos offset, @Nullable ResourceKey<Level> dimension,
                                    BlockPos absolutePos) {
        if (offset.equals(BlockPos.ZERO) || !out.mc().level.dimension().equals(dimension)) {
            return;
        }
        out.add(Kind.ENTRANCE, absolutePos);
    }

    // ---- Door group cache ----

    /**
     * Doors now draw in every mode, so the flood fill behind them ({@link RoomGraph#expandDoorGroup},
     * up to 1024 block entity lookups per door) can no longer run per frame. The cached groups are keyed
     * on the level, the selection and its stored anchors, so linking or unlinking a door refreshes them
     * on the next frame; the tick interval covers phase blocks added to or removed from an existing door,
     * which changes the group's shape without touching the room. The level is held weakly and compared by
     * identity, which covers both a dimension change and leaving the world entirely — the client builds a
     * fresh {@code ClientLevel} for each — without retaining it.
     *
     * <p>The cache now also carries each group's exterior faces, per cell, so the fill shape is derived
     * once per interval rather than per frame. The visible cost is that a phase block added to or removed
     * from a doorway takes up to half a second to change the tint — already true of the outline, but a
     * solid face makes it more obvious.
     */
    private static final int DOOR_CACHE_INTERVAL_TICKS = 10;
    /**
     * Above this many blocks a door group is not filled at all, only outlined. A doorway is 2 to 12
     * blocks and even a portcullis-sized gate is well under a hundred, so 256 covers every shape a
     * builder would call a door several times over, at a worst case of {@code 6 * 256 = 1536} quads —
     * still inside {@link #DOOR_FILL_FACE_BUDGET}. What it excludes is the reason
     * {@link RoomGraph#expandDoorGroup} has a 1024-block cap at all: a phase-block <em>wall</em> is a
     * legal group too, and a wall is not a doorway to paint over.
     */
    private static final int DOOR_FILL_CELL_LIMIT = 256;
    /**
     * Ceiling on door fills across all of a selection's doors in one refresh, since the number of doors
     * on a room is itself unbounded. Applied here rather than at draw time so the choice is cached with
     * the group and cannot flicker between frames, and expressed in faces rather than cells because faces
     * are what the extraction actually produces. It is no longer the bound on the pass —
     * {@link #FILL_CELL_BUDGET} is, and a door's cells compete for it like any other kind's, except that
     * they compete as one group — but it still keeps a room full of doorways from caching a face list
     * nobody will draw.
     */
    private static final int DOOR_FILL_FACE_BUDGET = 2048;
    @Nullable
    private static WeakReference<Level> doorCacheLevel;
    @Nullable
    private static BlockPos doorCacheSource;
    private static List<BlockPos> doorCacheAnchors = List.of();
    private static List<Bounds> doorCacheGroups = List.of();
    private static long doorCacheTick = Long.MIN_VALUE;

    /**
     * One box per door, hugging the whole flood-filled phase-block group so builders see the door rather
     * than the block that happened to be clicked. Anchors landing in an already-expanded group are
     * skipped, so a door registered from both sides draws once. Takes the room's stored offsets, which
     * are the cheaper and more stable cache key.
     */
    private static List<Bounds> doorGroups(Minecraft mc, BlockPos sourcePos, List<BlockPos> anchorOffsets) {
        long now = mc.level.getGameTime();
        if (sourcePos.equals(doorCacheSource)
            && doorCacheLevel != null && doorCacheLevel.get() == mc.level
            && anchorOffsets.equals(doorCacheAnchors)
            && now >= doorCacheTick
            && now - doorCacheTick < DOOR_CACHE_INTERVAL_TICKS) {
            return doorCacheGroups;
        }

        List<Bounds> groups = new ArrayList<>(anchorOffsets.size());
        Set<BlockPos> seen = new HashSet<>();
        int faceBudget = DOOR_FILL_FACE_BUDGET;
        for (BlockPos anchorOffset : anchorOffsets) {
            BlockPos anchor = sourcePos.offset(anchorOffset);
            if (!seen.add(anchor)) {
                continue;
            }
            Set<BlockPos> group = RoomGraph.expandDoorGroup(mc.level, anchor);
            seen.addAll(group);
            // An unloaded anchor expands to the lone-anchor group too, so only call the link broken when
            // the client can actually see that the block is not a phase block.
            boolean intact = !mc.level.isLoaded(anchor)
                || mc.level.getBlockEntity(anchor) instanceof PhaseBlockEntity;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : group) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            // The budget is tested before the work, not after: past it every remaining group would have
            // its faces extracted and then thrown away, which is the one part of this that scales with
            // the number of doors on the room.
            List<Cell> cells = null;
            if (group.size() <= DOOR_FILL_CELL_LIMIT && faceBudget > 0) {
                List<Cell> exterior = exteriorCells(group);
                int faces = faceCount(exterior);
                if (faces <= faceBudget) {
                    cells = exterior;
                    faceBudget -= faces;
                }
            }
            groups.add(new Bounds(
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), intact, cells));
        }

        doorCacheLevel = new WeakReference<>(mc.level);
        doorCacheSource = sourcePos;
        doorCacheAnchors = List.copyOf(anchorOffsets);
        doorCacheGroups = groups;
        doorCacheTick = now;
        return groups;
    }

    /**
     * The group's outward-facing block faces, gathered per cell: every face whose neighbor is not itself
     * in the group, so the shared faces inside a multi-block door are dropped and the tint never doubles
     * up on itself. A cell with no outward face at all — the inside of a thick group — is left out
     * entirely rather than stored with an empty mask, so it neither draws nor claims itself in
     * {@link #resolveFills}. Runs once per cache refresh, never per frame.
     */
    private static List<Cell> exteriorCells(Set<BlockPos> group) {
        List<Cell> cells = new ArrayList<>(group.size());
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (BlockPos pos : group) {
            int mask = 0;
            for (Direction dir : DIRECTIONS) {
                // BlockPos equality and hashing are by value, so a mutable probe is a valid set key and
                // saves an allocation per neighbor test.
                probe.setWithOffset(pos, dir);
                if (!group.contains(probe)) {
                    mask |= 1 << dir.ordinal();
                }
            }
            if (mask != 0) {
                cells.add(new Cell(pos, mask));
            }
        }
        return cells;
    }

    private static int faceCount(List<Cell> cells) {
        int faces = 0;
        for (Cell cell : cells) {
            faces += Integer.bitCount(cell.faceMask());
        }
        return faces;
    }

    private static boolean dimensionMatches(Minecraft mc, Optional<ResourceKey<Level>> dimension) {
        return dimension.isEmpty() || dimension.get().equals(mc.level.dimension());
    }
}
