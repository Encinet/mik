package org.encinet.mik.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;

/**
 * IP geolocation utility using hardcoded Chinese IP CIDR ranges.
 * Supports IPv4 (binary search on long[]) and IPv6 (binary search on long[2][] hi/lo pairs).
 * No heap allocation on the hot path.
 */
public final class GeoUtil {

    private GeoUtil() {}

    // IPv4: sorted array of [start, end] as unsigned longs
    private static final long[][] IPV4_RANGES;

    // IPv6: sorted array of [startHi, startLo, endHi, endLo]
    private static final long[][] IPV6_RANGES;

    static {
        IPV4_RANGES = buildIpv4(new String[]{
            "1.0.1.0/24", "1.0.2.0/23", "1.0.8.0/21", "1.0.32.0/19",
            "1.1.0.0/24", "1.2.0.0/23", "1.4.1.0/24", "1.8.0.0/13",
            "1.24.0.0/13", "1.48.0.0/12", "1.56.0.0/13", "1.68.0.0/14",
            "1.80.0.0/12", "1.116.0.0/14", "1.176.0.0/12", "1.192.0.0/11",
            "14.0.0.0/10", "14.96.0.0/11", "14.128.0.0/10", "14.192.0.0/11",
            "27.0.0.0/10", "27.96.0.0/11", "27.128.0.0/10", "27.192.0.0/11",
            "36.0.0.0/10", "36.96.0.0/11", "36.128.0.0/10", "36.192.0.0/11",
            "39.64.0.0/10", "39.128.0.0/10",
            "42.0.0.0/8",
            "43.224.0.0/11", "43.240.0.0/12", "43.248.0.0/13",
            "45.112.0.0/14", "45.116.0.0/14",
            "47.52.0.0/14", "47.88.0.0/14", "47.92.0.0/14", "47.96.0.0/11",
            "49.0.0.0/10", "49.64.0.0/10", "49.128.0.0/10", "49.192.0.0/10",
            "52.80.0.0/14", "54.222.0.0/15",
            "58.0.0.0/8",
            "59.32.0.0/11", "59.64.0.0/10", "59.128.0.0/10", "59.192.0.0/10",
            "60.0.0.0/10", "60.64.0.0/10", "60.128.0.0/10", "60.192.0.0/10",
            "61.0.0.0/10", "61.64.0.0/10", "61.128.0.0/10", "61.192.0.0/10",
            "101.0.0.0/10", "101.64.0.0/10", "101.128.0.0/10", "101.192.0.0/10",
            "103.0.0.0/10", "103.64.0.0/10",
            "106.0.0.0/8",
            "110.0.0.0/8", "111.0.0.0/8", "112.0.0.0/8", "113.0.0.0/8",
            "114.0.0.0/8", "115.0.0.0/8", "116.0.0.0/8", "117.0.0.0/8",
            "118.0.0.0/8", "119.0.0.0/8", "120.0.0.0/8", "121.0.0.0/8",
            "122.0.0.0/8", "123.0.0.0/8", "124.0.0.0/8", "125.0.0.0/8",
            "163.177.0.0/16", "163.179.0.0/16",
            "171.0.0.0/8", "175.0.0.0/8",
            "180.0.0.0/8", "182.0.0.0/8", "183.0.0.0/8",
            "202.96.0.0/11", "202.102.0.0/15", "202.112.0.0/12",
            "202.128.0.0/11", "202.192.0.0/11",
            "203.0.0.0/11", "203.64.0.0/10",
            "210.0.0.0/10", "210.64.0.0/10", "210.128.0.0/10", "210.192.0.0/10",
            "211.0.0.0/10", "211.64.0.0/10", "211.128.0.0/10", "211.192.0.0/10",
            "218.0.0.0/8", "219.0.0.0/8", "220.0.0.0/8",
            "221.0.0.0/8", "222.0.0.0/8", "223.0.0.0/8"
        });

        IPV6_RANGES = buildIpv6(new String[]{
            "2001:da8::/32",              // CERNET2
            "2001:250::/32", "2001:251::/32", // CSTNET
            "2001:cc0::/32",              // CERNET
            "240e::/20",                  // China Telecom
            "2408::/12",                  // China Unicom
            "2409::/12",                  // China Mobile
            "240c::/12",                  // CERNET/misc
            "2400:3200::/32", "2400:3a00::/32",
            "2402:4e00::/32",
            "2404:6800::/32",
            "2407:c080::/32"
        });
    }

