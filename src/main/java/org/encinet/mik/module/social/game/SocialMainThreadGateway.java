package org.encinet.mik.module.social.game;

import java.util.concurrent.Callable;

/** Executes only the snapshot reads that require the Minecraft server thread. */
@FunctionalInterface
public interface SocialMainThreadGateway {
    <T> T call(Callable<T> task);
}
