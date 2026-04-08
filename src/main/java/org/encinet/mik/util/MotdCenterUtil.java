package org.encinet.mik.util;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class MotdCenterUtil {

    // Minecraft 默认 MOTD 居中的基准像素值 (总长约254px)
    private static final int CENTER_PX = 127;

    /**
     * 居中含有 MiniMessage 标签的字符串
     */
    public static String center(String miniMessageText) {
        if (miniMessageText == null || miniMessageText.isEmpty()) return "";

        // 剥离 MiniMessage 标签，获取要在屏幕上实际渲染的纯文本
        String rawText = MiniMessage.miniMessage().stripTags(miniMessageText);

        // 计算像素总宽度
        int width = 0;
        for (char c : rawText.toCharArray()) {
            width += getCharWidth(c) + 1; // +1 是因为 Minecraft 字体中每个字符之间有 1px 的间距
        }

        // 计算需要补偿的像素值并转换为前置空格数
        int compensate = CENTER_PX - (width / 2);
        int spaceWidth = getCharWidth(' ') + 1; // 空格宽度 3 + 间距 1 = 4
        int spaces = compensate / spaceWidth;

        if (spaces <= 0) return miniMessageText;

        return " ".repeat(spaces) + miniMessageText;
    }

    /**
     * 获取单个字符在标准 Minecraft 字体下的像素宽度
     */
    private static int getCharWidth(char c) {
        // 处理特殊宽度 ASCII 字符 (标点符号和窄字母)
        switch (c) {
            case 'i': case 'l': case '!': case '.': case ',': case ':': case ';': case '\'': case '|':
                return 1;
            case '`':
                return 2;
            case ' ': case 'I': case '[': case ']': case '"':
                return 3;
            case 'f': case 'k': case 't': case '(': case ')': case '{': case '}': case '<': case '>':
                return 4;
            case '@':
                return 6;
        }

        // 处理 CJK (中日韩) 字符，中文字符在游戏中通常为全角，占 9px
        if ((c >= 0x4E00 && c <= 0x9FFF) || // CJK 统一表意文字
                (c >= 0x3400 && c <= 0x4DBF) || // CJK 扩展 A
                (c >= 0x3000 && c <= 0x303F) || // CJK 符号和标点
                (c >= 0xFF00 && c <= 0xFFEF)) { // 全角 ASCII / 宽字符
            return 9;
        }

        // 其他标准字母、数字等默认宽度均为 5px
        return 5;
    }
}
