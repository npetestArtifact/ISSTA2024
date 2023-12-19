package npetest.synthesizer.search.value;

import npetest.commons.misc.RandomUtils;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PredefinedLiteralPool {
  private PredefinedLiteralPool() {
  }

  private static final List<Boolean> booleanValues = Arrays.asList(true, false);

  private static final List<Byte> byteValues = Arrays.asList((byte) -127, (byte) -1, (byte) 0, (byte) 1, (byte) 127);
  private static final List<Short> shortValues = Arrays.asList((short) -1, (short) 0, (short) 1);
  private static final List<Integer> integerValues = Arrays.asList(-1, 0, 1);
  private static final List<Long> longValues = Arrays.asList(-1L, 0L, 1L);
  private static final List<Float> floatValues = Arrays.asList(-1.0F, 0.0F, 1.0F);
  private static final List<Double> doubleValues = Arrays.asList(-1.0, 0.0, 1.0);
  private static final char[] charValues = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789$&@?<>~!%#:;,.".toCharArray();
  private static final List<Character> charLiterals = new ArrayList<>();
  private static final String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789$&@?<>~!%#:;,.";

  public static List<CtLiteral<?>> getLiterals(CtTypeReference<?> primitiveType) {
    if (TypeUtils.isBoolean(primitiveType)) {
      return getBooleanLiterals();
    } else if (TypeUtils.isByte(primitiveType)) {
      return getByteLiterals();
    } else if (TypeUtils.isShort(primitiveType)) {
      return getShortLiterals();
    } else if (TypeUtils.isInteger(primitiveType)) {
      return getIntegerLiterals();
    } else if (TypeUtils.isLong(primitiveType)) {
      return getLongLiterals();
    } else if (TypeUtils.isFloat(primitiveType)) {
      return getFloatLiterals();
    } else if (TypeUtils.isDouble(primitiveType)) {
      return getDoubleLiterals();
    } else if (TypeUtils.isChar(primitiveType)) {
      return getCharLiterals();
    } else {
      //TypeUtils.isString(primitiveType)
      return getStringLiterals();
    }
  }

  public static List<CtLiteral<?>> getBooleanLiterals() {
    return booleanValues.stream().map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public static List<CtLiteral<?>> getByteLiterals() {
    return byteValues.stream().map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public static List<CtLiteral<?>> getShortLiterals() {
    return shortValues.stream().map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public static List<CtLiteral<?>> getIntegerLiterals() {
    return integerValues.stream().map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public static List<CtLiteral<?>> getLongLiterals() {
    return longValues.stream().map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public static List<CtLiteral<?>> getFloatLiterals() {
    return floatValues.stream().map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public static List<CtLiteral<?>> getDoubleLiterals() {
    return doubleValues.stream().map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public static List<CtLiteral<?>> getCharLiterals() {
    if (charLiterals.isEmpty()) {
      for (char charValue : charValues) {
        charLiterals.add(charValue);
      }
    }
    return charLiterals.stream().map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public static List<CtLiteral<?>> getStringLiterals() {
    List<String> stringValues = getStringValues();
    return stringValues.stream().map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public static List<String> getStringValues() {
    int count = RandomUtils.random.nextInt(9) + 1;
    List<String> stringValues = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int length = RandomUtils.random.nextInt(10);
      StringBuilder builder = new StringBuilder();
      for (int j = 0; j < length; j++) {
        int index = RandomUtils.random.nextInt(charSet.length());
        builder.append(charSet.charAt(index));
      }
      stringValues.add(builder.toString());
    }
    return stringValues;
  }
}
