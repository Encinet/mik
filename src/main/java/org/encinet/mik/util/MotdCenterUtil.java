package org.encinet.mik.util;

import net.kyori.adventure.text.minimessage.MiniMessage;

public class MotdCenterUtil {

    // MOTD 标准半宽（总长 254px 左右）
    private static final int CENTER_PX = 127;

    public static String center(String miniMessageText) {
        if (miniMessageText == null || miniMessageText.isEmpty()) return "";

        String rawText = MiniMessage.miniMessage().stripTags(miniMessageText);

        int width = 0;
        for (char c : rawText.toCharArray()) {
            width += getCharPixelWidth(c);
        }

        int compensate = CENTER_PX - (width / 2);
        // 空格在 Minecraft 中固定占 4 像素（3px 宽度 + 1px 间距）
        int spaceWidth = 4;
        int spaces = compensate / spaceWidth;

        if (spaces <= 0) return miniMessageText;

        return " ".repeat(spaces) + miniMessageText;
    }

    private static int getCharPixelWidth(char c) {
        // 以下数值均为：字符宽度 + 1px 像素间距
        switch (c) {
            case '·': // 间隔符，实际非常窄
                return 3;
            case ' ', 'I', '[', ']', '"':
                return 4;
            case 'i': case 'l': case '!': case '.': case ',': case ':': case ';': case '\'': case '|':
                return 2;
            case '`':
                return 3;
            case 'f': case 'k': case 't': case '(': case ')': case '{': case '}': case '<': case '>':
                return 5;
            case '@':
                return 7;
        }

        // CJK 统一表意文字 (中文)
        // 在 Minecraft 中，中文字符是 8px 宽 + 1px 间距 = 9px
        if (isFullWidth(c)) {
            return 9;
        }

        // 标准字母数字默认 5px + 1px 间距 = 6px
        return 6;
    }

    private static boolean isFullWidth(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) ||
                (c >= 0x3400 && c <= 0x4DBF) ||
                (c >= 0x3000 && c <= 0x303F) ||
                (c >= 0xFF00 && c <= 0xFFEF);
    }
}
