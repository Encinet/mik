package org.encinet.mik.feature.player.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

final class TeleportCommandSafety {
    private TeleportCommandSafety() {}

    static boolean allow(String arguments, CommandSender sender, LanguageService languageService) {
        if (!containsBareEntitySelector(arguments)) return true;

        String message = sender instanceof Player player
                ? languageService.t(player, Message.TELEPORT_BARE_ENTITY_SELECTOR)
                : languageService.t(Language.DEFAULT, Message.TELEPORT_BARE_ENTITY_SELECTOR);
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
        return false;
    }

    private static boolean containsBareEntitySelector(String arguments) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < arguments.length(); i++) {
            char current = arguments.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == quote) quote = 0;
                continue;
            }
            if (depth == 0 && current == '@' && i + 1 < arguments.length()
                    && arguments.charAt(i + 1) == 'e'
                    && (i == 0 || Character.isWhitespace(arguments.charAt(i - 1)))
                    && (i + 2 == arguments.length() || Character.isWhitespace(arguments.charAt(i + 2)))) {
                return true;
            }
            // Quoted text and whitespace inside selector filters are not new arguments.
            if (current == '"' || current == '\'') quote = current;
            else if (current == '[' || current == '{') depth++;
            else if (current == ']' || current == '}') depth--;
        }
        return false;
    }
}
