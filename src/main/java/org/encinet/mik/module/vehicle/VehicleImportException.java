package org.encinet.mik.module.vehicle;

import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

final class VehicleImportException extends IllegalArgumentException {
    private final Message message;

    VehicleImportException(Message message) {
        super(message.name());
        this.message = message;
    }

    String describe(LanguageService language, Player player) { return language.t(player, message); }
}
