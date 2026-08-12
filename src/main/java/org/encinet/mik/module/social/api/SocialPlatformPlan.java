package org.encinet.mik.module.social.api;

import org.encinet.mik.module.social.command.SocialCommandSyntax;
import org.encinet.mik.module.social.runtime.SocialRuntimePolicy;

/** Immutable prepared configuration used for one platform generation. */
public interface SocialPlatformPlan {
    SocialRuntimePolicy runtimePolicy();

    SocialCommandSyntax commandSyntax();

    SocialPlatformSession open(SocialPlatformRuntimeContext context);
}
