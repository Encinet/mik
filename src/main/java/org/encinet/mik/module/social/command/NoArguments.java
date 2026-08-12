package org.encinet.mik.module.social.command;

/** Non-null decoded value for commands without arguments. */
public enum NoArguments {
    INSTANCE;

    public static NoArguments decode(SocialCommandInput ignored) {
        return INSTANCE;
    }
}
