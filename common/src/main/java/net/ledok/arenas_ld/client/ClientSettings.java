package net.ledok.arenas_ld.client;

import net.ledok.arenas_ld.api.client.ArenasClientApi.Choice;
import net.ledok.arenas_ld.api.client.ArenasClientApi.Setting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The client settings behind {@link net.ledok.arenas_ld.api.client.ArenasClientApi#settings()}. */
public final class ClientSettings {
    private ClientSettings() {
    }

    public static List<Setting> all() {
        ArenasClientConfig c = ArenasClientConfig.get();
        return List.of(
            setting("run_hud_style", () -> c.run_hud_style, v -> c.run_hud_style = v, "new", "classic"),
            setting("loot_reveal_style", () -> c.loot_reveal_style, v -> c.loot_reveal_style = v, "new", "classic"),
            setting("ally_sidebar", () -> c.ally_sidebar, v -> c.ally_sidebar = v, "left", "right", "hidden"),
            setting("lobby_badge", () -> c.lobby_badge, v -> c.lobby_badge = v, "top_left", "top_right", "hidden"));
    }

    private static Setting setting(String id, Supplier<String> get, Consumer<String> put, String... values) {
        List<Choice> choices = new ArrayList<>();
        for (String value : values) {
            choices.add(new Choice(value, Component.translatable("gui.arenas_ld.client_setting." + id + "." + value)));
        }
        return new Setting(id, Component.translatable("gui.arenas_ld.client_setting." + id),
            Component.translatable("gui.arenas_ld.client_setting." + id + ".description"), List.copyOf(choices), get,
            value -> {
                if (!List.of(values).contains(value) || value.equals(get.get())) {
                    return;
                }
                put.accept(value);
                ArenasClientConfig.get().save();
            });
    }
}
