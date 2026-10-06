package org.encinet.mik.module.performance;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads Linux route tables and the selected interface's outbound byte counter. */
record NetworkInterfaceProbe(String interfaceName, Path transmitBytesPath) {
    private static final Path IPV4_ROUTES = Path.of("/proc/net/route");
    private static final Path IPV6_ROUTES = Path.of("/proc/net/ipv6_route");
    private static final Path NETWORK_DEVICES = Path.of("/sys/class/net");

    static NetworkInterfaceProbe discover(String configuredInterface) {
        return discover(configuredInterface, IPV4_ROUTES, IPV6_ROUTES, NETWORK_DEVICES);
    }

    static NetworkInterfaceProbe discover(String configuredInterface, Path ipv4Routes,
                                          Path ipv6Routes, Path networkDevices) {
        String requested = configuredInterface == null ? "" : configuredInterface.trim();
        if (!requested.isEmpty() && !"auto".equalsIgnoreCase(requested)) {
            return forInterface(networkDevices, requested);
        }

        RouteCandidate ipv4 = discoverIpv4DefaultRoute(ipv4Routes, networkDevices);
        NetworkInterfaceProbe selected = ipv4 == null ? null
                : forInterface(networkDevices, ipv4.interfaceName());
        if (selected != null) return selected;

        RouteCandidate ipv6 = discoverIpv6DefaultRoute(ipv6Routes, networkDevices);
        return ipv6 == null ? null : forInterface(networkDevices, ipv6.interfaceName());
    }

    private static RouteCandidate discoverIpv4DefaultRoute(Path routes, Path networkDevices) {
        if (!Files.isReadable(routes)) return null;
        RouteCandidate best = null;
        try (BufferedReader reader = Files.newBufferedReader(routes)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 8 || "Iface".equals(fields[0])
                        || !"00000000".equals(fields[1])) continue;
                try {
                    long flags = Long.parseUnsignedLong(fields[3], 16);
                    long metric = Long.parseLong(fields[6]);
                    if ((flags & 0x1L) == 0L
                            || forInterface(networkDevices, fields[0]) == null) continue;
                    RouteCandidate candidate = new RouteCandidate(fields[0], metric);
                    if (best == null || candidate.metric() < best.metric()) best = candidate;
                } catch (NumberFormatException ignored) {
                    // Ignore only the malformed route row.
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return best;
    }

    private static RouteCandidate discoverIpv6DefaultRoute(Path routes, Path networkDevices) {
        if (!Files.isReadable(routes)) return null;
        RouteCandidate best = null;
        try (BufferedReader reader = Files.newBufferedReader(routes)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 10 || !isIpv6DefaultRoute(fields)) continue;
                try {
                    long metric = Long.parseUnsignedLong(fields[5], 16);
                    long flags = Long.parseUnsignedLong(fields[8], 16);
                    String interfaceName = fields[9];
                    if ((flags & 0x1L) == 0L
                            || forInterface(networkDevices, interfaceName) == null) continue;
                    RouteCandidate candidate = new RouteCandidate(interfaceName, metric);
                    if (best == null || candidate.metric() < best.metric()) best = candidate;
                } catch (NumberFormatException ignored) {
                    // Ignore only the malformed route row.
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return best;
    }

    private static boolean isIpv6DefaultRoute(String[] fields) {
        return "00000000000000000000000000000000".equals(fields[0])
                && "00".equals(fields[1]);
    }

    private static NetworkInterfaceProbe forInterface(Path networkDevices, String interfaceName) {
        if (!validInterfaceName(interfaceName)) return null;
        Path counter = networkDevices.resolve(interfaceName).resolve("statistics/tx_bytes");
        return Files.isReadable(counter) ? new NetworkInterfaceProbe(interfaceName, counter) : null;
    }

    private static boolean validInterfaceName(String interfaceName) {
        return !interfaceName.isBlank()
                && !"lo".equals(interfaceName)
                && !interfaceName.contains("/")
                && !interfaceName.contains("\\")
                && !interfaceName.contains("..");
    }

    long readTransmitBytes() {
        try {
            return Long.parseLong(Files.readString(transmitBytesPath).trim());
        } catch (IOException | NumberFormatException ignored) {
            return -1L;
        }
    }

    private record RouteCandidate(String interfaceName, long metric) { }
}
