package world.mongoose.banteng.builtin.family;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinHandler;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;

import static world.mongoose.banteng.builtin.ValueLimits.enforceListValueLimit;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import static world.mongoose.banteng.value.ValueSemantics.foldAscii;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.world.WorldTxn;

/** Owns builtin registrations and implementations for this family. */
public final class ListBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);

    private ListBuiltins() {
    }

    public static void registerLength(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("length", List.of(
                new CallShape(
                        List.of(ANY),
                        List.of(),
                        Optional.empty()))).implementation(call -> length(call.arguments()));
    }

    public static void registerListEdits(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("listappend", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.LIST), ANY),
                        List.of(INTEGER),
                        Optional.empty()))).implementation(call -> listInsert(call.arguments(), true, call.world()));
        registrar.builtin("listinsert", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.LIST), ANY),
                        List.of(INTEGER),
                        Optional.empty()))).implementation(call -> listInsert(call.arguments(), false, call.world()));
        registrar.builtin("listdelete", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.LIST), INTEGER), List.of(), Optional.empty()))).implementation(call -> listDelete(call.arguments(), call.world()));
        registrar.builtin("listset", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.LIST), ANY, INTEGER),
                        List.of(),
                        Optional.empty()))).implementation(call -> listSet(call.arguments(), call.world()));
    }

    public static void registerSetsAndSort(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        BuiltinHandler allMembers =
                call ->
                        call.threadMode()
                                ? BuiltinResult.hostWork(() -> allMembers(call.arguments(), call.hosts().valueSemantics()))
                                : allMembers(call.arguments(), call.hosts().valueSemantics());
        BuiltinHandler sort =
                call ->
                        call.threadMode()
                                ? BuiltinResult.hostWork(() -> sortValues(call.arguments()))
                                : sortValues(call.arguments());
        registrar.builtin("setadd", List.of(new CallShape(List.of(Set.of(ArgType.LIST), ANY), List.of(), Optional.empty()))).implementation(call -> setAdd(call.arguments(), call.world(), call.hosts().valueSemantics()));
        registrar.builtin("setremove", List.of(new CallShape(List.of(Set.of(ArgType.LIST), ANY), List.of(), Optional.empty()))).implementation(call -> setRemove(call.arguments(), call.world(), call.hosts().valueSemantics()));
        registrar.builtin("all_members", List.of(
                new CallShape(
                        List.of(ANY, Set.of(ArgType.LIST)), List.of(), Optional.empty()))).implementation(allMembers);
        registrar.builtin("sort", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.LIST)),
                        List.of(
                                Set.of(ArgType.LIST),
                                Set.of(ArgType.INTEGER),
                                Set.of(ArgType.INTEGER)),
                        Optional.empty()))).implementation(sort);
    }

    public static void registerReverse(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("reverse", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> reverse(call.arguments()));
    }

    private static BuiltinResult length(List<MooValue> arguments) {
        MooValue value = arguments.getFirst();
        if (value instanceof StringValue string) {
            return BuiltinResult.value(new IntegerValue(string.length()));
        }
        if (value instanceof ListValue list) {
            return BuiltinResult.value(new IntegerValue(list.size()));
        }
        if (value instanceof MapValue map) {
            return BuiltinResult.value(new IntegerValue(map.size()));
        }
        return BuiltinResult.error(ErrorValue.E_TYPE);
    }

    private static BuiltinResult listInsert(
            List<MooValue> arguments, boolean append, WorldTxn world) {
        ListValue list = (ListValue) arguments.get(0);
        MooValue value = arguments.get(1);
        long requestedPosition;
        if (arguments.size() == 2) {
            requestedPosition = append ? list.size() + 1L : 1L;
        } else {
            long supplied = ((IntegerValue) arguments.get(2)).value();
            requestedPosition = append && supplied != Long.MAX_VALUE ? supplied + 1 : supplied;
        }
        int position;
        if (requestedPosition <= 0) {
            position = 1;
        } else if (requestedPosition > list.size() + 1L) {
            position = list.size() + 1;
        } else {
            position = (int) requestedPosition;
        }
        List<MooValue> inserted = new ArrayList<>(list.elements());
        inserted.add(position - 1, value);
        return enforceListValueLimit(new ListValue(inserted), world);
    }

    private static BuiltinResult listDelete(List<MooValue> arguments, WorldTxn world) {
        ListValue list = (ListValue) arguments.get(0);
        long position = ((IntegerValue) arguments.get(1)).value();
        if (position <= 0 || position > list.size()) {
            return BuiltinResult.error(ErrorValue.E_RANGE);
        }
        List<MooValue> deleted = new ArrayList<>(list.elements());
        deleted.remove((int) position - 1);
        return enforceListValueLimit(new ListValue(deleted), world);
    }

    private static BuiltinResult listSet(List<MooValue> arguments, WorldTxn world) {
        ListValue list = (ListValue) arguments.get(0);
        long position = ((IntegerValue) arguments.get(2)).value();
        if (position <= 0 || position > list.size()) {
            return BuiltinResult.error(ErrorValue.E_RANGE);
        }
        List<MooValue> replaced = new ArrayList<>(list.elements());
        replaced.set((int) position - 1, arguments.get(1));
        return enforceListValueLimit(new ListValue(replaced), world);
    }

    private static BuiltinResult setAdd(
            List<MooValue> arguments, WorldTxn world, ValueSemantics semantics) {
        ListValue list = (ListValue) arguments.get(0);
        MooValue value = arguments.get(1);
        boolean present = list.elements().stream().anyMatch(element -> semantics.equal(element, value, false));
        return enforceListValueLimit(present ? list : list.append(value), world);
    }

    private static BuiltinResult setRemove(
            List<MooValue> arguments, WorldTxn world, ValueSemantics semantics) {
        ListValue list = (ListValue) arguments.get(0);
        MooValue value = arguments.get(1);
        for (int index = 0; index < list.size(); index++) {
            if (semantics.equal(list.elements().get(index), value, false)) {
                List<MooValue> remaining = new ArrayList<>(list.elements());
                remaining.remove(index);
                return enforceListValueLimit(new ListValue(remaining), world);
            }
        }
        return enforceListValueLimit(list, world);
    }

    private static BuiltinResult allMembers(List<MooValue> arguments, ValueSemantics semantics) {
        MooValue value = arguments.get(0);
        ListValue list = (ListValue) arguments.get(1);
        List<MooValue> positions = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("all_members host work was canceled");
            }
            if (semantics.equal(list.elements().get(index), value, false)) {
                positions.add(new IntegerValue(index + 1L));
            }
        }
        return BuiltinResult.value(new ListValue(positions));
    }

    private static BuiltinResult sortValues(List<MooValue> arguments) {
        ListValue values = (ListValue) arguments.getFirst();
        boolean usingSeparateKeys =
                arguments.size() >= 2 && ((ListValue) arguments.get(1)).size() > 0;
        ListValue keys =
                usingSeparateKeys ? (ListValue) arguments.get(1) : values;
        if (keys.size() == 0) {
            return BuiltinResult.value(new ListValue(List.of()));
        }
        if (usingSeparateKeys && values.size() != keys.size()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        MooValue.Type keyType = keys.elements().getFirst().type();
        if (!sortableType(keyType)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        List<Integer> order = new ArrayList<>(keys.size());
        for (int index = 0; index < keys.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("sort host work was canceled");
            }
            if (keys.elements().get(index).type() != keyType) {
                return BuiltinResult.error(ErrorValue.E_TYPE);
            }
            order.add(index);
        }

        boolean natural = arguments.size() >= 3 && arguments.get(2).isTruthy();
        order.sort(
                (left, right) -> {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new CancellationException("sort host work was canceled");
                    }
                    return compareSortValues(
                            keys.elements().get(left), keys.elements().get(right), natural);
                });
        if (arguments.size() >= 4 && arguments.get(3).isTruthy()) {
            java.util.Collections.reverse(order);
        }

        List<MooValue> sorted = new ArrayList<>(values.size());
        for (int index : order) {
            sorted.add(values.elements().get(index));
        }
        return BuiltinResult.value(new ListValue(sorted));
    }

    private static boolean sortableType(MooValue.Type type) {
        return switch (type) {
            case INTEGER, OBJECT, ERROR, FLOAT, BOOLEAN, STRING -> true;
            case LIST, MAP, ANONYMOUS, WAIF -> false;
        };
    }

    private static int compareSortValues(MooValue left, MooValue right, boolean natural) {
        if (natural && left instanceof StringValue string) {
            return compareNaturallyIgnoringCase(string, (StringValue) right);
        }
        // Toast sort uses a strict-less predicate: booleans and NaNs compare neither way.
        if (left instanceof BooleanValue
                || (left instanceof FloatValue floating
                && (Double.isNaN(floating.value()) || Double.isNaN(((FloatValue) right).value())))) {
            return 0;
        }
        return ValueSemantics.STANDARD.compare(left, right, false);
    }

    private static int compareNaturallyIgnoringCase(StringValue left, StringValue right) {
        byte[] leftBytes = left.bytes();
        byte[] rightBytes = right.bytes();
        int leftIndex = 0;
        int rightIndex = 0;
        while (true) {
            int leftByte = cStringByte(leftBytes, leftIndex);
            int rightByte = cStringByte(rightBytes, rightIndex);
            while (isAsciiSpace(leftByte)) {
                leftByte = cStringByte(leftBytes, ++leftIndex);
            }
            while (isAsciiSpace(rightByte)) {
                rightByte = cStringByte(rightBytes, ++rightIndex);
            }
            if (isAsciiDigit(leftByte) && isAsciiDigit(rightByte)) {
                int result =
                        leftByte == '0' || rightByte == '0'
                                ? compareLeftAlignedDigits(leftBytes, leftIndex, rightBytes, rightIndex)
                                : compareRightAlignedDigits(leftBytes, leftIndex, rightBytes, rightIndex);
                if (result != 0) {
                    return result;
                }
            }
            if (leftByte == 0 && rightByte == 0) {
                return 0;
            }
            leftByte = foldAscii(leftByte);
            rightByte = foldAscii(rightByte);
            if (leftByte != rightByte) {
                return Integer.compare(leftByte, rightByte);
            }
            leftIndex++;
            rightIndex++;
        }
    }

    private static int compareLeftAlignedDigits(
            byte[] left, int leftIndex, byte[] right, int rightIndex) {
        while (true) {
            int leftByte = cStringByte(left, leftIndex++);
            int rightByte = cStringByte(right, rightIndex++);
            if (!isAsciiDigit(leftByte) && !isAsciiDigit(rightByte)) {
                return 0;
            }
            if (!isAsciiDigit(leftByte)) {
                return -1;
            }
            if (!isAsciiDigit(rightByte)) {
                return 1;
            }
            if (leftByte != rightByte) {
                return Integer.compare(leftByte, rightByte);
            }
        }
    }

    private static int compareRightAlignedDigits(
            byte[] left, int leftIndex, byte[] right, int rightIndex) {
        int bias = 0;
        while (true) {
            int leftByte = cStringByte(left, leftIndex++);
            int rightByte = cStringByte(right, rightIndex++);
            if (!isAsciiDigit(leftByte) && !isAsciiDigit(rightByte)) {
                return bias;
            }
            if (!isAsciiDigit(leftByte)) {
                return -1;
            }
            if (!isAsciiDigit(rightByte)) {
                return 1;
            }
            if (bias == 0 && leftByte != rightByte) {
                bias = Integer.compare(leftByte, rightByte);
            }
        }
    }

    private static int cStringByte(byte[] value, int index) {
        return index >= value.length ? 0 : Byte.toUnsignedInt(value[index]);
    }

    private static boolean isAsciiDigit(int value) {
        return value >= '0' && value <= '9';
    }

    private static boolean isAsciiSpace(int value) {
        return value == ' '
                || value == '\t'
                || value == '\n'
                || value == '\r'
                || value == '\f'
                || value == 0x0B;
    }

    private static BuiltinResult reverse(List<MooValue> arguments) {
        MooValue value = arguments.getFirst();
        if (value instanceof StringValue string) {
            byte[] bytes = string.bytes();
            for (int left = 0, right = bytes.length - 1; left < right; left++, right--) {
                byte exchanged = bytes[left];
                bytes[left] = bytes[right];
                bytes[right] = exchanged;
            }
            return BuiltinResult.value(StringValue.of(bytes));
        }
        if (value instanceof ListValue list) {
            List<MooValue> elements = list.elements();
            List<MooValue> reversed = new ArrayList<>(elements.size());
            for (int index = elements.size() - 1; index >= 0; index--) {
                reversed.add(elements.get(index));
            }
            return BuiltinResult.value(new ListValue(reversed));
        }
        return BuiltinResult.error(ErrorValue.E_INVARG);
    }
}
