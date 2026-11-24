package org.encinet.mik.feature.server.prison;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * The prison add-on's settings. Sentences are not configured here: a ban is the sentence, so
 * duration comes from {@code BanManager} and release follows unbanning.
 */
record PrisonSettings(boolean allowBannedPrisonerLogin, boolean protectRegion,
                      long checkIntervalTicks, Set<String> allowedCommands) {
    static PrisonSettings load(Path file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (Files.exists(file)) yaml.load(file.toFile());
            else {
                yaml.set("allow-banned-prisoner-login", true);
                yaml.set("protect-regions", true);
                yaml.set("check-interval-ticks", 40);
                yaml.set("allowed-commands",
                        Set.of("msg", "tell", "r", "reply", "help", "rules", "ban", "unban", "banlist"));
                Files.createDirectories(file.getParent());
                Files.writeString(file, yaml.saveToString());
            }
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Prison settings YAML is invalid: " + file, exception);
        }
        long interval = yaml.getLong("check-interval-ticks");
        if (interval < 1 || interval > 72_000)
            throw new IOException("check-interval-ticks must be between 1 and 72000");
        Set<String> commands = yaml.getStringList("allowed-commands").stream()
                .map(command -> command.toLowerCase(Locale.ROOT).replaceFirst("^/", ""))
                .filter(command -> !command.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new PrisonSettings(yaml.getBoolean("allow-banned-prisoner-login", true),
                yaml.getBoolean("protect-regions", true), interval, commands);
    }
}
