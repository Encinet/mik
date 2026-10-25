package org.encinet.mik.feature.community.social.runtime;

import org.encinet.mik.feature.community.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.feature.community.social.api.SocialPlatformSession;
import org.encinet.mik.feature.community.social.command.SocialCommandSyntax;

/** Immutable prepared configuration used for one platform generation. */
public interface SocialPlatformPlan {
    SocialRuntimePolicy runtimePolicy();

    SocialCommandSyntax commandSyntax();

    SocialPlatformSession open(SocialPlatformRuntimeContext context);
}
