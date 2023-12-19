package npetest.commons.keys;

import npetest.commons.spoon.TypeUtils;
import spoon.reflect.reference.CtTypeReference;

public class TypeReferenceKey implements SpoonElementKey {
  private final CtTypeReference<?> ctTypeReference;

  private final String key;

  private TypeReferenceKey(CtTypeReference<?> ctTypeReference) {
    this.ctTypeReference = ctTypeReference;
    this.key = toKey(ctTypeReference);
  }

  public static TypeReferenceKey of(CtTypeReference<?> instanceType) {
    return instanceType == null ? null : new TypeReferenceKey(instanceType);
  }

  private String toKey(CtTypeReference<?> instanceType) {
    if (TypeUtils.isPrimitive(instanceType) || TypeUtils.isBoxingType(instanceType) || TypeUtils.isString(instanceType)) {
      return TypeUtils.toQualifiedName(instanceType);
    } else {
      return instanceType.toString();
    }
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public CtTypeReference<?> getCtElement() {
    return ctTypeReference;
  }

  @Override
  public String toString() {
    return getKey();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TypeReferenceKey that = (TypeReferenceKey) o;

    return key.equals(that.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }
}
