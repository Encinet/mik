package org.encinet.mik.module.governance.platform.paper.role;

import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuckPermsGovernanceRolesTest {
    @Test
    void onlyModeratorsWithoutCustodianRoleEnterGovernanceTenure() {
        TestUser moderator = user("moderator", "moderator");
        assertTrue(LuckPermsGovernanceRoles.isGovernedModerator(moderator.user()));
        LuckPermsGovernanceRoles.removeModeratorRole(moderator.user());
        assertFalse(moderator.groups().contains("moderator"));
        assertEquals("member", moderator.primaryGroup().get());

        TestUser custodian = user("custodian", "moderator", "custodian");
        assertFalse(LuckPermsGovernanceRoles.isGovernedModerator(custodian.user()));
        LuckPermsGovernanceRoles.removeModeratorRole(custodian.user());
        assertTrue(custodian.groups().contains("moderator"));
        assertEquals("custodian", custodian.primaryGroup().get());

        assertFalse(LuckPermsGovernanceRoles.isGovernedModerator(
                user("manager", "manager", "helper").user()));
    }

    @Test
    void offlinePlayerInspectionRecognizesAllFormalMemberGroups() {
        for (String group : new String[] {"member", "moderator", "custodian"}) {
            assertTrue(LuckPermsGovernanceRoles.hasFormalMemberRole(
                    user(group, group).user()), group);
        }
        assertFalse(LuckPermsGovernanceRoles.hasFormalMemberRole(
                user("newcomer", "newcomer").user()));
        assertFalse(LuckPermsGovernanceRoles.hasFormalMemberRole(
                user("manager", "manager", "helper").user()));
    }

    private static TestUser user(String primary, String... groupNames) {
        Set<InheritanceNode> nodes = new LinkedHashSet<>();
        for (String name : groupNames) {
            nodes.add((InheritanceNode) Proxy.newProxyInstance(
                    InheritanceNode.class.getClassLoader(),
                    new Class<?>[] {InheritanceNode.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getGroupName" -> name;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        case "toString" -> name;
                        default -> throw new UnsupportedOperationException(method.getName());
                    }));
        }
        AtomicReference<String> primaryGroup = new AtomicReference<>(primary);
        Class<?> nodeMapType;
        try {
            nodeMapType = User.class.getMethod("data").getReturnType();
        } catch (NoSuchMethodException error) {
            throw new AssertionError(error);
        }
        Object nodeMap = Proxy.newProxyInstance(nodeMapType.getClassLoader(),
                new Class<?>[] {nodeMapType}, (proxy, method, arguments) -> {
                    if (method.getName().equals("remove")) {
                        nodes.remove(arguments[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        User user = (User) Proxy.newProxyInstance(User.class.getClassLoader(),
                new Class<?>[] {User.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getNodes" -> Set.copyOf(nodes);
                    case "data" -> nodeMap;
                    case "getPrimaryGroup" -> primaryGroup.get();
                    case "setPrimaryGroup" -> {
                        primaryGroup.set((String) arguments[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new TestUser(user, nodes, primaryGroup);
    }

    private record TestUser(User user, Set<InheritanceNode> nodes,
                            AtomicReference<String> primaryGroup) {
        Set<String> groups() {
            return nodes.stream().map(InheritanceNode::getGroupName).collect(java.util.stream.Collectors.toSet());
        }
    }
}
