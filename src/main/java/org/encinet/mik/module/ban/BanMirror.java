package org.encinet.mik.module.ban;

interface BanMirror {

    default void requireWriteThread() {
    }

    void upsert(BanRecord record);

    void pardon(BanRecord record);
}
