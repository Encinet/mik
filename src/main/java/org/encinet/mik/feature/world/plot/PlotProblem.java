package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.i18n.Message;

/** A player-facing plot validation error resolved in the player's language. */
public final class PlotProblem extends IllegalArgumentException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final Message message;
    private final java.io.Serializable[] args;

    public PlotProblem(Message message, java.io.Serializable... args) {
        super(message.key());
        this.message = message;
        this.args = args.clone();
    }

    public Message message() { return message; }
    public Object[] args() { return args.clone(); }
}
