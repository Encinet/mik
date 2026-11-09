package org.encinet.mik.feature.world.space;

import java.util.List;

final class SpaceConfigurationException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 1L;


    private final String[] problems;

    SpaceConfigurationException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = problems.toArray(String[]::new);
    }

    List<String> problems() {
        return List.of(problems);
    }
}
