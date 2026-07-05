package net.ledok.arenas_ld.dungeon.screen;

import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.RoomClearDoorPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearEntrancesPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearRespawnPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearSpawnersPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomRemoveSpawnerPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomResetPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomSetNamePayload;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class RoomControllerScreen extends BaseOwoHandledScreen<FlowLayout, RoomControllerScreenHandler> {

    private static final int BG          = 0xFF070E14;
    private static final int PANEL       = 0xFF121922;
    private static final int PANEL_2     = 0xFF0C1218;
    private static final int HAIRLINE    = 0xFF283442;
    private static final int HAIRLINE_HI = 0xFF3A4A5C;
    private static final int ROW_BG      = 0xFF19222D;
    private static final int ROW_BG_ALT  = 0xFF16202A;
    private static final int INK         = 0xFFE8EEF5;
    private static final int INK_MID     = 0xFF9AA8B8;
    private static final int INK_DIM     = 0xFF5F6E80;
    private static final int WARN        = 0xFFF5B042;
    private static final int ACCENT      = 0xFFA98BE8;
    private static final int ACCENT_DARK = 0xFF6C4FB5;
    private static final int DANGER      = 0xFFE8624A;

    private final List<RoomControllerData.SpawnerEntry> spawners = new ArrayList<>();
    private List<BlockPos> doorPositions = List.of();
    private List<BlockPos> entrancePositions = List.of();
    private Optional<BlockPos> respawnPos = Optional.empty();
    private String roomNameInput = "";

    private FlowLayout contentArea;
    private FlowLayout footerActions;
    private LabelComponent footerLabel;

    public RoomControllerScreen(RoomControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.inventoryLabelY = 9999;
        this.titleLabelY = 9999;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        syncState();

        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth  = Math.max(440, Math.min(560, this.width - 24));
        int shellHeight = Math.max(300, this.height - 24);
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellWidth), Sizing.fixed(shellHeight));
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));

        shell.child(buildHeader());

        contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        contentArea.surface(Surface.flat(PANEL));
        contentArea.padding(Insets.of(10));
        contentArea.gap(4);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), contentArea);
        scroll.surface(Surface.flat(PANEL));
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        scroll.scrollbarThiccness(8);
        scroll.fixedScrollbarLength(28);
        scroll.scrollStep(18);
        shell.child(scroll);

        shell.child(buildFooter());
        rootComponent.child(shell);

        rebuildUi();
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(42));
        header.surface(Surface.flat(PANEL_2));
        header.padding(Insets.of(6));
        header.gap(8);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(18), Sizing.fixed(18));
        mark.surface(Surface.flat(WARN));
        header.child(mark);

        FlowLayout info = Containers.verticalFlow(Sizing.content(), Sizing.content());
        info.gap(3);
        LabelComponent titleLabel = Components.label(Component.translatable("gui.arenas_ld.room_controller.title"));
        titleLabel.color(Color.ofArgb(INK));
        info.child(titleLabel);
        LabelComponent posLabel = Components.label(Component.literal(
            "POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()));
        posLabel.color(Color.ofArgb(INK_DIM));
        info.child(posLabel);
        header.child(info);

        header.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

        ButtonComponent close = smallButton(Component.literal("×"), b -> onClose());
        close.sizing(Sizing.fixed(22), Sizing.fixed(18));
        header.child(close);
        return header;
    }

    private FlowLayout buildFooter() {
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(34));
        footer.surface(Surface.flat(PANEL_2));
        footer.padding(Insets.of(6));
        footer.gap(6);

        footerLabel = Components.label(Component.empty());
        footerLabel.color(Color.ofArgb(DANGER));
        footerLabel.horizontalSizing(Sizing.expand());
        footer.child(footerLabel);

        footerActions = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        footerActions.gap(4);
        footer.child(footerActions);
        return footer;
    }

    private void rebuildUi() {
        if (contentArea == null) return;
        contentArea.clearChildren();
        footerActions.clearChildren();

        // ── Room ────────────────────────────────────────────────────────────
        contentArea.child(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.room")));
        contentArea.child(spacer(2));
        contentArea.child(buildNameRow());
        contentArea.child(spacer(8));

        // ── Spawners ────────────────────────────────────────────────────────
        contentArea.child(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.spawners")
            .copy().append(Component.literal(" · " + spawners.size()))));
        contentArea.child(spacer(2));
        if (spawners.isEmpty()) {
            LabelComponent empty = Components.label(Component.translatable("gui.arenas_ld.room_controller.spawners_empty"));
            empty.color(Color.ofArgb(INK_DIM));
            contentArea.child(empty);
        } else {
            for (int i = 0; i < spawners.size(); i++) {
                if (i > 0) contentArea.child(rowDivider());
                contentArea.child(spawnerRow(i));
            }
        }
        contentArea.child(spacer(8));

        // ── Door ────────────────────────────────────────────────────────────
        contentArea.child(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.door")));
        contentArea.child(spacer(2));
        contentArea.child(buildDoorRow());
        contentArea.child(spacer(8));

        // ── Entrance doors ──────────────────────────────────────────────────
        contentArea.child(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.entrances")));
        contentArea.child(spacer(2));
        contentArea.child(buildEntranceRow());
        contentArea.child(spacer(8));

        // ── Respawn point ───────────────────────────────────────────────────
        contentArea.child(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.respawn")));
        contentArea.child(spacer(2));
        contentArea.child(buildRespawnRow());
        contentArea.child(spacer(8));

        // ── Rewards (edited in a dedicated screen) ──────────────────────────
        contentArea.child(sectionLabel(Component.translatable("gui.arenas_ld.room_controller.section.rewards")));
        contentArea.child(spacer(2));
        FlowLayout rewardsRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        rewardsRow.gap(8);
        rewardsRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent rewardHint = Components.label(Component.translatable("gui.arenas_ld.room_controller.reward_hint"));
        rewardHint.color(Color.ofArgb(INK_DIM));
        rewardsRow.child(rewardHint);
        rewardsRow.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
        ButtonComponent rewardsBtn = accentButton(
            Component.translatable("gui.arenas_ld.room_controller.button.rewards"), b -> openRewardsScreen());
        rewardsBtn.sizing(Sizing.content(), Sizing.fixed(18));
        rewardsRow.child(rewardsBtn);
        contentArea.child(rewardsRow);

        // ── Footer actions ──────────────────────────────────────────────────

        if (!spawners.isEmpty()) {
            ButtonComponent clearAll = dangerButton(
                Component.translatable("gui.arenas_ld.room_controller.button.clear_all"),
                b -> {
                    if (minecraft == null) return;
                    minecraft.setScreen(new ConfirmScreen(ok -> {
                        minecraft.setScreen(this);
                        if (ok) ClientPlayNetworking.send(new RoomClearSpawnersPayload(menu.getBlockPos()));
                    }, Component.translatable("gui.arenas_ld.room_controller.confirm.clear_title"),
                       Component.translatable("gui.arenas_ld.room_controller.confirm.clear_message")));
                });
            footerActions.child(clearAll);
        }

        ButtonComponent reset = dangerButton(
            Component.translatable("gui.arenas_ld.room_controller.button.reset"),
            b -> {
                if (minecraft == null) return;
                minecraft.setScreen(new ConfirmScreen(ok -> {
                    minecraft.setScreen(this);
                    if (ok) ClientPlayNetworking.send(new RoomResetPayload(menu.getBlockPos()));
                }, Component.translatable("gui.arenas_ld.room_controller.confirm.reset_title"),
                   Component.translatable("gui.arenas_ld.room_controller.confirm.reset_message")));
            });
        footerActions.child(reset);
    }

    private FlowLayout buildNameRow() {
        FlowLayout fieldRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldRow.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldRow.child(accent);

        TextBoxComponent field = IdSuggestionDropdown.textBox(Sizing.expand(), roomNameInput, 48);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(v -> roomNameInput = v);
        IdSuggestionDropdown.attachFullValueTooltip(field);
        fieldRow.child(field);

        ButtonComponent setBtn = accentButton(Component.translatable("gui.arenas_ld.room_controller.button.set_name"),
            b -> ClientPlayNetworking.send(new RoomSetNamePayload(menu.getBlockPos(), roomNameInput == null ? "" : roomNameInput)));
        setBtn.sizing(Sizing.fixed(64), Sizing.fixed(18));
        fieldRow.child(setBtn);
        return fieldRow;
    }

    private FlowLayout spawnerRow(int index) {
        RoomControllerData.SpawnerEntry entry = spawners.get(index);
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.surface(Surface.flat(index % 2 == 0 ? ROW_BG : ROW_BG_ALT));
        row.padding(Insets.of(0, 0, 6, 6));
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent coord = Components.label(Component.literal(entry.pos().toShortString()));
        coord.color(Color.ofArgb(entry.missing() ? INK_DIM : INK));
        row.child(coord);

        if (entry.missing()) {
            LabelComponent missing = Components.label(Component.translatable("gui.arenas_ld.room_controller.spawner_missing"));
            missing.color(Color.ofArgb(DANGER));
            row.child(missing);
        } else {
            LabelComponent wave = Components.label(Component.translatable("gui.arenas_ld.room_controller.wave_badge", entry.wave()));
            wave.color(Color.ofArgb(INK_MID));
            row.child(wave);
            if (entry.isBoss()) {
                LabelComponent boss = Components.label(Component.translatable("gui.arenas_ld.room_controller.boss_badge"));
                boss.color(Color.ofArgb(WARN));
                row.child(boss);
            }
        }

        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));

        ButtonComponent remove = smallButton(Component.translatable("gui.arenas_ld.room_controller.button.remove"),
            b -> ClientPlayNetworking.send(new RoomRemoveSpawnerPayload(menu.getBlockPos(), entry.pos())));
        remove.sizing(Sizing.content(), Sizing.fixed(16));
        row.child(remove);
        return row;
    }

    private FlowLayout buildDoorRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        Component doorValue = doorPositions.isEmpty()
            ? Component.translatable("gui.arenas_ld.room_controller.door_none")
            : (doorPositions.size() == 1
                ? Component.literal(doorPositions.get(0).toShortString())
                : Component.translatable("gui.arenas_ld.room_controller.door_count", doorPositions.size()));
        LabelComponent value = Components.label(doorValue);
        value.color(Color.ofArgb(doorPositions.isEmpty() ? INK_DIM : INK));
        row.child(value);

        LabelComponent hint = Components.label(Component.translatable("gui.arenas_ld.room_controller.door_hint"));
        hint.color(Color.ofArgb(INK_DIM));
        row.child(hint);

        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));

        ButtonComponent clearDoor = smallButton(Component.translatable("gui.arenas_ld.room_controller.button.clear_door"),
            b -> ClientPlayNetworking.send(new RoomClearDoorPayload(menu.getBlockPos())));
        clearDoor.active(!doorPositions.isEmpty());
        row.child(clearDoor);
        return row;
    }

    private FlowLayout buildEntranceRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        Component entranceValue = entrancePositions.isEmpty()
            ? Component.translatable("gui.arenas_ld.room_controller.entrance_none")
            : (entrancePositions.size() == 1
                ? Component.literal(entrancePositions.get(0).toShortString())
                : Component.translatable("gui.arenas_ld.room_controller.entrance_count", entrancePositions.size()));
        LabelComponent value = Components.label(entranceValue);
        value.color(Color.ofArgb(entrancePositions.isEmpty() ? INK_DIM : INK));
        row.child(value);

        LabelComponent hint = Components.label(Component.translatable("gui.arenas_ld.room_controller.entrance_hint"));
        hint.color(Color.ofArgb(INK_DIM));
        row.child(hint);

        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));

        ButtonComponent clear = smallButton(Component.translatable("gui.arenas_ld.room_controller.button.clear_entrances"),
            b -> ClientPlayNetworking.send(new RoomClearEntrancesPayload(menu.getBlockPos())));
        clear.active(!entrancePositions.isEmpty());
        row.child(clear);
        return row;
    }

    private FlowLayout buildRespawnRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent value = Components.label(respawnPos
            .map(pos -> (Component) Component.literal(pos.toShortString()))
            .orElse(Component.translatable("gui.arenas_ld.room_controller.respawn_none")));
        value.color(Color.ofArgb(respawnPos.isPresent() ? INK : INK_DIM));
        row.child(value);

        LabelComponent hint = Components.label(Component.translatable("gui.arenas_ld.room_controller.respawn_hint"));
        hint.color(Color.ofArgb(INK_DIM));
        row.child(hint);

        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));

        ButtonComponent clear = smallButton(Component.translatable("gui.arenas_ld.room_controller.button.clear_respawn"),
            b -> ClientPlayNetworking.send(new RoomClearRespawnPayload(menu.getBlockPos())));
        clear.active(respawnPos.isPresent());
        row.child(clear);
        return row;
    }

    private void openRewardsScreen() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.setScreen(new RoomRewardsScreen(menu, minecraft.player.getInventory(),
            Component.translatable("gui.arenas_ld.room_controller.rewards_title")));
    }

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(RoomControllerData data) {
        menu.applyData(data);
        syncState();
        rebuildUi();
    }

    private void syncState() {
        spawners.clear();
        spawners.addAll(menu.getSpawners());
        doorPositions = menu.getDoorPositions();
        entrancePositions = menu.getEntrancePositions();
        respawnPos = menu.getRespawnPos();
        roomNameInput = menu.getRoomName();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private FlowLayout sectionLabel(Component text) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(INK_DIM));
        row.child(label);
        return row;
    }

    private FlowLayout spacer(int px) {
        FlowLayout s = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(px));
        s.surface(Surface.BLANK);
        return s;
    }

    private FlowLayout rowDivider() {
        FlowLayout d = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        d.surface(Surface.flat(HAIRLINE));
        return d;
    }

    private ButtonComponent smallButton(Component text, Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text, action);
        button.sizing(Sizing.content(), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.active() ? (rendered.isHoveredOrFocused() ? ACCENT : ACCENT_DARK) : PANEL;
            int border = rendered.active() ? ACCENT : HAIRLINE;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
        });
        return button;
    }

    private ButtonComponent accentButton(Component text, Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text, action);
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ACCENT : ACCENT_DARK;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), ACCENT);
        });
        return button;
    }

    private ButtonComponent dangerButton(Component text, Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text.copy().withStyle(net.minecraft.ChatFormatting.RED), action);
        button.sizing(Sizing.content(), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ((DANGER & 0x00FFFFFF) | 0x44000000) : ((DANGER & 0x00FFFFFF) | 0x1F000000);
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), DANGER);
        });
        return button;
    }

}
