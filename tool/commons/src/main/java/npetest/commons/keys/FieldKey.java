package npetest.commons.keys;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;

public class FieldKey implements SpoonElementKey {
  private final CtField<?> field;

  private final String key;

  private FieldKey(CtField<?> field) {
    this.field = field;
    this.key = toKey(field);
  }

  public static FieldKey of(CtField<?> field) {
    return new FieldKey(field);
  }

  private String toKey(CtField<?> field) {
    CtType<?> declaringType = field.getDeclaringType();
    return String.format("%s#%s", declaringType.getQualifiedName(), field.getSimpleName());
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public CtElement getCtElement() {
    return field;
  }

  @Override
  public String toString() {
    return getKey();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FieldKey that = (FieldKey) o;

    return key.equals(that.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }
}
