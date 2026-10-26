package org.encinet.mik.feature.player.teleport;

import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeTipMentionTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"怎么设家？", "怎么设置家", "如何回家", "家园在哪里", "家園怎麼用", "設置家園",
            "我家还没有保存", "how do I set a home?", "Can I save HOMES?", "/sethome 的名字怎么写", "/home怎么用？"})
    void recognizesExplicitHomeMentionsAndCommands(String text) {
        assertEquals(1, offered(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "大家好", "HomeAssistant", "homework", "homepage", "homestead", "someonehome",
            "https://home.example.net", "https://example.net/home", "www.example.net/sethome"})
    void ignoresUnrelatedWordsAndLinks(String text) {
        assertEquals(0, offered(text));
    }

    private int offered(String text) {
        UUID id = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    throw new UnsupportedOperationException(method.getName());
                });
        try (HomeStore store = new HomeStore(directory.resolve("homes.yml"), Logger.getAnonymousLogger());
             TipTestScope tips = new TipTestScope()) {
            new HomeModule(null, new LanguageService(null), tips, store);
            tips.chat(player, text);
            return tips.collect(player, 2000).size();
        }
    }
}
