package org.encinet.mik.module.afk;

public enum AfkSource {
    MANUAL("manual"),
    AUTOMATIC("automatic"),
    SKRIPT("skript");

    private final String id;

    AfkSource(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
