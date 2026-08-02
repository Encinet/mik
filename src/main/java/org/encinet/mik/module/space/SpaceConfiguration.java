package org.encinet.mik.module.space;

import java.util.List;

record SpaceConfiguration(boolean enabled, List<SpaceLink> links) {

    SpaceConfiguration {
        links = List.copyOf(links);
    }
}
