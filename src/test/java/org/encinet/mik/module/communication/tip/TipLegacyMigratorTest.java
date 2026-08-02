package org.encinet.mik.module.communication.tip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipLegacyMigratorTest {

    private final TipLegacyMigrator migrator = new TipLegacyMigrator();

    @Test
    void recognizesTheBundledV1DocumentAcrossWhitespaceChanges() {
        String source = """
                <aqua>使用 /spawn 可以随时回到主城</aqua>
                ===
                <yellow>挂机时可以使用 /afk <gray>稍后回来</gray> 设置头顶状态；上线后可以用 /announcements 查看最近公告</yellow>
                ===
                <green>成员可以使用 /sethome 设置家，之后用 /home 回去</green>
                ===
                <gold>遇到问题时，先查看官网 mcmik.top，那里通常有最新说明</gold>
                """;

        assertTrue(migrator.isBundledV1(source));
        assertFalse(migrator.isBundledV1("<aqua>custom</aqua>"));
    }

    @Test
    void preservesCustomVisibleTextAndInfersSemanticCommandsAndTopics() {
        String migrated = migrator.migrate("""
                <green>Try /home & keep <gray>important</gray> items</green>
                ===
                Visit example.org
                """);
        TipCatalog.LoadResult parsed = new TipCatalog().parse(migrated);

        assertTrue(parsed.errors().isEmpty(), () -> String.join("\n", parsed.errors()));
        assertEquals(2, parsed.entries().size());
        assertEquals(java.util.Set.of("home"), parsed.entries().get(0).topics());
        assertEquals("Try /home & keep important items",
                parsed.entries().get(0).template().plainText());
        assertEquals(java.util.Set.of("website"), parsed.entries().get(1).topics());
        assertTrue(parsed.entries().get(0).supports(TipScene.PERIODIC));
        assertEquals(java.util.Set.of(TipScene.CHAT), parsed.entries().get(0).triggers());
    }
}
