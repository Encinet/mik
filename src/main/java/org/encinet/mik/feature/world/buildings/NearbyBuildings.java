package org.encinet.mik.feature.world.buildings;

import java.util.List;

public record NearbyBuildings(String world, String filter, int offset, int totalMatches,
                              List<Building> buildings, Integer nextOffset) {
    public NearbyBuildings { buildings = List.copyOf(buildings); }

    public record Search(String filter, int offset, int limit) {
        static final int MAX_PAGE_SIZE = 16;
        static final int MAX_FILTER_LENGTH = 256;

        public Search {
            if (offset < 0 || offset > BuildingCatalog.MAX_ENTRIES || limit < 1 || limit > MAX_PAGE_SIZE
                    || filter.length() > MAX_FILTER_LENGTH || filter.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid building search");
            }
            filter = filter.strip();
        }
    }

    public record Building(String id, String name, String buildType, int x, int y, int z, int distanceBlocks) { }
}
