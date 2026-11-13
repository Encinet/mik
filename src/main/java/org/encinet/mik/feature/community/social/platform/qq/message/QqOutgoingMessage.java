package org.encinet.mik.feature.community.social.platform.qq.message;

import java.util.Objects;

/** One QQ text or Markdown payload; the HTTP client owns its wire representation. */
public record QqOutgoingMessage(Format format, String content) {
    public QqOutgoingMessage {
        Objects.requireNonNull(format, "format");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("QQ message content must not be blank");
        }
    }

    public static QqOutgoingMessage text(String content) { return new QqOutgoingMessage(Format.TEXT, content); }

    public static QqOutgoingMessage markdown(String content) { return new QqOutgoingMessage(Format.MARKDOWN, content); }

    public enum Format {
        TEXT(0), MARKDOWN(2);

        private final int code;

        Format(int code) { this.code = code; }

        public int code() { return code; }
    }
}
