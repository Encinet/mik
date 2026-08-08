package org.encinet.mik.module.chat.modifier;

import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MinecraftWikiModifierTest {

    private final MinecraftWikiModifier modifier = new MinecraftWikiModifier();

    @Test
    void parsesEnglishPageAndKeepsBalancedParentheses() {
        String link = "https://minecraft.wiki/w/Java_Edition_(PC_Gamer_Demo)";
        String message = "See " + link + ".";

        ChatReplacement replacement = modifier.find(message, 0, null);

        assertEquals(4, replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertReplacement(replacement,
                "[Minecraft Wiki: Java Edition (PC Gamer Demo)]", link);
    }

    @Test
    void parsesChinesePageAndDecodesItsTitle() {
        String link = "zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3";
        String message = "请看" + link + "。";

        ChatReplacement replacement = modifier.find(message, 0, null);

        assertEquals(2, replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertReplacement(replacement, "[Minecraft Wiki: 钻石]", "https://" + link);
    }

    @Test
    void parsesEncodedChineseSubpagePath() {
        String link = "https://zh.minecraft.wiki/w/%E5%91%BD%E4%BB%A4/give";

        ChatReplacement replacement = modifier.find(link, 0, null);

        assertEquals(0, replacement.start());
        assertEquals(link.length(), replacement.end());
        assertReplacement(replacement, "[Minecraft Wiki: 命令/give]", link);
    }

    @Test
    void supportsBareLinksAndOtherLanguageSubdomains() {
        String link = "ja.minecraft.wiki/w/%E3%82%AF%E3%83%AA%E3%83%BC%E3%83%91%E3%83%BC";

        ChatReplacement replacement = modifier.find(link, 0, null);

        assertEquals(0, replacement.start());
        assertEquals(link.length(), replacement.end());
        assertReplacement(replacement, "[Minecraft Wiki: クリーパー]", "https://" + link);
    }

    @Test
    void parsesMediaWikiTitleQueryAndPreservesLanguageVariant() {
        String link = "https://zh.minecraft.wiki/w/index.php?title=%E5%91%BD%E4%BB%A4&variant=zh-tw";

        ChatReplacement replacement = modifier.find(link, 0, null);

        assertReplacement(replacement, "[Minecraft Wiki: 命令]", link);
    }

    @Test
    void decodesSpacesInMediaWikiTitleQuery() {
        String link = "https://minecraft.wiki/w/index.php?title=Java+Edition#History";

        ChatReplacement replacement = modifier.find(link, 0, null);

        assertReplacement(replacement, "[Minecraft Wiki: Java Edition]", link);
    }

    @Test
    void rendersTheWikiHomePageWithoutAPageTitle() {
        String link = "https://lzh.minecraft.wiki/";

        ChatReplacement replacement = modifier.find(link, 0, null);

        assertReplacement(replacement, "[Minecraft Wiki]", link);
    }

    @Test
    void rejectsLookalikeDomainsAndEmbeddedEmailDomains() {
        assertNull(modifier.find("https://minecraft.wiki.example/w/Diamond", 0, null));
        assertNull(modifier.find("user@zh.minecraft.wiki/w/Diamond", 0, null));
        assertNull(modifier.find("https://minecraft.wiki@evil.example/w/Diamond", 0, null));
        assertNull(modifier.find("minecraft.wiki:25565/w/Diamond", 0, null));
    }

    private void assertReplacement(ChatReplacement replacement, String label, String url) {
        assertEquals(label, ((TextComponent) replacement.component()).content());
        assertEquals(ClickEvent.openUrl(url), replacement.component().clickEvent());
    }
}
