package npetest.commons.models.java.util;

import npetest.commons.models.ModeledType;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class PropertiesModel extends ModeledType<Properties> {
  private static final List<String> modifyingMethodSignatures =
          Arrays.asList("setProperty(java.lang.String,java.lang.String)", "remove(java.lang.Object)");

  @SuppressWarnings("unchecked")
  public PropertiesModel(CtTypeReference<?> ctTypeReference) {
    this.ctTypeReference = (CtTypeReference<Properties>) ctTypeReference;
    this.ctType = (CtType<Properties>) ctTypeReference.getTypeDeclaration();
  }

  @Override
  public CtConstructor<?> getConstructor() {
    if (constructor != null) {
      return constructor;
    }
    constructor = getDefaultConstructor();
    return constructor;
  }

  @Override
  public List<CtMethod<?>> getModifyingMethods() {
    if (modifyingMethodsCache.isEmpty()) {
      modifyingMethodSignatures.stream()
              .map(methodName -> ctType.getMethods().stream()
                      .filter(m -> m.getSignature().equals((methodName))).collect(Collectors.toList()))
              .flatMap(List::stream)
              .forEach(modifyingMethodsCache::add);
    }
    return modifyingMethodsCache;
  }
}
