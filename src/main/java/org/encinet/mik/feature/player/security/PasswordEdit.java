package org.encinet.mik.feature.player.security;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.PlayerRole;

/** The current edit determines its instructions, restrictions and completion feedback. */
enum PasswordEdit {
    SET(Message.PASSWORD_SET, Message.PASSWORD_RULES, Message.PASSWORD_SET_DONE),
    CHANGE(Message.PASSWORD_CHANGE, Message.PASSWORD_RULES, Message.PASSWORD_CHANGE_DONE),
    REMOVE(Message.PASSWORD_REMOVE, Message.PASSWORD_REMOVE_QUESTION, Message.PASSWORD_REMOVE_DONE);

    final Message title;
    final Message instructions;
    final Message completed;

    PasswordEdit(Message title, Message instructions, Message completed) {
        this.title = title;
        this.instructions = instructions;
        this.completed = completed;
    }

    Message restriction(boolean present, PlayerRole role) {
        if (this == SET && present) return Message.PASSWORD_ALREADY_SET;
        if (this != SET && !present) return Message.PASSWORD_NOT_SET;
        if (PasswordPolicy.mandatory(role)) {
            if (this == SET) return Message.PASSWORD_REQUIRED_ROLE;
            if (this == REMOVE) return Message.PASSWORD_KEEP_ROLE;
        }
        return null;
    }
}
