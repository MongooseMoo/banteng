package world.mongoose.banteng.builtin.family;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA224Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.KeyParameter;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinPermissionRule;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.host.Crypt;
import world.mongoose.banteng.host.NativeCallException;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldTxn;

/** Owns builtin registrations and implementations for this family. */
public final class CryptoBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);

    private CryptoBuiltins() {
    }

    public static void register(List<BuiltinSpec> entries, SecureRandom secureRandom, Crypt crypt) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("crypt", List.of(new CallShape(List.of(STRING), List.of(STRING), Optional.empty()))).irrevocable().implementation(call -> crypt(call.arguments(), call.world(), call.programmer(), secureRandom, crypt));
        registrar.builtin("salt", List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()))).implementation(call -> salt(call.arguments()));
        for (String input : List.of("string", "binary", "value")) {
            registrar.builtin(input + "_hash", List.of(new CallShape(List.of(input.equals("value") ? ANY : STRING),
                    List.of(STRING, ANY), Optional.empty()))).implementation(call -> hash(input, call.arguments()));
            registrar.builtin(input + "_hmac", List.of(new CallShape(List.of(input.equals("value") ? ANY : STRING, STRING),
                    List.of(STRING, ANY), Optional.empty()))).implementation(call -> hmac(input, call.arguments()));
        }
        registrar.builtin("argon2", List.of(
                new CallShape(
                        List.of(STRING, STRING),
                        List.of(INTEGER, INTEGER, INTEGER),
                        Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> argon2(call.arguments()));
        registrar.builtin("argon2_verify", List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call ->
                argon2Verify(
                        call.arguments(), call.hosts().serverLog(), CryptoBuiltins::argon2Hash));
    }

    private static BuiltinResult crypt(List<MooValue> arguments, WorldTxn world, long programmer, SecureRandom secureRandom, Crypt crypt) {
        byte[] password = ((StringValue) arguments.getFirst()).bytes();
        String salt;
        if (arguments.size() == 1 || ((StringValue) arguments.get(1)).bytes().length < 2) {
            String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789./";
            salt =
                    new String(
                            new char[]{
                                    alphabet.charAt(secureRandom.nextInt(alphabet.length())),
                                    alphabet.charAt(secureRandom.nextInt(alphabet.length()))
                            });
        } else {
            salt = ((StringValue) arguments.get(1)).text();
        }
        OptionalLong strength = cryptStrength(salt);
        if (strength.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        long selectedStrength = strength.orElseThrow();
        if (!world.isWizard(programmer)
                && (isRecognizedBcrypt(salt) ? selectedStrength != 5 : selectedStrength != 0)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        String result = nativeCrypt(password, StringValue.of(salt).bytes(), crypt);
        if (isRecognizedBcrypt(salt) && (result.equals("*0") || result.equals("*1"))) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        return BuiltinResult.value(StringValue.of(result));
    }

    private static BuiltinResult salt(List<MooValue> arguments) {
        String prefix = ((StringValue) arguments.getFirst()).text();
        String alphabet = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        boolean md5 = prefix.startsWith("$1$");
        boolean sha = prefix.startsWith("$5$") || prefix.startsWith("$6$");
        boolean bcrypt = isRecognizedBcrypt(prefix);
        OptionalLong strength = cryptStrength(prefix);
        if (strength.isEmpty() || (!md5 && !sha && !bcrypt && !prefix.isEmpty()
                && (prefix.length() < 2 || alphabet.indexOf(prefix.charAt(0)) < 0
                || alphabet.indexOf(prefix.charAt(1)) < 0))) {
            return BuiltinResult.raised(ErrorValue.E_INVARG, StringValue.of("Invalid prefix"),
                    arguments.getFirst());
        }
        Optional<byte[]> decoded = ((StringValue) arguments.get(1)).binaryBytes();
        if (decoded.isEmpty()) {
            return BuiltinResult.raised(ErrorValue.E_INVARG, StringValue.of("Invalid binary input"),
                    arguments.get(1));
        }
        byte[] bytes = decoded.orElseThrow();
        if (bytes.length < (bcrypt ? 16 : md5 || sha ? 3 : 2) || prefix.startsWith("$2x$")) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        if (bcrypt) {
            long cost = strength.orElseThrow() == 0 ? 5 : strength.orElseThrow();
            String encoded = Base64.getEncoder().withoutPadding().encodeToString(Arrays.copyOf(bytes, 16));
            String base64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
            String bcryptAlphabet = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            StringBuilder output = new StringBuilder(prefix.substring(0, 4));
            output.append(cost < 10 ? "0" : "").append(cost).append('$');
            for (int index = 0; index < encoded.length(); index++) {
                output.append(bcryptAlphabet.charAt(base64.indexOf(encoded.charAt(index))));
            }
            return BuiltinResult.value(StringValue.of(output.toString()));
        }
        if (!md5 && !sha) {
            return BuiltinResult.value(StringValue.of("" + alphabet.charAt(bytes[0] & 63)
                    + alphabet.charAt(bytes[1] & 63)));
        }
        StringBuilder output = new StringBuilder(prefix.substring(0, 3));
        if (sha && strength.orElseThrow() != 0) {
            output.append("rounds=").append(strength.orElseThrow()).append('$');
        }
        int length = Math.min(bytes.length / 3 * 3, md5 ? 6 : 12);
        for (int index = 0; index < length; index += 3) {
            int word = Byte.toUnsignedInt(bytes[index])
                    | (Byte.toUnsignedInt(bytes[index + 1]) << 8)
                    | (Byte.toUnsignedInt(bytes[index + 2]) << 16);
            for (int shift = 0; shift < 24; shift += 6) {
                output.append(alphabet.charAt((word >>> shift) & 63));
            }
        }
        return BuiltinResult.value(StringValue.of(output.toString()));
    }

    private static OptionalLong cryptStrength(String salt) {
        if (isRecognizedBcrypt(salt)) {
            int separator = salt.indexOf('$', 4);
            if (separator < 0) {
                return OptionalLong.of(0);
            }
            if (salt.length() < 7) {
                return OptionalLong.empty();
            }
            String cost = salt.substring(4, separator);
            try {
                long parsed = Long.parseLong(cost);
                return parsed >= 4 && parsed <= 31 ? OptionalLong.of(parsed) : OptionalLong.empty();
            } catch (NumberFormatException error) {
                return OptionalLong.empty();
            }
        }
        if (salt.startsWith("$5$rounds=") || salt.startsWith("$6$rounds=")) {
            int separator = salt.indexOf('$', 10);
            if (separator < 0) {
                return OptionalLong.empty();
            }
            try {
                long rounds = Long.parseLong(salt.substring(10, separator));
                return rounds >= 1000 && rounds <= 999_999_999
                        ? OptionalLong.of(rounds)
                        : OptionalLong.empty();
            } catch (NumberFormatException error) {
                return OptionalLong.empty();
            }
        }
        if ((salt.startsWith("$5$") || salt.startsWith("$6$"))
                && salt.indexOf('$', 3) >= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(0);
    }

    private static boolean isRecognizedBcrypt(String salt) {
        return salt.startsWith("$2a$") || salt.startsWith("$2x$") || salt.startsWith("$2y$");
    }

    private static String nativeCrypt(byte[] password, byte[] salt, Crypt crypt) {
        try {
            return crypt.hash(password, salt);
        } catch (NativeCallException failure) {
            return "*0";
        }
    }

    private static BuiltinResult argon2(List<MooValue> arguments) {
        byte[] password = cStringBytes((StringValue) arguments.get(0));
        byte[] salt = cStringBytes((StringValue) arguments.get(1));
        final Argon2Settings settings;
        try {
            settings = argon2Settings(arguments);
        } catch (IllegalArgumentException | ArithmeticException error) {
            return BuiltinResult.error(ErrorValue.E_INVIND);
        }
        final byte[] hash;
        try {
            hash =
                    argon2Hash(
                            password,
                            salt,
                            settings.iterations(),
                            settings.memoryKiB(),
                            settings.parallelism(),
                            32);
        } catch (IllegalArgumentException error) {
            return BuiltinResult.error(ErrorValue.E_INVIND);
        }
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        String encoded =
                "$argon2id$v=19$m="
                        + settings.memoryKiB()
                        + ",t="
                        + settings.iterations()
                        + ",p="
                        + settings.parallelism()
                        + "$"
                        + encoder.encodeToString(salt)
                        + "$"
                        + encoder.encodeToString(hash);
        return BuiltinResult.value(StringValue.of(encoded));
    }

    private static Argon2Settings argon2Settings(List<MooValue> arguments) {
        return new Argon2Settings(
                optionalPositiveInt(arguments, 2, 3),
                optionalPositiveInt(arguments, 3, 4096),
                optionalPositiveInt(arguments, 4, 1));
    }

    static BuiltinResult argon2Verify(
            List<MooValue> arguments, ServerLog serverLog, Argon2Hasher hasher) {
        String encoded = ((StringValue) arguments.get(0)).text();
        byte[] password = cStringBytes((StringValue) arguments.get(1));
        String[] fields = encoded.split("\\$", -1);
        if (fields.length != 6
                || !fields[0].isEmpty()
                || !fields[1].equals("argon2id")
                || !fields[2].equals("v=19")) {
            return BuiltinResult.value(new IntegerValue(0));
        }
        int memoryKiB = 0;
        int iterations = 0;
        int parallelism = 0;
        for (String parameter : fields[3].split(",", -1)) {
            String[] pair = parameter.split("=", 2);
            if (pair.length != 2) {
                return BuiltinResult.value(new IntegerValue(0));
            }
            final int value;
            try {
                value = Integer.parseInt(pair[1]);
            } catch (NumberFormatException malformed) {
                return BuiltinResult.value(new IntegerValue(0));
            }
            switch (pair[0]) {
                case "m" -> memoryKiB = value;
                case "t" -> iterations = value;
                case "p" -> parallelism = value;
                default -> {
                    return BuiltinResult.value(new IntegerValue(0));
                }
            }
        }
        if (memoryKiB <= 0 || iterations <= 0 || parallelism <= 0) {
            return BuiltinResult.value(new IntegerValue(0));
        }
        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(fields[4]);
        } catch (IllegalArgumentException malformed) {
            return BuiltinResult.value(new IntegerValue(0));
        }
        byte[] expected;
        try {
            expected = Base64.getDecoder().decode(fields[5]);
        } catch (IllegalArgumentException malformed) {
            return BuiltinResult.value(new IntegerValue(0));
        }
        if (salt.length == 0 || expected.length == 0) {
            return BuiltinResult.value(new IntegerValue(0));
        }
        final byte[] actual;
        try {
            actual =
                    hasher.hash(password, salt, iterations, memoryKiB, parallelism, expected.length);
        } catch (RuntimeException failure) {
            serverLog.error(
                    "ARGON2 VERIFY: internal failure: " + failure.getClass().getSimpleName());
            return BuiltinResult.error(ErrorValue.E_QUOTA);
        }
        return BuiltinResult.value(new IntegerValue(MessageDigest.isEqual(expected, actual) ? 1 : 0));
    }

    private static byte[] argon2Hash(
            byte[] password,
            byte[] salt,
            int iterations,
            int memoryKiB,
            int parallelism,
            int outputLength) {
        Argon2Parameters parameters =
                new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                        .withIterations(iterations)
                        .withMemoryAsKB(memoryKiB)
                        .withParallelism(parallelism)
                        .withSalt(salt)
                        .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] output = new byte[outputLength];
        generator.generateBytes(password, output);
        return output;
    }

    private static int optionalPositiveInt(
            List<MooValue> arguments, int index, int defaultValue) {
        if (arguments.size() <= index) {
            return defaultValue;
        }
        long value = ((IntegerValue) arguments.get(index)).value();
        if (value <= 0) {
            throw new IllegalArgumentException("argon2 parameter must be positive");
        }
        return Math.toIntExact(value);
    }

    private static byte[] cStringBytes(StringValue value) {
        byte[] bytes = value.bytes();
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return Arrays.copyOf(bytes, length);
    }

    private static Optional<byte[]> hashInput(String input, MooValue value) {
        return switch (input) {
            case "binary" -> ((StringValue) value).binaryBytes();
            case "value" -> Optional.of(StringValue.of(value.toLiteral()).bytes());
            default -> Optional.of(((StringValue) value).bytes());
        };
    }

    private static BuiltinResult hash(String input, List<MooValue> arguments) {
        String algorithm = arguments.size() > 1
                ? ((StringValue) arguments.get(1)).text().toLowerCase(Locale.ROOT) : "sha256";
        Digest digest = switch (algorithm) {
            case "md5" -> new MD5Digest();
            case "sha1" -> new SHA1Digest();
            case "sha224" -> new SHA224Digest();
            case "sha256" -> new SHA256Digest();
            case "sha384" -> new SHA384Digest();
            case "sha512" -> new SHA512Digest();
            case "ripemd160" -> new RIPEMD160Digest();
            default -> null;
        };
        Optional<byte[]> message = hashInput(input, arguments.getFirst());
        if (digest == null || message.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        byte[] bytes = message.orElseThrow();
        digest.update(bytes, 0, bytes.length);
        byte[] result = new byte[digest.getDigestSize()];
        digest.doFinal(result, 0);
        return digestResult(result, arguments.size() > 2 && arguments.get(2).isTruthy());
    }

    private static BuiltinResult hmac(String input, List<MooValue> arguments) {
        String algorithm = arguments.size() > 2
                ? ((StringValue) arguments.get(2)).text().toLowerCase(Locale.ROOT) : "sha256";
        HMac mac = switch (algorithm) {
            case "sha1" -> new HMac(new SHA1Digest());
            case "sha256" -> new HMac(new SHA256Digest());
            default -> null;
        };
        Optional<byte[]> key = ((StringValue) arguments.get(1)).binaryBytes();
        Optional<byte[]> message = hashInput(input, arguments.getFirst());
        if (mac == null || key.isEmpty() || message.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        mac.init(new KeyParameter(key.orElseThrow()));
        byte[] bytes = message.orElseThrow();
        mac.update(bytes, 0, bytes.length);
        byte[] digest = new byte[mac.getMacSize()];
        mac.doFinal(digest, 0);
        return digestResult(digest, arguments.size() > 3 && arguments.get(3).isTruthy());
    }

    private static BuiltinResult digestResult(byte[] digest, boolean binary) {
        StringBuilder result = new StringBuilder(digest.length * (binary ? 3 : 2));
        for (byte current : digest) {
            int value = Byte.toUnsignedInt(current);
            if (binary) {
                result.append('~');
            }
            result.append("0123456789ABCDEF".charAt(value >>> 4));
            result.append("0123456789ABCDEF".charAt(value & 15));
        }
        return BuiltinResult.value(StringValue.of(result.toString()));
    }

    private record Argon2Settings(int iterations, int memoryKiB, int parallelism) {
    }

    @FunctionalInterface
    interface Argon2Hasher {
        byte[] hash(
                byte[] password,
                byte[] salt,
                int iterations,
                int memoryKiB,
                int parallelism,
                int outputLength);
    }
}
