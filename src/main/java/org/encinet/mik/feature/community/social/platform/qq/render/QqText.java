package org.encinet.mik.feature.community.social.platform.qq.render;

import java.util.Locale;

/** Literal text rules shared by QQ chat, command replies and notices. */
final class QqText {
    private static final String MARKDOWN_CHARACTERS = "&\\`*_[]<>#+-!|~$=@";

    private QqText() { }

    static boolean containsInteractionSyntax(String text) {
        // Even msg_type=0 can interpret QQ interaction tags and the legacy broadcast token.
        return text.indexOf('<') >= 0 || text.toLowerCase(Locale.ROOT).contains("@everyone");
    }

    static String markdownLiteral(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            // Backslash-parenthesis/bracket pairs can invoke QQ math rendering.
            // Encode literal syntax without creating those delimiters.
            if (MARKDOWN_CHARACTERS.indexOf(character) >= 0 || character == '.' && orderedMarker(value, index)) {
                result.append("&#").append((int) character).append(';');
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static boolean orderedMarker(String value, int dot) {
        if (dot == 0 || dot + 1 >= value.length() || !Character.isWhitespace(value.charAt(dot + 1))) return false;
        int start = dot;
        while (start > 0 && value.charAt(start - 1) >= '0' && value.charAt(start - 1) <= '9') start--;
        if (start == dot) return false;
        while (start > 0 && (value.charAt(start - 1) == ' ' || value.charAt(start - 1) == '\t')) start--;
        return start == 0 || value.charAt(start - 1) == '\n' || value.charAt(start - 1) == '\r';
    }
}
