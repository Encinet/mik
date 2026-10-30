package org.encinet.mik.feature.player.security;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;

/** One bounded Argon2id configuration, encoded in the standard PHC format. */
final class PasswordHasher {
    private static final String PREFIX = "$argon2id$v=19$m=19456,t=2,p=1$";
    private static final Pattern ENCODING = Pattern.compile("\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}");
    private static final Base64.Encoder BASE64 = Base64.getEncoder().withoutPadding();
    private final SecureRandom random = new SecureRandom();

    String hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] result = derive(password, salt);
        try { return PREFIX + BASE64.encodeToString(salt) + "$" + BASE64.encodeToString(result); }
        finally { Arrays.fill(result, (byte) 0); }
    }

    static void validateEncoding(String hash) {
        if (!ENCODING.matcher(hash).matches()) throw new IllegalArgumentException("Invalid password hash encoding");
        String[] fields = hash.split("\\$");
        byte[] salt = Base64.getDecoder().decode(fields[4]);
        byte[] digest = Base64.getDecoder().decode(fields[5]);
        if (!BASE64.encodeToString(salt).equals(fields[4]) || !BASE64.encodeToString(digest).equals(fields[5])) {
            throw new IllegalArgumentException("Noncanonical password hash encoding");
        }
    }

    boolean verify(String password, String hash) {
        validateEncoding(hash);
        String[] fields = hash.split("\\$");
        byte[] actual = derive(password, Base64.getDecoder().decode(fields[4]));
        byte[] expected = Base64.getDecoder().decode(fields[5]);
        try { return MessageDigest.isEqual(actual, expected); }
        finally { Arrays.fill(actual, (byte) 0); Arrays.fill(expected, (byte) 0); }
    }

    String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder password = new StringBuilder(24);
        for (int i = 0; i < 24; i++) password.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return password.toString();
    }

    private static byte[] derive(String password, byte[] salt) {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(19456)
                .withIterations(2).withParallelism(1).withSalt(salt).build();
        try {
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            byte[] output = new byte[32];
            generator.generateBytes(bytes, output);
            return output;
        } finally { Arrays.fill(bytes, (byte) 0); parameters.clear(); }
    }
}
