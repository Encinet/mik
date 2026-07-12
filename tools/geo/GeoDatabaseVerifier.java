import java.io.BufferedReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.encinet.mik.util.GeoUtil;

/** Exhaustively verifies generated range endpoints against the source CSV files. */
public final class GeoDatabaseVerifier {

    private GeoDatabaseVerifier() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: GeoDatabaseVerifier <ipv4-num.csv> <ipv6.csv>");
        }

        long checked = verifyIpv4(Path.of(args[0])) + verifyIpv6(Path.of(args[1]));
        InetAddress[] benchmarkAddresses = {
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("1.0.2.0"),
            InetAddress.getByName("2001:4860:4860::8888"),
            InetAddress.getByName("2404:6800:4004:80a::200e")
        };

        long checksum = 0;
        long started = System.nanoTime();
        for (int i = 0; i < 5_000_000; i++) {
            checksum += GeoUtil.countryCode(benchmarkAddresses[i & 3]).charAt(0);
        }
        long elapsed = System.nanoTime() - started;
        System.out.printf(
                "Verified %,d endpoints; 5,000,000 lookups in %.2f ms (%.1f ns/op), checksum=%d%n",
                checked, elapsed / 1_000_000.0, elapsed / 5_000_000.0, checksum);
    }

    private static long verifyIpv4(Path path) throws Exception {
        long checked = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int first = line.indexOf(',');
                int second = line.indexOf(',', first + 1);
                String expected = line.substring(second + 1);
                assertCountry(ipv4(Long.parseUnsignedLong(line, 0, first, 10)), expected);
                assertCountry(ipv4(Long.parseUnsignedLong(line, first + 1, second, 10)), expected);
                checked += 2;
            }
        }
        return checked;
    }

    private static long verifyIpv6(Path path) throws Exception {
        long checked = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int first = line.indexOf(',');
                int second = line.indexOf(',', first + 1);
                String expected = line.substring(second + 1);
                assertCountry(InetAddress.getByName(line.substring(0, first)), expected);
                assertCountry(InetAddress.getByName(line.substring(first + 1, second)), expected);
                checked += 2;
            }
        }
        return checked;
    }

    private static InetAddress ipv4(long value) throws Exception {
        return InetAddress.getByAddress(new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        });
    }

    private static void assertCountry(InetAddress address, String expected) {
        String actual = GeoUtil.countryCode(address);
        if (!expected.equals(actual)) {
            throw new AssertionError(address.getHostAddress() + ": expected " + expected + ", got " + actual);
        }
    }
}
