package org.encinet.mik.module.identity;

/** An owned platform registration that remains visible until its last lease is closed. */
public interface IdentityPlatformRegistration extends AutoCloseable {

    IdentityPlatform platform();

    boolean isActive();

    @Override
    void close();
}
