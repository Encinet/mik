package org.encinet.mik.feature.server.prison;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

record PrisonSettings(boolean autoJailBans, boolean allowBannedPrisonerLogin, boolean releaseOnUnban,
                      boolean releaseWhenSentenceEnds, boolean protectRegion,
                      long checkIntervalTicks, long defaultBanSentenceMillis,
                      Set<String> allowedCommands) {
    static PrisonSettings load(Path file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (Files.exists(file)) yaml.load(file.toFile());
            else {
                yaml.set("automatic-jailing", true);
                yaml.set("allow-banned-prisoner-login", true);
                yaml.set("release-on-unban", true);
                yaml.set("release-when-sentence-ends", true);
                yaml.set("protect-regions", true);
                yaml.set("check-interval-ticks", 40);
                yaml.set("default-ban-sentence", "permanent");
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
        long defaultSentence;
        try {
            defaultSentence = PrisonDuration.parse(yaml.getString("default-ban-sentence", "permanent"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid default-ban-sentence", exception);
        }
        Set<String> commands = yaml.getStringList("allowed-commands").stream()
                .map(command -> command.toLowerCase(Locale.ROOT).replaceFirst("^/", ""))
                .filter(command -> !command.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new PrisonSettings(yaml.getBoolean("automatic-jailing", true),
                yaml.getBoolean("allow-banned-prisoner-login", true), yaml.getBoolean("release-on-unban", true),
                yaml.getBoolean("release-when-sentence-ends", true),
                yaml.getBoolean("protect-regions", true), interval, defaultSentence, commands);
    }
}
