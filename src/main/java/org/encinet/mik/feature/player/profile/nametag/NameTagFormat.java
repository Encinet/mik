package org.encinet.mik.feature.player.profile.nametag;

import org.encinet.mik.shared.util.NameMetaRenderer;

import java.util.Optional;

/** Shared source validation for field edits and persistence. */
final class NameTagFormat {
    static final int MAX_LENGTH = 200;
    enum Problem { TOO_LONG, UNSUPPORTED_PLACEHOLDER, INVALID }
    record Failure(Problem problem, String detail) { }

    private NameTagFormat() { }

    static Optional<Failure> validate(String raw) {
        if (raw.codePointCount(0, raw.length()) > MAX_LENGTH)
            return Optional.of(new Failure(Problem.TOO_LONG, ""));
        if ((!raw.isEmpty() && raw.isBlank()) || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0)
            return Optional.of(new Failure(Problem.INVALID, ""));
        String unsupported = NameMetaRenderer.findUnsupportedPlaceholder(raw);
        if (unsupported != null) return Optional.of(new Failure(Problem.UNSUPPORTED_PLACEHOLDER, unsupported));
        try { NameMetaRenderer.deserialize(raw); }
        catch (RuntimeException error) { return Optional.of(new Failure(Problem.INVALID, "")); }
        return Optional.empty();
    }
}