    // Build helpers

    private static long[][] buildIpv4(String[] cidrs) {
        long[][] r = new long[cidrs.length][2];
        for (int i = 0; i < cidrs.length; i++) {
            int slash = cidrs[i].indexOf('/');
            long ip = ipv4ToLong(cidrs[i], slash);
            int prefix = Integer.parseInt(cidrs[i].substring(slash + 1));
            long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            r[i][0] = ip & mask;
            r[i][1] = r[i][0] + (~mask & 0xFFFFFFFFL);
        }
        Arrays.sort(r, (a, b) -> Long.compareUnsigned(a[0], b[0]));
        return r;
    }

    private static long[][] buildIpv6(String[] cidrs) {
        long[][] r = new long[cidrs.length][4]; // [startHi, startLo, endHi, endLo]
        for (int i = 0; i < cidrs.length; i++) {
            int slash = cidrs[i].indexOf('/');
            byte[] bytes = parseIpv6Bytes(cidrs[i].substring(0, slash));
            int prefix = Integer.parseInt(cidrs[i].substring(slash + 1));
            long hi = toLong(bytes, 0);
            long lo = toLong(bytes, 8);
            // apply mask
            if (prefix <= 64) {
                long maskHi = prefix == 0 ? 0L : (0xFFFFFFFFFFFFFFFFL << (64 - prefix));
                hi = hi & maskHi;
                lo = 0L;
                r[i][0] = hi; r[i][1] = lo;
                r[i][2] = hi | ~maskHi; r[i][3] = 0xFFFFFFFFFFFFFFFFL;
            } else {
                long maskLo = 0xFFFFFFFFFFFFFFFFL << (128 - prefix);
                lo = lo & maskLo;
                r[i][0] = hi; r[i][1] = lo;
                r[i][2] = hi; r[i][3] = lo | ~maskLo;
            }
        }
        Arrays.sort(r, (a, b) -> {
            int c = Long.compareUnsigned(a[0], b[0]);
            return c != 0 ? c : Long.compareUnsigned(a[1], b[1]);
        });
        return r;
    }

    // Parse helpers (no allocation on hot path)

    private static long ipv4ToLong(String cidr, int slash) {
        long result = 0;
        int shift = 24;
        int num = 0;
        for (int i = 0; i < slash; i++) {
            char c = cidr.charAt(i);
            if (c == '.') {
                result |= ((long) num) << shift;
                shift -= 8;
                num = 0;
            } else {
                num = num * 10 + (c - '0');
            }
        }
        result |= ((long) num) << shift;
        return result;
    }

    private static byte[] parseIpv6Bytes(String ip) {
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (Exception e) {
            return new byte[16];
        }
    }

    private static long toLong(byte[] b, int offset) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (b[offset + i] & 0xFFL);
        return v;
    }

    // Public API

    public static boolean isChinaIp(InetAddress address) {
        if (address instanceof Inet4Address) {
            byte[] b = address.getAddress();
            long ip = ((b[0] & 0xFFL) << 24) | ((b[1] & 0xFFL) << 16)
                    | ((b[2] & 0xFFL) << 8) | (b[3] & 0xFFL);
            return searchIpv4(ip);
        } else if (address instanceof Inet6Address) {
            byte[] b = address.getAddress();
            return searchIpv6(toLong(b, 0), toLong(b, 8));
        }
        return false;
    }

    // Binary search

    private static boolean searchIpv4(long ip) {
        int lo = 0, hi = IPV4_RANGES.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (Long.compareUnsigned(ip, IPV4_RANGES[mid][0]) < 0) hi = mid - 1;
            else if (Long.compareUnsigned(ip, IPV4_RANGES[mid][1]) > 0) lo = mid + 1;
            else return true;
        }
        return false;
    }

    private static boolean searchIpv6(long ipHi, long ipLo) {
        int lo = 0, hi = IPV6_RANGES.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long[] r = IPV6_RANGES[mid];
            int cmpLo = Long.compareUnsigned(ipHi, r[0]);
            if (cmpLo < 0 || (cmpLo == 0 && Long.compareUnsigned(ipLo, r[1]) < 0)) {
                hi = mid - 1;
            } else {
                int cmpHi = Long.compareUnsigned(ipHi, r[2]);
                if (cmpHi > 0 || (cmpHi == 0 && Long.compareUnsigned(ipLo, r[3]) > 0)) {
                    lo = mid + 1;
                } else {
                    return true;
                }
            }
        }
        return false;
    }
}