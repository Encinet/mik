package org.encinet.mik.module.space;

import java.util.List;

public record SpaceReloadResult(boolean successful, int linkCount, List<String> problems) {

    public SpaceReloadResult {
        problems = List.copyOf(problems);
    }

    public static SpaceReloadResult success(int linkCount) {
        return new SpaceReloadResult(true, linkCount, List.of());
    }

    public static SpaceReloadResult failure(List<String> problems) {
        return new SpaceReloadResult(false, 0, problems);
    }
}
