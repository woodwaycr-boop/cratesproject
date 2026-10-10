package dev.cratecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CrateCore plugin;

    Messages(CrateCore plugin) {
        this.plugin = plugin;
    }

    /**
     * No fallback value is passed to getString on purpose. That makes Bukkit fall back to the
     * config.yml bundled in the jar, so messages added in an update work with an old config file.
     */
    private String raw(String key) {
        String value = plugin.getConfig().getString("messages." + key);
        return value == null ? "<red>Missing message: " + key : value;
    }

    /** Message with the chat prefix. */
    Component get(String key, TagResolver... resolvers) {
        String prefix = plugin.getConfig().getString("messages.prefix");
        return MM.deserialize((prefix == null ? "" : prefix) + raw(key), resolvers);
    }

    /** Message without the prefix, for titles. */
    Component plain(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), resolvers);
    }

    void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(get(key, resolvers));
    }
}
