package org.encinet.mik.module.ai.tool.web;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

/** Allows only HTTP(S) destinations whose complete DNS answer is globally routable. */
final class PublicWebUriPolicy implements WebUriPolicy {
    private static final int MAXIMUM_URL_CHARACTERS = 2_048;

    private final AddressResolver resolver;

    PublicWebUriPolicy() {
        this(InetAddress::getAllByName);
    }

    PublicWebUriPolicy(AddressResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public URI requirePublic(URI uri) {
        URI canonical;
        try {
            canonical = HttpUriCanonicalizer.canonicalize(uri, MAXIMUM_URL_CHARACTERS);
        } catch (RuntimeException error) {
            throw new WebFetchException("invalid_url",
                    Objects.requireNonNullElse(error.getMessage(), "URL is invalid"), error);
        }
        String host = canonical.getHost();
        String lookupHost = stripIpv6Brackets(host);
        rejectReservedHostname(lookupHost);
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(lookupHost);
        } catch (UnknownHostException error) {
            throw new WebFetchException(
                    "dns_failed", "The web host could not be resolved", error);
        }
        if (addresses == null || addresses.length == 0) {
            throw new WebFetchException(
                    "dns_failed", "The web host did not resolve to an address");
        }
        for (InetAddress address : addresses) {
            if (address == null || !isGloballyRoutable(address.getAddress())) {
                throw new WebFetchException("blocked_address",
                        "Private, local, reserved, and non-public addresses cannot be fetched");
            }
        }
        return canonical;
    }

    private static void rejectReservedHostname(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String suffix : new String[]{
                "localhost", ".localhost", ".local", ".internal", ".localdomain",
                ".home", ".lan", ".onion", ".invalid"}) {
            if (normalized.equals(suffix) || normalized.endsWith(suffix)) {
                throw new WebFetchException(
                        "blocked_address", "Local and private host names cannot be fetched");
            }
        }
    }

    static boolean isGloballyRoutable(byte[] address) {
        if (address == null) {
            return false;
        }
        if (address.length == 4) {
            return isPublicIpv4(address);
        }
        if (address.length != 16) {
            return false;
        }
        if (isIpv4Mapped(address)) {
            return isPublicIpv4(new byte[]{
                    address[12], address[13], address[14], address[15]});
        }
        int first = unsigned(address[0]);
        int second = unsigned(address[1]);
        boolean globalUnicast = (first & 0xE0) == 0x20;
        if (!globalUnicast) {
            return false;
        }
        // Documentation, Teredo, and 6to4 ranges are not valid direct web destinations here.
        boolean documentation = first == 0x20 && second == 0x01
                && unsigned(address[2]) == 0x0D && unsigned(address[3]) == 0xB8;
        boolean teredo = first == 0x20 && second == 0x01
                && address[2] == 0 && address[3] == 0;
        boolean sixToFour = first == 0x20 && second == 0x02;
        return !(documentation || teredo || sixToFour);
    }

    private static boolean isPublicIpv4(byte[] address) {
        int first = unsigned(address[0]);
        int second = unsigned(address[1]);
        int third = unsigned(address[2]);
        if (first == 0 || first == 10 || first == 127 || first >= 224) {
            return false;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return false;
        }
        if (first == 169 && second == 254) {
            return false;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return false;
        }
        if (first == 192 && (second == 168
                || second == 0 && (third == 0 || third == 2)
                || second == 88 && third == 99)) {
            return false;
        }
        if (first == 198 && (second == 18 || second == 19
                || second == 51 && third == 100)) {
            return false;
        }
        return !(first == 203 && second == 0 && third == 113);
    }

    private static boolean isIpv4Mapped(byte[] address) {
        for (int index = 0; index < 10; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        return address[10] == (byte) 0xFF && address[11] == (byte) 0xFF;
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static String stripIpv6Brackets(String host) {
        return host != null && host.length() > 1
                && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']'
                ? host.substring(1, host.length() - 1) : host;
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
