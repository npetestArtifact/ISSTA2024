package npetest.commons.keys;

import npetest.commons.spoon.TypeUtils;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;

public class TypeKey implements SpoonElementKey {
  private CtType<?> ctType;

  // This is set if the type is non-primitive array
  private final CtTypeReference<?> arrayTypeReference;

  private final String key;

  private boolean isTypeLoaded = true;

  private TypeKey(CtArrayTypeReference<?> arrayTypeReference) {
    this.ctType = null;
    this.key = arrayTypeReference.getQualifiedName();
    this.arrayTypeReference = arrayTypeReference;
  }

  private TypeKey(CtType<?> ctType) {
    this.ctType = ctType;
    this.key = TypeUtils.toQualifiedName(ctType);
    this.arrayTypeReference = null;
  }

  public TypeKey(String typeName) {
    this.ctType = null;
    this.key = typeName;
    this.arrayTypeReference = null;
    this.isTypeLoaded = false;
  }

  public void setCtType(CtType<?> ctType) {
    this.ctType = ctType;
    this.isTypeLoaded = true;
  }

  public static TypeKey of(CtTypeReference<?> typeReference) {
    if (TypeUtils.isNonPrimitiveArray(typeReference)) {
      return new TypeKey((CtArrayTypeReference<?>) typeReference);
    }
    CtType<?> ctType = typeReference.getTypeDeclaration();
    return ctType != null ? new TypeKey(ctType) : new TypeKey(typeReference.getQualifiedName());
  }

  public static TypeKey of(CtType<?> type) {
    return new TypeKey(type);
  }

  public static TypeKey of(String typeName) {
    return new TypeKey(typeName);
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public String toString() {
    return getKey();
  }

  @Override
  public CtElement getCtElement() {
    return ctType == null ? arrayTypeReference : ctType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TypeKey that = (TypeKey) o;

    return key.equals(that.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  public boolean isTypeLoaded() {
    return isTypeLoaded;
  }
}
