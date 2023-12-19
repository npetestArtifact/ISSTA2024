package npetest.synthesizer.mutation.literal;

import java.util.Arrays;
import java.util.Objects;

import npetest.commons.misc.RandomUtils;

class ByteLiteralMutator extends LiteralMutator<Byte> {

  enum Operator {
    BIT_FLIP, RANDOM_BIT_FLIP, ALL_BITS_FLIP
  }

  @Override
  protected Byte mutateT(Byte originalValue) {
    Operator op = RandomUtils.select(Arrays.asList(Operator.values()));
    switch (Objects.requireNonNull(op)) {
      case BIT_FLIP:
        return (byte) ((int) originalValue ^ 1);
      case RANDOM_BIT_FLIP:
        return (byte) ((int) originalValue ^ (1 << RandomUtils.random.nextInt(8)));
      case ALL_BITS_FLIP:
        return (byte) ((int) originalValue ^ 0b11111111);
    }
    return originalValue;
  }
}
