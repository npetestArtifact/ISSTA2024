package npetest.commons.keys;

import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.reference.CtExecutableReference;

public class ExecutableKey implements SpoonElementKey {
  private final CtExecutable<?> executable;

  private final String key;

  private ExecutableKey(CtExecutable<?> executable) {
    this.executable = executable;
    this.key = toKey(executable);
  }

  public static ExecutableKey of(CtExecutable<?> executable) {
    return new ExecutableKey(executable);
  }

  public static ExecutableKey of(CtExecutableReference<?> executableReference) {
    return new ExecutableKey(executableReference.getExecutableDeclaration());
  }

  private String toKey(CtExecutable<?> executable) {
    if (executable == null) {
      return "null";
    }
    assert executable instanceof CtTypeMember;
    if (executable instanceof CtConstructor<?>) {
      return executable.getSignature();
    }
    CtType<?> declaringType = ((CtTypeMember) executable).getDeclaringType();
    return String.format("%s#%s", declaringType.getQualifiedName(), executable.getSignature());
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public CtExecutable<?> getCtElement() {
    return executable;
  }

  @Override
  public String toString() {
    return getKey();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExecutableKey that = (ExecutableKey) o;

    return key.equals(that.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }
}
