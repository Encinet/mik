package org.encinet.mik.module.space;

import java.util.List;

final class SpaceConfigurationException extends Exception {

    private final List<String> problems;

    SpaceConfigurationException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    List<String> problems() {
        return problems;
    }
}
