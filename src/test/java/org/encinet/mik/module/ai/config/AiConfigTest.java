package org.encinet.mik.module.ai.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.i18n.Language;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConfigTest {

    @Test
    void defaultsToEveryBuiltInCapabilityPackAndProgressiveToolBudget() throws Exception {
        AiConfig config = AiConfig.from(yaml("""
                active-provider: custom
                providers:
                  custom:
                    endpoint: https://ai.example.test/v1/chat/completions
                    model: model-a
                system-prompts:
                  default: Default prompt
                """));

        assertEquals(AiConfig.Tools.BUILT_IN_PACKS, config.tools().enabledPacks());
        assertTrue(config.tools().webSearch().enabled());
        assertTrue(config.tools().webFetch().enabled());
        assertEquals(16_000, config.tools().webFetch().maxContentCharacters());
        assertFalse(config.tools().includePlayerLocations());
        assertEquals(6, config.limits().maxToolRounds());
        assertEquals(512, config.limits().maxConversations());
        assertEquals(8, config.limits().maxBlockingWorkers());
        assertEquals(128, config.limits().maxWorkerQueue());
        assertTrue(config.knowledge().enabled());
        assertTrue(config.knowledge().learning().enabled());
        assertTrue(config.knowledge().memory().enabled());
        assertEquals("custom", config.knowledge().learning().provider());
        assertEquals(1_800, config.knowledge().index().maxChunkCharacters());
        assertEquals(200, config.knowledge().index().overlapCharacters());
        assertEquals(4_000, config.knowledge().memory().maxContextCharacters());
    }

    @Test
    void resolvesCustomProviderSecretsHeadersOptionsAndLocalizedPrompts() throws Exception {
        AiConfig config = AiConfig.from(yaml("""
                enabled: true
                active-provider: gateway
                providers:
                  gateway:
                    endpoint: https://gateway.example.test/chat/completions?api-version=7
                    model: ${MODEL_NAME:-fallback-model}
                    api-key: ${AI_TEST_KEY}
                    auth-header: X-Api-Key
                    auth-prefix: ""
                    headers:
                      X-Tenant: ${TENANT_NAME:-public}
                    request-options:
                      temperature: 0.15
                      response_format:
                        type: json_object
                tools:
                  enabled-packs: [utility, world]
                system-prompts:
                  default: Answer in the question language.
                  zh_cn: 请按问题所用语言回答。
                """), name -> Map.of(
                "AI_TEST_KEY", "secret-value",
                "MODEL_NAME", "vendor-model").get(name));

        assertTrue(config.enabled());
        assertEquals("gateway", config.provider().id());
        assertEquals("vendor-model", config.provider().model());
        assertEquals("secret-value", config.provider().apiKey());
        assertEquals("public", config.provider().headers().get("X-Tenant"));
        assertEquals(0.15,
                config.provider().requestOptions().get("temperature").getAsDouble());
        assertEquals("json_object", config.provider().requestOptions()
                .getAsJsonObject("response_format").get("type").getAsString());
        assertEquals("请按问题所用语言回答。", config.systemPrompt("zh-CN"));
        assertEquals("Answer in the question language.", config.systemPrompt("fr-FR"));
        assertEquals(java.util.Set.of("utility", "world"),
                config.tools().enabledPacks());
    }

    @Test
    void bundledConfigurationProvidesAPromptForEveryInterfaceLanguage() {
        AiConfig config = AiConfig.from(YamlConfiguration.loadConfiguration(
                new File("src/main/resources/ai.yml")));

        for (Language language : Language.values()) {
            assertTrue(config.systemPrompts().containsKey(language.id()), language.id());
            assertFalse(config.systemPrompt(language.id()).isBlank(), language.id());
        }
    }

    @Test
    void rejectsUnknownPacksAndReservedProviderOverrides() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> AiConfig.from(yaml("""
                active-provider: custom
                providers:
                  custom:
                    endpoint: https://ai.example.test/v1/chat/completions
                    model: model-a
                tools:
                  enabled-packs: [server, shell]
                system-prompts:
                  default: Prompt
                """)));
        assertThrows(IllegalArgumentException.class, () -> AiConfig.from(yaml("""
                active-provider: custom
                providers:
                  custom:
                    endpoint: https://ai.example.test/v1/chat/completions
                    model: model-a
                    request-options:
                      messages: forged
                system-prompts:
                  default: Prompt
                """)));
    }

    @Test
    void rejectsInvalidKnowledgeIndexAndLearningProvider() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> AiConfig.from(yaml("""
                active-provider: custom
                providers:
                  custom:
                    endpoint: https://ai.example.test/v1/chat/completions
                    model: model-a
                knowledge:
                  index:
                    max-chunk-characters: 1000
                    overlap-characters: 1000
                system-prompts:
                  default: Prompt
                """)));
        assertThrows(IllegalArgumentException.class, () -> AiConfig.from(yaml("""
                active-provider: custom
                providers:
                  custom:
                    endpoint: https://ai.example.test/v1/chat/completions
                    model: model-a
                knowledge:
                  learning:
                    provider: missing
                system-prompts:
                  default: Prompt
                """)));
    }

    private static YamlConfiguration yaml(String source) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return yaml;
    }
}
