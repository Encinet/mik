package org.encinet.mik.feature.community.governance.platform.paper.role;

import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.*;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.model.group.*;
import net.luckperms.api.model.user.*;
import net.luckperms.api.node.*;
import net.luckperms.api.node.matcher.*;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.context.ContextManager;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LuckPermsStaffDirectoryTest {
    @Test void storedAndInheritedRolesIncludeOfflineStaffWithoutLoadingUnrelatedUsers() {
        var provider = new Provider();
        provider.add(1,"OfflineModerator","moderator",true,false,List.of("moderator"));
        provider.add(2,"OfflineCustodian","senior",true,false,List.of("moderator","custodian"));
        provider.add(3,"Member","member",true,false,List.of("member"));
        provider.add(4,"Denied","moderator",false,false,List.of());
        provider.add(5,"Expired","moderator",true,true,List.of());
        provider.add(6,"OtherContext","moderator",true,false,List.of());
        try (var directory = new LuckPermsStaffDirectory(provider.api(),this,provider.clock)) {
            var people = directory.members().join();
            assertEquals(List.of("OfflineCustodian","OfflineModerator"),people.stream().map(person -> person.name()).toList());
            assertEquals(PlayerRole.CUSTODIAN,people.getFirst().role());
            assertFalse(people.getFirst().subjectToRemoval());
            assertTrue(people.get(1).subjectToRemoval());
            assertEquals(Set.of(id(1),id(2),id(6)),provider.loads);
            assertEquals(people,directory.members().join());
            assertEquals(1,provider.searches.get());
            provider.changed.accept(null);
            directory.members().join();
            assertEquals(2,provider.searches.get());
            provider.clock.time += 5_001;
            directory.members().join();
            assertEquals(3,provider.searches.get());
        }
        assertTrue(provider.closed.get());
    }

    @Test void overlappingReadsCoalesceAndAnInvalidationDuringTheReadCannotPublishAStaleCache() {
        var provider = new Provider();
        provider.add(1,"Mod","moderator",true,false,List.of("moderator"));
        provider.ready = new CompletableFuture<>();
        var directory = new LuckPermsStaffDirectory(provider.api(),this,provider.clock);
        var first = directory.members();
        var second = directory.members();
        assertEquals(1,provider.groupReads.get());
        directory.invalidate();
        provider.ready.complete(null);
        assertEquals(first.join(),second.join());
        directory.members().join();
        assertEquals(2,provider.groupReads.get());
        directory.close();
        assertThrows(CompletionException.class,() -> directory.members().join());
    }

    @Test void providerFailureDoesNotWedgeSubsequentRequests() {
        var provider = new Provider();
        provider.ready = CompletableFuture.failedFuture(new IllegalStateException("storage unavailable"));
        try (var directory = new LuckPermsStaffDirectory(provider.api(),this,provider.clock)) {
            assertThrows(CompletionException.class,() -> directory.members().join());
            provider.ready = CompletableFuture.completedFuture(null);
            assertEquals(List.of(),directory.members().join());
        }
    }

    @Test void inheritedStaffArePublicWithoutOfferingRemovalOfAnUnownedGroupGrant() {
        var provider = new Provider();
        provider.groups.put("helpers",proxy(Group.class,(_,m,_) -> switch(m.getName()) {
            case "getName" -> "helpers";
            case "getInheritedGroups" -> List.of(provider.groups.get("moderator"));
            default -> unexpected(m);
        }));
        provider.add(7,"InheritedStaff","helpers",true,false,List.of("helpers","moderator"));
        try (var directory = new LuckPermsStaffDirectory(provider.api(),this,provider.clock)) {
            var person = directory.members().join().getFirst();
            assertEquals(PlayerRole.MODERATOR,person.role());
            assertFalse(person.subjectToRemoval());
        }
    }

    private static UUID id(int index) { return new UUID(0,index); }
    private static <T> T proxy(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[] {type},(p,m,a) -> switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            case "toString" -> type.getSimpleName();
            default -> calls.invoke(p,m,a);
        }));
    }
    private static Object unexpected(Method method) { throw new UnsupportedOperationException(method.getName()); }

    private static final class Provider {
        final Map<UUID,Collection<Node>> assignments = new HashMap<>();
        final Map<UUID,User> users = new HashMap<>();
        final Set<UUID> loads = new HashSet<>();
        final AtomicInteger searches = new AtomicInteger(),groupReads = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean();
        final TestClock clock = new TestClock();
        final Map<String,Group> groups = new HashMap<>();
        Consumer<NodeMutateEvent> changed;
        CompletableFuture<Void> ready = CompletableFuture.completedFuture(null);

        Provider() {
            for (String name : List.of("member","moderator","custodian","senior"))
                groups.put(name,proxy(Group.class,(_,m,_) -> switch(m.getName()) {
                    case "getName" -> name;
                    case "getInheritedGroups" -> name.equals("senior") ? List.of(groups.get("custodian")) : List.of();
                    default -> unexpected(m);
                }));
        }
        void add(int index,String name,String group,boolean value,boolean expired,List<String> effective) {
            var node = proxy(InheritanceNode.class,(_,m,_) -> switch(m.getName()) {
                case "getType" -> NodeType.INHERITANCE;
                case "getValue" -> value;
                case "hasExpired" -> expired;
                case "getGroupName" -> group;
                default -> unexpected(m);
            });
            assignments.put(id(index),List.of(node));
            users.put(id(index),proxy(User.class,(_,m,_) -> switch(m.getName()) {
                case "getUniqueId" -> id(index);
                case "getUsername" -> name;
                case "getNodes" -> List.of(node);
                case "getInheritedGroups" -> effective.stream().map(groups::get).toList();
                default -> unexpected(m);
            }));
        }
        LuckPerms api() {
            var userManager = proxy(UserManager.class,(_,m,a) -> switch(m.getName()) {
                case "searchAll" -> { searches.incrementAndGet(); yield CompletableFuture.completedFuture(assignments); }
                case "getLoadedUsers" -> Set.of();
                case "loadUser" -> { loads.add((UUID)a[0]); yield CompletableFuture.completedFuture(users.get(a[0])); }
                default -> unexpected(m);
            });
            var groupManager = proxy(GroupManager.class,(_,m,_) -> switch(m.getName()) {
                case "loadAllGroups" -> { groupReads.incrementAndGet(); yield ready; }
                case "getLoadedGroups" -> new HashSet<>(groups.values());
                default -> unexpected(m);
            });
            var events = proxy(EventBus.class,(_,m,a) -> {
                if (!m.getName().equals("subscribe")) return unexpected(m);
                @SuppressWarnings("unchecked") // EventBus.subscribe binds this callback to NodeMutateEvent.
                var consumer = (Consumer<NodeMutateEvent>)a[a.length-1];
                changed = consumer;
                return proxy(EventSubscription.class,(_,n,_) -> { if(n.getName().equals("close")) {closed.set(true);return null;} return unexpected(n); });
            });
            var context = proxy(ContextManager.class,(_,_,_) -> null);
            var matchers = proxy(NodeMatcherFactory.class,(_,_,_) -> proxy(NodeMatcher.class,(_,_,_) -> true));
            return proxy(LuckPerms.class,(_,m,_) -> switch(m.getName()) {
                case "getUserManager" -> userManager;
                case "getGroupManager" -> groupManager;
                case "getEventBus" -> events;
                case "getContextManager" -> context;
                case "getNodeMatcherFactory" -> matchers;
                default -> unexpected(m);
            });
        }
    }
    private static final class TestClock extends Clock {
        long time;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(time); }
    }
}
