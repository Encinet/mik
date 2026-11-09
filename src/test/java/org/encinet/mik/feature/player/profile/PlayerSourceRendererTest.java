package org.encinet.mik.feature.player.profile;

import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSourceRendererTest {
    @Test void gameAndWebShareLocalizedLabelsWithoutSharingTheirConnectionFacts() throws Exception {
        var bundles = Map.of(Language.ZH_CN, FluentTestSupport.bundle(Language.ZH_CN),
                Language.EN_US, FluentTestSupport.bundle(Language.EN_US));
        LanguageService language = new LanguageService(null) {
            @Override public Language language(Player player) { return Language.EN_US; }
            @Override public String t(Language locale, Message message, Object... args) {
                return FluentTestSupport.text(bundles.get(locale), message, args);
            }
        };
        Player bedrock = player(UUID.randomUUID());
        var queries = new ArrayList<UUID>();
        var sources = new PlayerSourceRenderer(id -> { queries.add(id); return id.equals(bedrock.getUniqueId()); }, language);
        assertEquals("基岩", sources.game(bedrock, Language.ZH_CN).label());
        assertEquals("Bedrock", sources.game(bedrock, Language.EN_US).label());
        assertEquals("Web", sources.web(bedrock).label());
        assertEquals("Web", sources.webTabList(bedrock, 1).label());
        assertEquals("Web ×3", sources.webTabList(bedrock, 3).label());
        assertEquals(2, queries.size(), "Web labels never infer a live game client's source");
        assertEquals(PlayerSource.NONE, sources.game(player(UUID.randomUUID()), Language.EN_US));
    }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (_, method, _) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    throw new AssertionError(method.getName());
                });
    }
}
