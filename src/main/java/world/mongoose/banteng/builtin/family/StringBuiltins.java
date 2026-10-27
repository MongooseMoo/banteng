package world.mongoose.banteng.builtin.family;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import static world.mongoose.banteng.value.ValueSemantics.foldAscii;
import static world.mongoose.banteng.value.ValueSemantics.matchesAt;

/** Owns builtin registrations and implementations for this family. */
public final class StringBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);

    private StringBuiltins() {
    }

    public static void registerExplode(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("explode", List.of(new CallShape(List.of(STRING), List.of(STRING, INTEGER), Optional.empty()))).implementation(call -> explode(call.arguments()));
    }

    public static void registerSubstitutionAndAnsi(List<BuiltinSpec> entries, Random random) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("strsub", List.of(new CallShape(List.of(STRING, STRING, STRING), List.of(ANY), Optional.empty()))).implementation(call -> stringSubstitute(call.arguments()));
        registrar.builtin("strtr", List.of(new CallShape(List.of(STRING, STRING, STRING), List.of(ANY), Optional.empty()))).implementation(call -> stringTranslate(call.arguments()));
        registrar.builtin("parse_ansi", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty()))).irrevocable().implementation(call -> parseAnsi(call.arguments(), random));
        registrar.builtin("remove_ansi", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty()))).implementation(call -> removeAnsi(call.arguments()));
    }

    public static void registerSearchAndCompare(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("index", List.of(new CallShape(List.of(STRING, STRING), List.of(ANY, INTEGER), Optional.empty()))).implementation(call -> stringIndex(call.arguments(), false));
        registrar.builtin("rindex", List.of(new CallShape(List.of(STRING, STRING), List.of(ANY, INTEGER), Optional.empty()))).implementation(call -> stringIndex(call.arguments(), true));
        registrar.builtin("strcmp", List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()))).implementation(call -> stringCompare(call.arguments()));
    }

    private static BuiltinResult stringSubstitute(List<MooValue> arguments) {
        byte[] source = ((StringValue) arguments.get(0)).bytes();
        byte[] what = ((StringValue) arguments.get(1)).bytes();
        byte[] replacement = ((StringValue) arguments.get(2)).bytes();
        if (what.length == 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        boolean caseMatters = arguments.size() == 4 && arguments.get(3).isTruthy();
        ByteArrayOutputStream substituted = new ByteArrayOutputStream(source.length);
        int position = 0;
        while (position <= source.length - what.length) {
            if (matchesAt(source, position, what, caseMatters)) {
                substituted.writeBytes(replacement);
                position += what.length;
            } else {
                substituted.write(source[position]);
                position++;
            }
        }
        substituted.write(source, position, source.length - position);
        return BuiltinResult.value(StringValue.of(substituted.toByteArray()));
    }

    private static BuiltinResult stringTranslate(List<MooValue> arguments) {
        byte[] source = ((StringValue) arguments.get(0)).bytes();
        byte[] from = ((StringValue) arguments.get(1)).bytes();
        byte[] to = ((StringValue) arguments.get(2)).bytes();
        boolean caseMatters = arguments.size() == 4 && arguments.get(3).isTruthy();
        int[] translations = new int[256];
        Arrays.fill(translations, -2);
        for (int index = 0; index < from.length; index++) {
            int key = Byte.toUnsignedInt(from[index]);
            if (!caseMatters) {
                key = foldAscii(key);
            }
            translations[key] = index < to.length ? Byte.toUnsignedInt(to[index]) : -1;
        }
        ByteArrayOutputStream translated = new ByteArrayOutputStream(source.length);
        for (byte sourceByte : source) {
            int original = Byte.toUnsignedInt(sourceByte);
            int key = caseMatters ? original : foldAscii(original);
            int replacement = translations[key];
            if (replacement == -2) {
                translated.write(original);
            } else if (replacement >= 0) {
                if (!caseMatters) {
                    replacement = preserveAsciiCase(original, replacement);
                }
                translated.write(replacement);
            }
        }
        return BuiltinResult.value(StringValue.of(translated.toByteArray()));
    }

    private static int preserveAsciiCase(int source, int replacement) {
        if (source >= 'A' && source <= 'Z' && replacement >= 'a' && replacement <= 'z') {
            return replacement - ('a' - 'A');
        }
        if (source >= 'a' && source <= 'z' && replacement >= 'A' && replacement <= 'Z') {
            return replacement + ('a' - 'A');
        }
        return replacement;
    }

    private static BuiltinResult parseAnsi(List<MooValue> arguments, Random random) {
        byte[] parsed = ((StringValue) arguments.getFirst()).bytes();
        List<AnsiReplacement> replacements =
                List.of(
                        new AnsiReplacement("[red]", "\u001b[31m"),
                        new AnsiReplacement("[green]", "\u001b[32m"),
                        new AnsiReplacement("[yellow]", "\u001b[33m"),
                        new AnsiReplacement("[blue]", "\u001b[34m"),
                        new AnsiReplacement("[purple]", "\u001b[35m"),
                        new AnsiReplacement("[cyan]", "\u001b[36m"),
                        new AnsiReplacement("[normal]", "\u001b[0m"),
                        new AnsiReplacement("[inverse]", "\u001b[7m"),
                        new AnsiReplacement("[underline]", "\u001b[4m"),
                        new AnsiReplacement("[bold]", "\u001b[1m"),
                        new AnsiReplacement("[bright]", "\u001b[1m"),
                        new AnsiReplacement("[unbold]", "\u001b[22m"),
                        new AnsiReplacement("[blink]", "\u001b[5m"),
                        new AnsiReplacement("[unblink]", "\u001b[25m"),
                        new AnsiReplacement("[magenta]", "\u001b[35m"),
                        new AnsiReplacement("[unbright]", "\u001b[22m"),
                        new AnsiReplacement("[white]", "\u001b[37m"),
                        new AnsiReplacement("[gray]", "\u001b[1;30m"),
                        new AnsiReplacement("[grey]", "\u001b[1;30m"),
                        new AnsiReplacement("[beep]", "\u0007"),
                        new AnsiReplacement("[black]", "\u001b[30m"),
                        new AnsiReplacement("[b:black]", "\u001b[40m"),
                        new AnsiReplacement("[b:red]", "\u001b[41m"),
                        new AnsiReplacement("[b:green]", "\u001b[42m"),
                        new AnsiReplacement("[b:yellow]", "\u001b[43m"),
                        new AnsiReplacement("[b:blue]", "\u001b[44m"),
                        new AnsiReplacement("[b:magenta]", "\u001b[45m"),
                        new AnsiReplacement("[b:purple]", "\u001b[45m"),
                        new AnsiReplacement("[b:cyan]", "\u001b[46m"),
                        new AnsiReplacement("[b:white]", "\u001b[47m"));
        for (AnsiReplacement replacement : replacements) {
            parsed =
                    replaceAsciiIgnoringCase(
                            parsed, StringValue.of(replacement.tag()).bytes(), StringValue.of(replacement.code()).bytes());
        }
        byte[][] randomCodes = {
                StringValue.of("\u001b[31m").bytes(),
                StringValue.of("\u001b[32m").bytes(),
                StringValue.of("\u001b[33m").bytes(),
                StringValue.of("\u001b[34m").bytes(),
                StringValue.of("\u001b[35m").bytes(),
                StringValue.of("\u001b[35m").bytes()
        };
        byte[] randomTag = StringValue.of("[random]").bytes();
        ByteArrayOutputStream randomized = new ByteArrayOutputStream(parsed.length);
        int position = 0;
        while (position < parsed.length) {
            if (matchesAt(parsed, position, randomTag, false)) {
                randomized.writeBytes(randomCodes[random.nextInt(6)]);
                position += randomTag.length;
            } else {
                randomized.write(parsed[position++]);
            }
        }
        parsed =
                replaceAsciiIgnoringCase(
                        randomized.toByteArray(), StringValue.of("[null]").bytes(), new byte[0]);
        return BuiltinResult.value(StringValue.of(parsed));
    }

    private static BuiltinResult removeAnsi(List<MooValue> arguments) {
        byte[] stripped = ((StringValue) arguments.getFirst()).bytes();
        List<String> tags =
                List.of(
                        "[red]", "[green]", "[yellow]", "[blue]", "[purple]", "[cyan]",
                        "[normal]", "[inverse]", "[underline]", "[bold]", "[bright]", "[unbold]",
                        "[blink]", "[unblink]", "[magenta]", "[unbright]", "[white]", "[gray]",
                        "[grey]", "[beep]", "[black]", "[b:black]", "[b:red]", "[b:green]",
                        "[b:yellow]", "[b:blue]", "[b:magenta]", "[b:purple]", "[b:cyan]",
                        "[b:white]", "[random]", "[null]");
        for (String tag : tags) {
            stripped = replaceAsciiIgnoringCase(stripped, StringValue.of(tag).bytes(), new byte[0]);
        }
        return BuiltinResult.value(StringValue.of(stripped));
    }

    private static byte[] replaceAsciiIgnoringCase(
            byte[] source, byte[] what, byte[] replacement) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(source.length);
        int position = 0;
        while (what.length > 0 && position <= source.length - what.length) {
            if (matchesAt(source, position, what, false)) {
                output.writeBytes(replacement);
                position += what.length;
            } else {
                output.write(source[position++]);
            }
        }
        output.write(source, position, source.length - position);
        return output.toByteArray();
    }

    private static BuiltinResult explode(List<MooValue> arguments) {
        byte[] source = ((StringValue) arguments.getFirst()).bytes();
        byte[] delimiterArgument =
                arguments.size() >= 2 ? ((StringValue) arguments.get(1)).bytes() : new byte[0];
        byte delimiter = delimiterArgument.length == 0 ? (byte) ' ' : delimiterArgument[0];
        boolean preserveEmpty = arguments.size() == 3 && arguments.get(2).isTruthy();
        List<MooValue> pieces = new ArrayList<>();

        if (preserveEmpty) {
            int start = 0;
            for (int end = 0; end <= source.length; end++) {
                if (end == source.length || source[end] == delimiter) {
                    pieces.add(StringValue.of(Arrays.copyOfRange(source, start, end)));
                    start = end + 1;
                }
            }
        } else {
            int start = 0;
            while (start < source.length) {
                while (start < source.length && source[start] == delimiter) {
                    start++;
                }
                int end = start;
                while (end < source.length && source[end] != delimiter) {
                    end++;
                }
                if (start < end) {
                    pieces.add(StringValue.of(Arrays.copyOfRange(source, start, end)));
                }
                start = end;
            }
        }
        return BuiltinResult.value(new ListValue(pieces));
    }

    private static BuiltinResult stringIndex(List<MooValue> arguments, boolean reverse) {
        byte[] source = ((StringValue) arguments.get(0)).bytes();
        byte[] what = ((StringValue) arguments.get(1)).bytes();
        boolean caseMatters = arguments.size() >= 3 && arguments.get(2).isTruthy();
        long offset = arguments.size() == 4 ? ((IntegerValue) arguments.get(3)).value() : 0;
        if ((!reverse && offset < 0) || (reverse && offset > 0)) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        if (reverse) {
            long prefixLength = source.length + offset;
            if (prefixLength < 0) {
                return BuiltinResult.value(new IntegerValue(0));
            }
            int length = (int) Math.min(prefixLength, source.length);
            for (int position = length - what.length; position >= 0; position--) {
                if (matchesAt(source, position, what, caseMatters)) {
                    return BuiltinResult.value(new IntegerValue(position + 1L));
                }
            }
            return BuiltinResult.value(new IntegerValue(0));
        }

        if (offset > source.length) {
            return BuiltinResult.value(new IntegerValue(0));
        }
        int start = (int) offset;
        for (int position = start; position <= source.length - what.length; position++) {
            if (matchesAt(source, position, what, caseMatters)) {
                return BuiltinResult.value(new IntegerValue(position - start + 1L));
            }
        }
        return BuiltinResult.value(new IntegerValue(0));
    }

    private static BuiltinResult stringCompare(List<MooValue> arguments) {
        byte[] left = ((StringValue) arguments.get(0)).bytes();
        byte[] right = ((StringValue) arguments.get(1)).bytes();
        int commonLength = Math.min(left.length, right.length);
        for (int index = 0; index < commonLength; index++) {
            int comparison =
                    Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
            if (comparison != 0) {
                return BuiltinResult.value(new IntegerValue(comparison));
            }
        }
        return BuiltinResult.value(new IntegerValue(Integer.compare(left.length, right.length)));
    }

    private record AnsiReplacement(String tag, String code) {
    }
}
