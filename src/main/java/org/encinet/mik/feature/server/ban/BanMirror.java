package org.encinet.mik.feature.server.ban;

interface BanMirror {

    default void requireWriteThread() {
    }

    void upsert(BanRecord record);

    void pardon(BanRecord record);
}
