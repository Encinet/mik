package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SocialArgumentsTest {
    @Test
    void nativeOptionsCannotBeSilentlyIgnoredByTheArgumentContract() {
        var none = SocialArguments.none();
        assertInstanceOf(SocialArguments.Invalid.class, none.parser().apply(
                SocialCommandInput.nativeCommand("online", "", Map.of("player", "Alice"))));
        assertInstanceOf(SocialArguments.Valid.class, none.parser().apply(
                SocialCommandInput.nativeCommand("online", "", Map.of("locale", "ja_jp"))));
        var profile = SocialArguments.text(Message.SOCIAL_PROFILE_COMMAND_PARAMETERS, Set.of("player"),
                input -> input.options().getOrDefault("player", input.rawArgument()));
        assertEquals("Alice", assertInstanceOf(SocialArguments.Valid.class, profile.parser().apply(
                SocialCommandInput.nativeCommand("profile", "", Map.of("player", "Alice")))).value());
        assertInstanceOf(SocialArguments.Invalid.class, profile.parser().apply(
                SocialCommandInput.nativeCommand("profile", "", Map.of("target", "Alice"))));
    }
}
