package org.encinet.mik.feature.entertainment.music.application;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stable user-facing failure categories; translated by entry points. */
public final class MusicProblem extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public enum Code {
        UNAVAILABLE,
        NOT_FOUND,
        FORBIDDEN,
        CONFLICT,
        LIMIT,
        INVALID,
        INTERNAL,
        OFFLINE,
        OVERLAP,
        UNSUPPORTED,
        BUSY,
        NO_SAFE_DESTINATION,
        TELEPORT_FAILED
    }
    private final Code code;
    public MusicProblem(Code code) {
        super(code.name());
        this.code = code;
    }
    public Code code() {
        return code;
    }

    /** Only explicit domain failures describe a player's input or an expected operation failure. */
    public static Code code(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof MusicProblem problem) return problem.code();
        if (cause instanceof IOException || cause instanceof TimeoutException || cause instanceof CancellationException)
            return Code.UNAVAILABLE;
        return Code.INTERNAL;
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null && error.getCause() != error) error = error.getCause();
        return error;
    }

    public static Code report(Logger logger, String context, Throwable error) {
        Code code = code(error);
        if (code == Code.INTERNAL || !(unwrap(error) instanceof MusicProblem))
            logger.log(code == Code.INTERNAL ? Level.SEVERE : Level.WARNING, context, error);
        return code;
    }
}
