package org.encinet.mik.module.social.platform.qq;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqPackageArchitectureTest {
    private static final Path QQ = Path.of(
            "src/main/java/org/encinet/mik/module/social/platform/qq");

    @Test
    void adapterContainsProtocolCapabilitiesButNoSharedBusinessRuntime() throws IOException {
        assertTrue(Files.exists(QQ.resolve("client")));
        assertTrue(Files.exists(QQ.resolve("gateway")));
        assertFalse(Files.exists(QQ.resolve("webhook/QqWebhookServer.java")));
        assertTrue(Files.exists(QQ.resolve("render")));
        assertFalse(Files.exists(QQ.resolve("command")));
        assertFalse(Files.exists(QQ.resolve("runtime")));
        assertFalse(Files.exists(QQ.resolve("safety")));

        try (Stream<Path> paths = Files.walk(QQ)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("QqServerCommand"), path.toString());
                assertFalse(source.contains("QqBindingCommand"), path.toString());
                assertFalse(source.contains("IdentityBindingManager"), path.toString());
                assertFalse(source.contains("Bukkit.getScheduler"), path.toString());
                assertFalse(source.contains("com.sun.net.httpserver"), path.toString());
                assertFalse(source.contains("QqWebhook"), path.toString());
                assertFalse(source.contains("QqCommandCatalog"), path.toString());
            }
        }
    }

    @Test
    void compositionRootUsesSocialModuleInsteadOfQqModule() throws IOException {
        String mik = Files.readString(Path.of("src/main/java/org/encinet/mik/Mik.java"));
        assertTrue(mik.contains("SocialModule"));
        assertFalse(mik.contains("QqBotModule"));
        String module = Files.readString(
                Path.of("src/main/java/org/encinet/mik/module/social/SocialModule.java"));
        String managementCommand = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/social/management/"
                        + "SocialManagementCommandRegistrar.java"));
        assertTrue(module.contains("new QqPlatformAdapter"));
        assertTrue(module.contains("new SocialManagementCommandRegistrar"));
        assertTrue(managementCommand.contains("Commands.literal(\"social\")"));
        assertFalse(module.contains("Commands.literal(\"qqbot\")"));
        assertFalse(module.contains("Commands.literal(\"qqbind\")"));
        assertFalse(managementCommand.contains("Commands.literal(\"qqbot\")"));
        assertFalse(managementCommand.contains("Commands.literal(\"qqbind\")"));
    }

    @Test
    void gatewaySessionDelegatesFramesTimersAndReconnectPolicy() throws IOException {
        Path gateway = QQ.resolve("gateway");
        assertTrue(Files.exists(gateway.resolve("QqGatewaySocketListener.java")));
        assertTrue(Files.exists(gateway.resolve("QqGatewayTimers.java")));
        assertTrue(Files.exists(gateway.resolve("QqGatewayReconnectPolicy.java")));

        String session = Files.readString(gateway.resolve("QqGatewaySession.java"));
        assertFalse(session.contains("implements WebSocket.Listener"));
        assertFalse(session.contains("ScheduledExecutorService"));
        assertFalse(session.contains("isFatalCloseCode"));
    }
}
