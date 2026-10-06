package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;

/** A player-facing plot validation error resolved in the player's language. */
public final class PlotProblem extends IllegalArgumentException {
    private final Message message;
    private final Object[] args;

    public PlotProblem(Message message, Object... args) {
        super(message.key());
        this.message = message;
        this.args = args;
    }

    public Message message() { return message; }
    public Object[] args() { return args; }
}
