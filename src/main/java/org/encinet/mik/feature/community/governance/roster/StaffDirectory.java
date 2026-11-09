package org.encinet.mik.feature.community.governance.roster;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Includes offline staff; implementations own permission-provider I/O and caching. */
@FunctionalInterface
public interface StaffDirectory {
    CompletionStage<List<StaffMember>> members();
}
