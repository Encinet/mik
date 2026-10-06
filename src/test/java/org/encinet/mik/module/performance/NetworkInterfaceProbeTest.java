package org.encinet.mik.module.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NetworkInterfaceProbeTest {
    @TempDir Path directory;

    @Test
    void choosesTheLowestMetricUsableIpv4DefaultRoute() throws Exception {
        Path devices = directory.resolve("devices");
        counter(devices, "eth0", "17");
        counter(devices, "eth1", "42");
        Path ipv4 = directory.resolve("route");
        Files.writeString(ipv4, """
                Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT
                lo 00000000 00000000 0003 0 0 0 00000000 0 0 0
                eth0 00000000 00000000 0003 0 0 100 00000000 0 0 0
                eth1 00000000 00000000 0003 0 0 10 00000000 0 0 0
                eth1 0000A8C0 00000000 0001 0 0 1 00000000 0 0 0
                """);

        NetworkInterfaceProbe probe = NetworkInterfaceProbe.discover(
                "auto", ipv4, directory.resolve("missing-ipv6"), devices);

        assertNotNull(probe);
        assertEquals("eth1", probe.interfaceName());
        assertEquals(42L, probe.readTransmitBytes());
    }

    @Test
    void fallsBackToIpv6WhenTheIpv4InterfaceHasNoReadableCounter() throws Exception {
        Path devices = directory.resolve("devices");
        counter(devices, "eth6", "51");
        Path ipv4 = directory.resolve("route");
        Files.writeString(ipv4,
                "eth4 00000000 00000000 0003 0 0 1 00000000 0 0 0\n");
        Path ipv6 = directory.resolve("ipv6_route");
        Files.writeString(ipv6, """
                00000000000000000000000000000000 00 00000000000000000000000000000000 00 00000000000000000000000000000000 0000000A 00000000 00000000 00000001 eth6
                """);

        NetworkInterfaceProbe probe = NetworkInterfaceProbe.discover("auto", ipv4, ipv6, devices);

        assertNotNull(probe);
        assertEquals("eth6", probe.interfaceName());
        assertEquals(51L, probe.readTransmitBytes());
    }

    @Test
    void usesAnotherIpv4RouteBeforeFallingBackToIpv6() throws Exception {
        Path devices = directory.resolve("devices");
        counter(devices, "eth0", "73");
        Path ipv4 = directory.resolve("route");
        Files.writeString(ipv4, """
                eth4 00000000 00000000 0003 0 0 1 00000000 0 0 0
                eth0 00000000 00000000 0003 0 0 100 00000000 0 0 0
                """);

        NetworkInterfaceProbe probe = NetworkInterfaceProbe.discover(
                "auto", ipv4, directory.resolve("missing-ipv6"), devices);

        assertNotNull(probe);
        assertEquals("eth0", probe.interfaceName());
        assertEquals(73L, probe.readTransmitBytes());
    }

    @Test
    void configuredInterfaceCannotEscapeTheDeviceDirectory() throws Exception {
        Path devices = directory.resolve("devices");
        counter(devices, "eth0", "not-a-number");

        assertNull(NetworkInterfaceProbe.discover("../eth0",
                directory.resolve("route"), directory.resolve("ipv6_route"), devices));
        NetworkInterfaceProbe selected = NetworkInterfaceProbe.discover(" eth0 ",
                directory.resolve("route"), directory.resolve("ipv6_route"), devices);
        assertNotNull(selected);
        assertEquals(-1L, selected.readTransmitBytes());
    }

    private static void counter(Path devices, String interfaceName, String value) throws Exception {
        Path statistics = devices.resolve(interfaceName).resolve("statistics");
        Files.createDirectories(statistics);
        Files.writeString(statistics.resolve("tx_bytes"), value);
    }
}
