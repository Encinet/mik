package org.encinet.mik.module.social.runtime;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Narrow control and query surface used by the Bukkit management adapter. */
public interface SocialPlatformAdmin {
    void reloadAll();

    boolean reload(String platformId);

    Optional<SocialPlatformSnapshot> status(String platformId);

    List<SocialPlatformSnapshot> platforms();

    Set<String> conversations(String platformId);
}
