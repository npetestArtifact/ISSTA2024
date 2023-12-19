package npetest.commons.models;

import npetest.commons.spoon.TypeUtils;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;

import java.util.ArrayList;
import java.util.List;

public abstract class ModeledType<T> {
  protected CtType<? extends T> ctType;

  protected CtTypeReference<? extends T> ctTypeReference;

  protected CtConstructor<?> constructor;

  protected CtMethod<?> factoryMethod;
  protected final List<CtMethod<?>> modifyingMethodsCache = new ArrayList<>();

  public CtConstructor<?> getConstructor() {
    return null;
  }

  public CtMethod<?> getFactoryMethod() {
    return null;
  }

  public List<CtMethod<?>> getModifyingMethods() {
    return new ArrayList<>();
  }

  protected CtConstructor<?> getDefaultConstructor() {
    if (ctType.isClass()) {
      return ((CtClass<?>) ctType).getConstructors().stream()
              .filter(ctConstructor -> ctConstructor.getParameters().isEmpty())
              .findFirst().orElse(null);
    } else {
      return null;
    }
  }

  public CtTypeReference<?> getAdaptedType() {
    List<CtTypeReference<?>> actualTypeArguments = new ArrayList<>();
    for (CtTypeReference<?> actualTypeArgument : this.ctTypeReference.getActualTypeArguments()) {
      CtTypeReference<?> typeArgument;
      if (!(actualTypeArgument instanceof CtWildcardReference)) {
        typeArgument = actualTypeArgument.clone();
      } else {
        if (((CtWildcardReference) actualTypeArgument).getBoundingType() == null) {
          typeArgument = TypeUtils.objectType().clone();
        } else {
          typeArgument = ((CtWildcardReference) actualTypeArgument).getBoundingType();
        }
      }
      actualTypeArguments.add(typeArgument);
    }
    return ctType.getReference().setActualTypeArguments(actualTypeArguments);
  }

  public String getQualifiedName() {
    return ctType.getQualifiedName();
  }
}
