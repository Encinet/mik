package org.encinet.mik.module.space;

/** Removable handle returned for a programmatically registered spatial link. */
public interface SpaceRegistration extends AutoCloseable {

    String linkId();

    boolean active();

    @Override
    void close();
}
