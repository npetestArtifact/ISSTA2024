package npetest.commons.models.java.util;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.models.ModeledType;
import npetest.commons.spoon.TypeUtils;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class MapModel extends ModeledType<Map> {
  private static final List<String> modifyingMethodSignatures =
          Arrays.asList("put(java.lang.Object,java.lang.Object)", "remove(java.lang.Object)", "clear()", "keySet()", "getOrDefault(java.lang.Object,java.lang.Object)",
                        "putAll(java.util.Map)", "putIfAbsent(java.lang.Object,java.lang.Object)", "replace(java.lang.Object,java.lang.Object,java.lang.Object)");

  @SuppressWarnings("unchecked")
  public MapModel(CtTypeReference<?> typeReference) {
    this.ctTypeReference = (CtTypeReference<? extends Map>) typeReference;
    this.ctType = (CtType<HashMap>) CtModelExt.INSTANCE.getShadowType(HashMap.class);
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
              .map(methodName -> TypeUtils.mapType().getTypeDeclaration().getMethods()
                      .stream().filter(m -> m.getSignature().equals((methodName))).collect(Collectors.toList()))
              .flatMap(List::stream)
              .forEach(modifyingMethodsCache::add);
    }
    return modifyingMethodsCache;
  }
}
