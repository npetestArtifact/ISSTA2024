package npetest.synthesizer.mutation.literal;

import java.util.Arrays;
import java.util.Objects;

import npetest.commons.misc.RandomUtils;

class CharacterLiteralMutator extends LiteralMutator<Character> {

  enum Operator {
    BIT_FLIP, RANDOM_BIT_FLIP, ALL_BITS_FLIP
  }

  @Override
  protected Character mutateT(Character originalValue) {
    Operator op = RandomUtils.select(Arrays.asList(Operator.values()));
    switch (Objects.requireNonNull(op)) {
      case BIT_FLIP:
        return (char) ((int) originalValue ^ 1);
      case RANDOM_BIT_FLIP:
        return (char) ((int) originalValue ^ (1 << RandomUtils.random.nextInt(16)));
      case ALL_BITS_FLIP:
        return (char) ((int) originalValue ^ Character.MAX_VALUE);
    }
    return originalValue;
  }
}
