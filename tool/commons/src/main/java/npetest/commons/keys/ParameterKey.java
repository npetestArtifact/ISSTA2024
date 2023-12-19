package npetest.commons.keys;

import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;

public class ParameterKey implements SpoonElementKey {
  private final CtExecutable<?> executable;

  private final CtParameter<?> parameter;

  private final String key;

  private ParameterKey(CtExecutable<?> executable, String key, int index) {
    this.executable = executable;
    this.key = key;
    this.parameter = executable.getParameters().get(index);
  }

  public static ParameterKey of(CtExecutable<?> executable, int index) {
    String signature = executable.getSignature();
    CtType<?> declaringType = ((CtTypeMember) executable).getDeclaringType();
    String declaringTypeName = declaringType.getQualifiedName();
    String key = String.format("%s#%s.%d", declaringTypeName, signature, index);
    return new ParameterKey(executable, key, index);
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
  public CtParameter<?> getCtElement() {
    return parameter;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ParameterKey that = (ParameterKey) o;
    return key.equals(that.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  public CtExecutable<?> getExecutable() {
    return executable;
  }
}
