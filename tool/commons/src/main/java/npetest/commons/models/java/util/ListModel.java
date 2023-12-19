package npetest.commons.models.java.util;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.models.ModeledType;
import npetest.commons.spoon.TypeUtils;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class ListModel extends ModeledType<List> {
  private static final List<String> modifyingMethodSignatures =
          Arrays.asList("add(java.lang.Object)", "remove(java.lang.Object)", "clear()");

  @SuppressWarnings("unchecked")
  public ListModel(CtTypeReference<?> typeReference) {
    this.ctTypeReference = (CtTypeReference<? extends List>) typeReference;
    this.ctType = (CtType<ArrayList>) CtModelExt.INSTANCE.getShadowType(ArrayList.class);
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
              .map(methodName -> TypeUtils.listType().getTypeDeclaration().getMethods()
                      .stream().filter(m -> m.getSignature().equals((methodName))).collect(Collectors.toList()))
              .flatMap(List::stream)
              .forEach(modifyingMethodsCache::add);
    }
    return modifyingMethodsCache;
  }
}
