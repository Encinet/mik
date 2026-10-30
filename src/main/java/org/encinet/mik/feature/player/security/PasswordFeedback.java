package org.encinet.mik.feature.player.security;
import org.encinet.mik.shared.i18n.Message;

/** Translate domain failures at the presentation boundary, keeping login recovery specific to login. */
final class PasswordFeedback {
    private PasswordFeedback() { }

    static Message failure(PasswordException.Code code) {
        return switch (code) {
            case INVALID -> Message.PASSWORD_INVALID;
            case MISMATCH -> Message.PASSWORD_MISMATCH;
            case WEAK -> Message.PASSWORD_RULES;
            case CONFLICT -> Message.PASSWORD_CONFLICT;
            case REQUIRED_ROLE -> Message.PASSWORD_REQUIRED_ROLE;
            case KEEP_ROLE -> Message.PASSWORD_KEEP_ROLE;
            case REQUIRED_APPLICATION -> Message.PASSWORD_REQUIRED_APPLICATION;
            case DENIED -> Message.PASSWORD_DENIED;
            case SESSION_EXPIRED -> Message.PASSWORD_LOGIN_EXPIRED;
            case STORAGE -> Message.PASSWORD_STORAGE;
            case BUSY -> Message.PASSWORD_BUSY;
            case COOLDOWN -> Message.PASSWORD_COOLDOWN;
        };
    }

    static Message loginFailure(PasswordException.Code code) {
        return code == PasswordException.Code.CONFLICT ? Message.PASSWORD_LOGIN_EXPIRED : failure(code);
    }
}
