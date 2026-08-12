package org.encinet.mik.module.social.command;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.identity.IdentityBindingException;
import org.encinet.mik.module.identity.IdentityBindingManager;

import java.util.Objects;
import java.util.Optional;

/** Resolves a command language without defining any command-specific response. */
public final class SocialCommandLanguageResolver {
    private final IdentityBindingManager bindings;
    private final LanguageService languages;

    public SocialCommandLanguageResolver(
            IdentityBindingManager bindings,
            LanguageService languages
    ) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    public Optional<IdentityBinding> binding(SocialCommandContext context) {
        try {
            return context.message().authenticatedIdentity()
                    .flatMap(identity -> bindings.find(identity.key()));
        } catch (IdentityBindingException error) {
            return Optional.empty();
        }
    }

    public Language language(SocialCommandContext context) {
        return language(binding(context), context);
    }

    public Language language(
            Optional<IdentityBinding> binding,
            SocialCommandContext context
    ) {
        Optional<Language> preferred = binding.flatMap(
                value -> languages.preferredLanguage(value.playerId()));
        return preferred.or(() -> context.input().languageHint())
                .orElse(context.syntax().defaultLanguage());
    }
}
