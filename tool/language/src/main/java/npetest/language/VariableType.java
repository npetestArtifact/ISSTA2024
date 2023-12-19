package npetest.language;

import java.util.ArrayList;
import java.util.Set;

import npetest.commons.spoon.TypeUtils;
import spoon.reflect.reference.CtTypeReference;

public class VariableType {
  private final CtTypeReference<?> declType;

  private final CtTypeReference<?> instanceType;

  private final boolean successAdaption;

  private VariableType(CtTypeReference<?> declType, CtTypeReference<?> instanceType, boolean successAdaption) {
    this.declType = declType;
    this.instanceType = instanceType;
    this.successAdaption = successAdaption;
  }

  public boolean successAdaption() {
    return successAdaption;
  }

  public static VariableType fromFailedAdaption(CtTypeReference<?> declType) {
    return new VariableType(declType, TypeUtils.nullType(), false);
  }

  public static VariableType fromSuccessfulAdaption(CtTypeReference<?> declType, CtTypeReference<?> instanceType) {
    return new VariableType(declType, instanceType, true);
  }

  public static VariableType fromInstanceType(CtTypeReference<?> instanceType) {
    CtTypeReference<?> declType;
    if (instanceType.isAnonymous()) {
      declType = instanceType.getSuperclass();
      if (declType == null) {
        Set<CtTypeReference<?>> superInterfaces = instanceType.getSuperInterfaces();
        declType = superInterfaces.size() != 1 ? null : new ArrayList<>(superInterfaces).get(0);
      }
    } else {
      declType = instanceType;
    }
    return new VariableType(declType, instanceType, true);
  }

  public CtTypeReference<?> getDeclType() {
    return declType;
  }

  public CtTypeReference<?> getInstanceType() {
    return instanceType;
  }

  public boolean inputNull() {
    return TypeUtils.isNull(instanceType);
  }

  @Override
  public String toString() {
    return "VariableType{" +
            "declType=" + declType +
            ", instanceType=" + instanceType +
            '}';
  }
}
