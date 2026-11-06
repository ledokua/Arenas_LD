package net.ledok.arenas_ld.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/** How the admin screens show {@link AdminDrafts}: tab markers, the conflict warning, the "saved by" notice. */
public final class AdminEditState {
    private AdminEditState() {
    }

    /** A tab's label: "•" for unsaved edits, a yellow "⚠" for a conflict. */
    public static Component tabLabel(String key, AdminDrafts drafts, String prefix) {
        MutableComponent label = Component.translatable(key);
        if (!drafts.conflicts(prefix).isEmpty()) {
            label.append(Component.literal(" ⚠").withStyle(ChatFormatting.YELLOW));
        } else if (drafts.dirtyAny(prefix)) {
            label.append(" •");
        }
        return label;
    }

    /** One character per prefix ("!" conflict, "*" unsaved, "-" clean), to see when the tab labels change. */
    public static String markers(AdminDrafts drafts, String... prefixes) {
        StringBuilder out = new StringBuilder();
        for (String prefix : prefixes) {
            out.append(!drafts.conflicts(prefix).isEmpty() ? '!' : drafts.dirtyAny(prefix) ? '*' : '-');
        }
        return out.toString();
    }

    /** "Bob changed Cooldown (now 30), … while you were editing. Saving keeps yours." */
    public static Component conflictText(String editor, Map<String, String> conflicts,
                                         Function<String, Component> caption) {
        MutableComponent fields = Component.empty();
        boolean first = true;
        for (Map.Entry<String, String> entry : conflicts.entrySet()) {
            if (!first) {
                fields.append(", ");
            }
            first = false;
            fields.append(Component.translatable("gui.arenas_ld.admin.conflict_field",
                caption.apply(entry.getKey()), shortValue(entry.getValue())));
        }
        return Component.translatable("gui.arenas_ld.admin.conflict", editorName(editor), fields);
    }

    /**
     * "Bob saved General, Easy." for the tabs another admin's save just changed here, or null when
     * none changed, or the change was this admin's own save coming back.
     */
    public static @Nullable String notice(String editor, Collection<String> changed, Map<String, Component> tabs) {
        if (changed.isEmpty() || isSelf(editor)) {
            return null;
        }
        MutableComponent names = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Component> tab : tabs.entrySet()) {
            if (AdminDrafts.any(changed, tab.getKey())) {
                if (!first) {
                    names.append(", ");
                }
                first = false;
                names.append(tab.getValue());
            }
        }
        return first ? null : Component.translatable("gui.arenas_ld.admin.updated", editorName(editor), names).getString();
    }

    private static Component editorName(String editor) {
        return editor == null || editor.isEmpty()
            ? Component.translatable("gui.arenas_ld.admin.another_admin")
            : Component.literal(editor);
    }

    private static boolean isSelf(String editor) {
        return editor != null && editor.equals(Minecraft.getInstance().getUser().getName());
    }

    /** A value short enough for the footer (reward commands are joined lines). */
    private static String shortValue(String value) {
        String flat = value.replace('\n', ';');
        return flat.length() > 24 ? flat.substring(0, 23) + "…" : flat;
    }
}
