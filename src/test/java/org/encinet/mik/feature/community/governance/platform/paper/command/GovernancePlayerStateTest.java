package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerState.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GovernancePlayerStateTest {
    @Test void outOfOrderPlayerResultsCannotReplaceTheNewTargetOrItsError() {
        var first = request(null,FIRST); var current = new AtomicReference<GovernancePlayerState>(first);
        var old = new CompletableFuture<GovernancePlayerReport.Data>();
        var retired = old.thenAccept(data -> current.updateAndGet(state -> complete(state,first,data)));
        var second = request(current.get(),SECOND); current.set(second);
        var newest = new CompletableFuture<GovernancePlayerReport.Data>();
        newest.thenAccept(data -> current.updateAndGet(state -> complete(state,second,data)));
        newest.complete(data(SECOND,2));
        var accepted = assertInstanceOf(Loaded.class,current.get()); assertEquals(SECOND,accepted.target());
        old.complete(data(FIRST,0));
        retired.join();
        assertSame(accepted,current.get());
        assertSame(accepted,fail(current.get(),first,Message.GOVERNANCE_ROLE_LOOKUP_ERROR));
        var retry = request(accepted,SECOND); current.set(fail(retry,retry,Message.GOVERNANCE_OPERATION_ERROR));
        assertSame(current.get(),complete(current.get(),retry,data(SECOND,0)));
    }

    @Test void repeatedRequestsForTheSamePlayerUseIdentityAndKeepReadingPositions() {
        var first = request(null,FIRST); var second = request(first,FIRST);
        assertEquals(first,second); assertNotSame(first,second);
        assertSame(second,complete(second,first,data(FIRST,0)));
        assertSame(second,fail(second,first,Message.GOVERNANCE_OPERATION_ERROR));
        var loaded = (Loaded) complete(second,second,data(FIRST,2));
        var position = new UiReadingPosition(UiKey.named("report"),173,0);
        var moved = (Loaded) read(loaded,loaded,position);
        assertSame(moved,read(moved,loaded,UiReadingPosition.START));
        assertEquals(position,request(moved,FIRST).position());
        assertEquals(UiReadingPosition.START,request(moved,SECOND).position());
        var foreign = request(null,SECOND);
        var wrong = assertInstanceOf(Failed.class,complete(foreign,foreign,data(FIRST,0)));
        assertEquals(SECOND,wrong.target());
        assertEquals(Message.GOVERNANCE_OPERATION_ERROR,wrong.reason());
    }
}
