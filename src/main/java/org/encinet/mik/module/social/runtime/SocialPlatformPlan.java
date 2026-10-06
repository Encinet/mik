package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.module.social.api.SocialPlatformSession;
import org.encinet.mik.module.social.command.SocialCommandSyntax;

/** Immutable prepared configuration used for one platform generation. */
public interface SocialPlatformPlan {
    SocialRuntimePolicy runtimePolicy();

    SocialCommandSyntax commandSyntax();

    SocialPlatformSession open(SocialPlatformRuntimeContext context);
}
