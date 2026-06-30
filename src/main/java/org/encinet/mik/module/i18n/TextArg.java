package org.encinet.mik.module.i18n;

public record TextArg(String name, Object value) {

    public static TextArg of(String name, Object value) {
        return new TextArg(name, value);
    }
}
