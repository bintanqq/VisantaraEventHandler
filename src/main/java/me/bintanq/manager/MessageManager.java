package me.bintanq.manager;

import me.bintanq.VisantaraEventHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MessageManager {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    private final VisantaraEventHandler plugin;
    private FileConfiguration messages;

    public MessageManager(VisantaraEventHandler plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "message.yml");

        if (!file.exists()) {
            plugin.saveResource("message.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(file);

        InputStream defaultStream = plugin.getResource("message.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        }
    }

    public String getRaw(String path) {
        return messages.getString(path, "&cMissing message: " + path);
    }

    public String getRaw(String path, Map<String, String> placeholders) {
        String raw = getRaw(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw;
    }

    public Component get(String path) {
        return withPrefix(getRaw(path));
    }

    public Component get(String path, Map<String, String> placeholders) {
        return withPrefix(getRaw(path, placeholders));
    }

    public Component getNoPrefix(String path) {
        return SERIALIZER.deserialize(getRaw(path));
    }

    public Component getNoPrefix(String path, Map<String, String> placeholders) {
        return SERIALIZER.deserialize(getRaw(path, placeholders));
    }

    private Component withPrefix(String message) {
        String prefix = messages.getString("prefix", "&8[&6VEH&8] ");
        return SERIALIZER.deserialize(prefix + message);
    }

    public static Map<String, String> of(String... kvPairs) {
        if (kvPairs.length % 2 != 0) throw new IllegalArgumentException("Pairs must be even");
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            map.put(kvPairs[i], kvPairs[i + 1]);
        }
        return map;
    }
}