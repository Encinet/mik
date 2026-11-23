import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.encinet.mik.shared.util.GeoUtil;

/** Exhaustively verifies generated range endpoints against the source CSV files. */
public final class GeoDatabaseVerifier {

    private GeoDatabaseVerifier() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: GeoDatabaseVerifier <ipv4-num.csv> <ipv6.csv>");
        }

        long checked = verify(Path.of(args[0]), GeoDatabaseVerifier::ipv4)
                + verify(Path.of(args[1]), InetAddress::getByName);
        System.out.printf("Verified %,d endpoints%n", checked);
    }

    private static long verify(Path path, AddressParser parse) throws IOException {
        long checked = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int first = line.indexOf(',');
                int second = line.indexOf(',', first + 1);
                String expected = line.substring(second + 1);
                assertCountry(parse.address(line.substring(0, first)), expected);
                assertCountry(parse.address(line.substring(first + 1, second)), expected);
                checked += 2;
            }
        }
        return checked;
    }

    private static InetAddress ipv4(String text) throws UnknownHostException {
        long value = Long.parseUnsignedLong(text);
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

    @FunctionalInterface
    private interface AddressParser {
        InetAddress address(String text) throws UnknownHostException;
    }
}
