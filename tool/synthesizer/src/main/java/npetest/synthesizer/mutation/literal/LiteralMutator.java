package npetest.synthesizer.mutation.literal;

abstract class LiteralMutator<T> {
  protected abstract T mutateT(T originalValue);

  @SuppressWarnings("unchecked")
  public T mutate(Object originalValue) {
    return mutateT((T) originalValue);
  }
}
