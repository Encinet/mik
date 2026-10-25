package org.encinet.mik.shell.api;

import java.sql.SQLException;

/** Read-only public board content supplied by the plot feature. */
public interface CommunityBoardView {
    String listJson() throws SQLException;

    String detailJson(String id) throws SQLException;
}
