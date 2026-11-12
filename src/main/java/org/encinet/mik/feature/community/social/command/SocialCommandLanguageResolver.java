package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.feature.community.identity.IdentityBinding;
import org.encinet.mik.feature.community.identity.IdentityBindingException;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;

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

    public BindingLookup lookup(SocialCommandContext context) {
        try {
            return new BindingLookup(context.message().authenticatedIdentity()
                    .flatMap(identity -> bindings.find(identity.key())), true);
        } catch (IdentityBindingException error) {
            return new BindingLookup(Optional.empty(), false);
        }
    }

    public record BindingLookup(Optional<IdentityBinding> binding, boolean available) {}

    public SocialCommandExecution.LanguageTarget target(SocialCommandContext context) {
        return target(lookup(context).binding(), context);
    }

    public SocialCommandExecution.LanguageTarget target(Optional<IdentityBinding> binding, SocialCommandContext context) {
        return new SocialCommandExecution.LanguageTarget(context.input().languageHint()
                .orElse(context.syntax().defaultLanguage()), binding.map(IdentityBinding::playerId));
    }

    public Language language(Optional<IdentityBinding> binding, SocialCommandContext context) {
        return target(binding, context).resolve(languages);
    }
}
