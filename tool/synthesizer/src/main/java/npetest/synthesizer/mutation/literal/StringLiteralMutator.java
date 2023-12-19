package npetest.synthesizer.mutation.literal;

import npetest.commons.misc.RandomUtils;
import npetest.synthesizer.search.value.PredefinedLiteralPool;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class StringLiteralMutator extends LiteralMutator<String> {

  enum Operator {
    RANDOM_PREFIX, RANDOM_SUFFIX, SHUFFLE, CLEAR, INSERT_PREDEFINED,
  }

  @Override
  protected String mutateT(String originalValue) {
    int length = originalValue == null ? 0 : originalValue.length();
    if (length == 0) {
      List<String> stringValues = PredefinedLiteralPool.getStringValues();
      return RandomUtils.select(stringValues);
    }

    Operator op = RandomUtils.select(Arrays.asList(Operator.values()));
    switch (Objects.requireNonNull(op)) {
      case RANDOM_PREFIX:
        return originalValue.substring(0, RandomUtils.random.nextInt(length));
      case RANDOM_SUFFIX:
        return originalValue.substring(RandomUtils.random.nextInt(length));
      case SHUFFLE:
        List<String> letters = Arrays.asList(originalValue.split(""));
        Collections.shuffle(letters);
        StringBuilder builder = new StringBuilder();
        for (String letter : letters) {
          builder.append(letter);
        }
        return builder.toString();
      case INSERT_PREDEFINED:
        int i = RandomUtils.random.nextInt(length);
        List<String> stringValues = PredefinedLiteralPool.getStringValues();
        String select = RandomUtils.select(stringValues);
        return new StringBuilder(originalValue).insert(i, select).toString();
      case CLEAR:
        return "";
    }
    return originalValue;
  }
}
