package org.encinet.mik.module.skript;

import ch.njol.skript.lang.parser.ParserInstance;
import org.skriptlang.skript.lang.script.Script;

import java.nio.file.Path;

final class MikSkriptOwner {

    private MikSkriptOwner() {
    }

    static String current(ParserInstance parser) {
        Script script = parser.getCurrentScript();
        return of(script);
    }

    static String of(Script script) {
        String nameAndPath = script.nameAndPath();
        if (nameAndPath != null && !nameAndPath.isBlank()) {
            return nameAndPath;
        }
        Path path = script.getConfig().getPath();
        return path == null
                ? script.getConfig().getFileName()
                : path.toAbsolutePath().normalize().toString();
    }
}
