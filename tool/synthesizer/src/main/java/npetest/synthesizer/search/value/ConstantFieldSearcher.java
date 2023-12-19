package npetest.synthesizer.search.value;

import npetest.commons.spoon.TypeUtils;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class ConstantFieldSearcher {
  private static final ConstantFieldSearcher instance = new ConstantFieldSearcher();

  private ConstantFieldSearcher() {
  }

  public static ConstantFieldSearcher getInstance() {
    return instance;
  }

  private final Map<String, Collection<CtField<?>>> byteCache = new HashMap<>();
  private final Map<String, Collection<CtField<?>>> shortCache = new HashMap<>();
  private final Map<String, Collection<CtField<?>>> integerCache = new HashMap<>();
  private final Map<String, Collection<CtField<?>>> longCache = new HashMap<>();

  private final Map<String, Collection<CtField<?>>> floatCache = new HashMap<>();
  private final Map<String, Collection<CtField<?>>> doubleCache = new HashMap<>();

  private final Map<String, Collection<CtField<?>>> charCache = new HashMap<>();
  private final Map<String, Collection<CtField<?>>> stringCache = new HashMap<>();


  public Collection<CtField<?>> searchFields(CtPackage ctPackage, CtTypeReference<?> primitiveType) {
    if (TypeUtils.isBoolean(primitiveType)) {
      return searchBooleanFields();
    } else if (TypeUtils.isByte(primitiveType)) {
      return searchByteFields(ctPackage, primitiveType);
    } else if (TypeUtils.isShort(primitiveType)) {
      return searchShortFields(ctPackage, primitiveType);
    } else if (TypeUtils.isInteger(primitiveType)) {
      return searchIntegerFields(ctPackage, primitiveType);
    } else if (TypeUtils.isLong(primitiveType)) {
      return searchLongFields(ctPackage, primitiveType);
    } else if (TypeUtils.isFloat(primitiveType)) {
      return searchFloatFields(ctPackage, primitiveType);
    } else if (TypeUtils.isDouble(primitiveType)) {
      return searchDoubleFields(ctPackage, primitiveType);
    } else if (TypeUtils.isChar(primitiveType)) {
      return searchCharFields(ctPackage, primitiveType);
    } else {
      //TypeUtils.isString(primitiveType)
      return searchStringFields(ctPackage, primitiveType);
    }
  }

  public Collection<CtField<?>> searchBooleanFields() {
    return new ArrayList<>();
  }

  public Collection<CtField<?>> searchByteFields(CtPackage ctPackage, CtTypeReference<?> primitiveType) {
    return searchFields(ctPackage, primitiveType, byteCache);
  }

  public Collection<CtField<?>> searchShortFields(CtPackage ctPackage, CtTypeReference<?> primitiveType) {
    return searchFields(ctPackage, primitiveType, shortCache);
  }

  public Collection<CtField<?>> searchIntegerFields(CtPackage ctPackage, CtTypeReference<?> primitiveType) {
    return searchFields(ctPackage, primitiveType, integerCache);
  }

  public Collection<CtField<?>> searchLongFields(CtPackage ctPackage, CtTypeReference<?> primitiveType) {
    return searchFields(ctPackage, primitiveType, longCache);
  }


  public Collection<CtField<?>> searchFloatFields(CtPackage ctPackage, CtTypeReference<?> primitiveType) {
    return searchFields(ctPackage, primitiveType, floatCache);
  }

  public Collection<CtField<?>> searchDoubleFields(CtPackage ctPackage, CtTypeReference<?> primitiveType) {
    return searchFields(ctPackage, primitiveType, doubleCache);
  }

  public Collection<CtField<?>> searchCharFields(CtPackage ctPackage, CtTypeReference<?> primitiveType) {
    return searchFields(ctPackage, primitiveType, charCache);
  }

  public Collection<CtField<?>> searchStringFields(CtPackage ctPackage, CtTypeReference<?> primitiveType) {
    return searchFields(ctPackage, primitiveType, stringCache);
  }

  private Collection<CtField<?>> searchFields(CtPackage ctPackage, CtTypeReference<?> primitiveType,
                                              Map<String, Collection<CtField<?>>> cache) {
    String key = ctPackage.getQualifiedName();
    if (cache.containsKey(key)) {
      return cache.get(key);
    }

    List<CtField<?>> ctFieldAccesses = ctPackage.filterChildren(new TypeFilter<>(CtField.class))
            .select((CtField<?> f) -> f.isStatic() && f.isPublic() && f.isFinal())
            .select((CtField<?> f) -> f.getType().equals(primitiveType))
            .list();
    cache.put(key, ctFieldAccesses);
    return ctFieldAccesses;
  }
}
