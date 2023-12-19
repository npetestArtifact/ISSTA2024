package npetest.synthesizer.mutation.literal;

class BooleanLiteralMutator extends LiteralMutator<Boolean> {
  @Override
  protected Boolean mutateT(Boolean originalValue) {
    return !originalValue;
  }
}
