import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** Compiles ip-location-db user-country CSV files into GeoUtil's compact binary format. */
public final class GeoDatabaseCompiler {

    private static final int MAGIC = 0x4D494B47; // MIKG
    private static final int FORMAT_VERSION = 1;
    private static final int PREFIX_COUNT = 1 << 16;
    private static final int IPV6_PAGE_WIDTH = 257;
    private static final int IPV6_PAGE_THRESHOLD = 128;

    private GeoDatabaseCompiler() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: GeoDatabaseCompiler <ipv4-num.csv> <ipv6.csv> <output.dat.gz>");
        }

        List<Ipv4Range> ipv4 = readIpv4(Path.of(args[0]));
        List<Ipv6Range> ipv6 = readIpv6(Path.of(args[1]));
        validateIpv4(ipv4);
        validateIpv6(ipv6);

        int[] ipv4Prefixes = buildIpv4Prefixes(ipv4);
        int[] ipv6Prefixes = buildIpv6Prefixes(ipv6);
        V6Pages ipv6Pages = buildIpv6Pages(ipv6, ipv6Prefixes);
        writeDatabase(Path.of(args[2]), ipv4, ipv6, ipv4Prefixes, ipv6Prefixes, ipv6Pages);

        long rawBytes = 16L
                + Integer.BYTES * (long) (ipv4Prefixes.length + ipv6Prefixes.length)
                + Short.BYTES * (long) PREFIX_COUNT
                + Integer.BYTES * (long) ipv6Pages.offsets().length
                + 10L * ipv4.size()
                + 34L * ipv6.size();
        System.out.printf(
                "Compiled %,d IPv4 and %,d IPv6 ranges (%d dense IPv6 /16 pages, %,d raw bytes)%n",
                ipv4.size(), ipv6.size(), ipv6Pages.count(), rawBytes);
    }

    private static List<Ipv4Range> readIpv4(Path path) throws IOException {
        List<Ipv4Range> ranges = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int first = line.indexOf(',');
                int second = line.indexOf(',', first + 1);
                long start = Long.parseUnsignedLong(line, 0, first, 10);
                long end = Long.parseUnsignedLong(line, first + 1, second, 10);
                ranges.add(new Ipv4Range(start, end, encodeCountry(line, second + 1)));
            }
        }
        ranges.sort(Comparator.comparingLong(Ipv4Range::start));
        return ranges;
    }

    private static List<Ipv6Range> readIpv6(Path path) throws Exception {
        List<Ipv6Range> ranges = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int first = line.indexOf(',');
                int second = line.indexOf(',', first + 1);
                long[] start = parseIpv6(line.substring(0, first));
                long[] end = parseIpv6(line.substring(first + 1, second));
                ranges.add(new Ipv6Range(
                        start[0], start[1], end[0], end[1], encodeCountry(line, second + 1)));
            }
        }
        ranges.sort((left, right) -> compare128(
                left.startHi(), left.startLo(), right.startHi(), right.startLo()));
        return ranges;
    }

    private static long[] parseIpv6(String value) throws Exception {
        byte[] bytes = InetAddress.getByName(value).getAddress();
        if (bytes.length != 16) {
            throw new IOException("Not an IPv6 address: " + value);
        }
        return new long[]{toLong(bytes, 0), toLong(bytes, 8)};
    }

    private static short encodeCountry(String line, int offset) throws IOException {
        if (line.length() != offset + 2) {
            throw new IOException("Invalid country code: " + line);
        }
        char first = line.charAt(offset);
        char second = line.charAt(offset + 1);
        if (first < 'A' || first > 'Z' || second < 'A' || second > 'Z') {
            throw new IOException("Invalid country code: " + line);
        }
        return (short) ((first - 'A') * 26 + second - 'A');
    }

    private static void validateIpv4(List<Ipv4Range> ranges) throws IOException {
        long previousEnd = -1;
        for (Ipv4Range range : ranges) {
            if (range.start() < 0 || range.end() > 0xFFFF_FFFFL || range.start() > range.end()) {
                throw new IOException("Invalid IPv4 range: " + range);
            }
            if (previousEnd >= range.start()) {
                throw new IOException("Overlapping IPv4 ranges around " + range.start());
            }
            previousEnd = range.end();
        }
    }

    private static void validateIpv6(List<Ipv6Range> ranges) throws IOException {
        Ipv6Range previous = null;
        for (Ipv6Range range : ranges) {
            if (compare128(range.startHi(), range.startLo(), range.endHi(), range.endLo()) > 0) {
                throw new IOException("Invalid IPv6 range: " + range);
            }
            if (previous != null
                    && compare128(previous.endHi(), previous.endLo(), range.startHi(), range.startLo()) >= 0) {
                throw new IOException("Overlapping IPv6 ranges around " + range);
            }
            previous = range;
        }
    }

    private static int[] buildIpv4Prefixes(List<Ipv4Range> ranges) {
        int[] offsets = new int[PREFIX_COUNT + 1];
        int cursor = 0;
        for (int prefix = 0; prefix < PREFIX_COUNT; prefix++) {
            long boundary = (long) prefix << 16;
            while (cursor < ranges.size() && ranges.get(cursor).start() < boundary) {
                cursor++;
            }
            offsets[prefix] = cursor;
        }
        offsets[PREFIX_COUNT] = ranges.size();
        return offsets;
    }

    private static int[] buildIpv6Prefixes(List<Ipv6Range> ranges) {
        int[] offsets = new int[PREFIX_COUNT + 1];
        int cursor = 0;
        for (int prefix = 0; prefix < PREFIX_COUNT; prefix++) {
            while (cursor < ranges.size()
                    && (int) (ranges.get(cursor).startHi() >>> 48) < prefix) {
                cursor++;
            }
            offsets[prefix] = cursor;
        }
        offsets[PREFIX_COUNT] = ranges.size();
        return offsets;
    }

    private static V6Pages buildIpv6Pages(List<Ipv6Range> ranges, int[] prefix16Offsets) {
        short[] pageByPrefix = new short[PREFIX_COUNT];
        List<Integer> offsets = new ArrayList<>();
        int pageCount = 0;

        for (int prefix16 = 0; prefix16 < PREFIX_COUNT; prefix16++) {
            int start = prefix16Offsets[prefix16];
            int end = prefix16Offsets[prefix16 + 1];
            if (end - Math.max(0, start - 1) <= IPV6_PAGE_THRESHOLD) {
                continue;
            }
            if (pageCount == 0xFFFF) {
                throw new IllegalStateException("Too many IPv6 prefix pages");
            }
            pageByPrefix[prefix16] = (short) (++pageCount);
            int cursor = start;
            for (int prefix8 = 0; prefix8 < 256; prefix8++) {
                while (cursor < end
                        && (int) ((ranges.get(cursor).startHi() >>> 40) & 0xFF) < prefix8) {
                    cursor++;
                }
                offsets.add(cursor);
            }
            offsets.add(end);
        }

        int[] flatOffsets = new int[offsets.size()];
        for (int i = 0; i < offsets.size(); i++) {
            flatOffsets[i] = offsets.get(i);
        }
        return new V6Pages(pageCount, pageByPrefix, flatOffsets);
    }

    private static void writeDatabase(
            Path output,
            List<Ipv4Range> ipv4,
            List<Ipv6Range> ipv6,
            int[] ipv4Prefixes,
            int[] ipv6Prefixes,
            V6Pages ipv6Pages
    ) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                Files.newOutputStream(output), 1 << 20))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(ipv4.size());
            out.writeInt(ipv6.size());
            writeInts(out, ipv4Prefixes);
            writeInts(out, ipv6Prefixes);
            out.writeInt(ipv6Pages.count());
            for (short page : ipv6Pages.pageByPrefix()) {
                out.writeShort(page);
            }
            writeInts(out, ipv6Pages.offsets());

            for (Ipv4Range range : ipv4) {
                out.writeInt((int) range.start());
                out.writeInt((int) range.end());
                out.writeShort(range.country());
            }
            for (Ipv6Range range : ipv6) {
                out.writeLong(range.startHi());
                out.writeLong(range.startLo());
                out.writeLong(range.endHi());
                out.writeLong(range.endLo());
                out.writeShort(range.country());
            }
        }
    }

    private static void writeInts(DataOutputStream out, int[] values) throws IOException {
        for (int value : values) {
            out.writeInt(value);
        }
    }

    private static long toLong(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFFL);
        }
        return value;
    }

    private static int compare128(long leftHi, long leftLo, long rightHi, long rightLo) {
        int high = Long.compareUnsigned(leftHi, rightHi);
        return high != 0 ? high : Long.compareUnsigned(leftLo, rightLo);
    }

    private record Ipv4Range(long start, long end, short country) {}

    private record Ipv6Range(long startHi, long startLo, long endHi, long endLo, short country) {}

    private record V6Pages(int count, short[] pageByPrefix, int[] offsets) {}
}
