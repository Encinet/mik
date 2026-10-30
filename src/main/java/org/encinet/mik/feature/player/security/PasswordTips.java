package org.encinet.mik.feature.player.security;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.shared.i18n.Message;

/** Password presence is read again whenever the shared daily pool considers this advice. */
public final class PasswordTips {
    private PasswordTips() { }

    public static void register(TipScope tips, PasswordAccounts accounts) {
        tips.define("password-set", Message.PASSWORD_TIP_SET_MM)
                .ambient()
                .when(player -> !accounts.hasPassword(player.getUniqueId()))
                .suggestCommand("/password")
                .build();
    }
}
