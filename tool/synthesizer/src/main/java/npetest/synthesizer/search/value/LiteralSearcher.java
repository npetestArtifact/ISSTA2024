package npetest.synthesizer.search.value;

import npetest.commons.astmodel.CtModelExt;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import npetest.language.sequence.TestCase;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.*;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.chain.CtQueryable;

import java.util.*;
import java.util.stream.Collectors;

public class LiteralSearcher {
  private static final LiteralSearcher instance = new LiteralSearcher();

  private LiteralSearcher() {
  }

  public static LiteralSearcher getInstance() {
    return instance;
  }

  private final Collection<CtLiteral<?>> booleanValues = Arrays.asList(
          CodeFactory.createLiteral(true), CodeFactory.createLiteral(false));

  private final Map<String, Collection<CtLiteral<?>>> byteCache = new HashMap<>();
  private final Map<String, Collection<CtLiteral<?>>> shortCache = new HashMap<>();
  private final Map<String, Collection<CtLiteral<?>>> integerCache = new HashMap<>();
  private final Map<String, Collection<CtLiteral<?>>> longCache = new HashMap<>();

  private final Map<String, Collection<CtLiteral<?>>> floatCache = new HashMap<>();
  private final Map<String, Collection<CtLiteral<?>>> doubleCache = new HashMap<>();

  private final Map<String, Collection<CtLiteral<?>>> charCache = new HashMap<>();
  private final Map<String, Collection<CtLiteral<?>>> stringCache = new HashMap<>();

  public Collection<CtLiteral<?>> searchLiteralsWithTrace(TestCase originalTestcase, CtTypeReference<?> primitiveType) {
    List<ExecutableKey> calledMethods = originalTestcase.getMethodTrace().get(originalTestcase.length() - 1);
    List<CtLiteral<?>> results = new ArrayList<>();
    for (ExecutableKey calledMethod : calledMethods) {
      CtExecutable<?> executable = calledMethod.getCtElement();
      results.addAll(searchLiterals(executable, primitiveType));
    }
    if (results.isEmpty()) {
      CtPackage ctQueryable = originalTestcase.getCtPackage();
      results.addAll(searchLiterals(ctQueryable, primitiveType));
    }
    return results;
  }

  public Collection<CtLiteral<?>> searchLiterals(CtQueryable ctQueryable, CtTypeReference<?> primitiveType) {
    if (TypeUtils.isBoolean(primitiveType)) {
      return searchBooleanLiterals();
    } else if (TypeUtils.isByte(primitiveType)) {
      return searchByteLiterals(ctQueryable);
    } else if (TypeUtils.isShort(primitiveType)) {
      return searchShortLiterals(ctQueryable);
    } else if (TypeUtils.isInteger(primitiveType)) {
      return searchIntegerLiterals(ctQueryable);
    } else if (TypeUtils.isLong(primitiveType)) {
      return searchLongLiterals(ctQueryable);
    } else if (TypeUtils.isFloat(primitiveType)) {
      return searchFloatLiterals(ctQueryable);
    } else if (TypeUtils.isDouble(primitiveType)) {
      return searchDoubleLiterals(ctQueryable);
    } else if (TypeUtils.isChar(primitiveType)) {
      return searchCharLiterals(ctQueryable);
    } else {
      //TypeUtils.isString(primitiveType)
      return searchStringLiterals(ctQueryable);
    }
  }

  public Collection<CtLiteral<?>> searchBooleanLiterals() {
    return booleanValues;
  }

  public Collection<CtLiteral<?>> searchByteLiterals(CtQueryable ctQueryable) {
    String key = toKey(ctQueryable);
    if (byteCache.containsKey(key)) {
      return byteCache.get(key);
    }
    Collection<CtLiteral<?>> ctLiterals = CtModelExt.INSTANCE.queryLiterals(ctQueryable, TypeUtils.bytePrimitive());
    Set<CtLiteral<?>> uniqueLiterals = handleLiterals(ctLiterals);
    byteCache.put(key, uniqueLiterals);
    return uniqueLiterals.stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  private Collection<CtLiteral<?>> searchShortLiterals(CtQueryable ctQueryable) {
    String key = toKey(ctQueryable);
    if (shortCache.containsKey(key)) {
      return shortCache.get(key);
    }
    Collection<CtLiteral<?>> ctLiterals = CtModelExt.INSTANCE.queryLiterals(ctQueryable, TypeUtils.shortPrimitive());
    Set<CtLiteral<?>> uniqueLiterals = handleLiterals(ctLiterals);
    shortCache.put(key, uniqueLiterals);
    return uniqueLiterals.stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public Collection<CtLiteral<?>> searchIntegerLiterals(CtQueryable ctQueryable) {
    String key = toKey(ctQueryable);
    if (integerCache.containsKey(key)) {
      return integerCache.get(key).stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
    }
    Collection<CtLiteral<?>> ctLiterals = CtModelExt.INSTANCE.queryLiterals(ctQueryable, TypeUtils.integerPrimitive());
    Set<CtLiteral<?>> uniqueLiterals = handleLiterals(ctLiterals);
    integerCache.put(key, uniqueLiterals);
    return uniqueLiterals.stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public Collection<CtLiteral<?>> searchLongLiterals(CtQueryable ctQueryable) {
    String key = toKey(ctQueryable);
    if (longCache.containsKey(key)) {
      return longCache.get(key).stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
    }
    Collection<CtLiteral<?>> ctLiterals = CtModelExt.INSTANCE.queryLiterals(ctQueryable, TypeUtils.longPrimitive());
    Set<CtLiteral<?>> uniqueLiterals = handleLiterals(ctLiterals);
    longCache.put(key, uniqueLiterals);
    return uniqueLiterals.stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
  }


  public Collection<CtLiteral<?>> searchFloatLiterals(CtQueryable ctQueryable) {
    String key = toKey(ctQueryable);
    if (floatCache.containsKey(key)) {
      return floatCache.get(key).stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
    }
    Collection<CtLiteral<?>> ctLiterals = CtModelExt.INSTANCE.queryLiterals(ctQueryable, TypeUtils.floatPrimitive());
    Set<CtLiteral<?>> uniqueLiterals = handleLiterals(ctLiterals);
    floatCache.put(key, uniqueLiterals);
    return uniqueLiterals.stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public Collection<CtLiteral<?>> searchDoubleLiterals(CtQueryable ctQueryable) {
    String key = toKey(ctQueryable);
    if (doubleCache.containsKey(key)) {
      return doubleCache.get(key).stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
    }
    Collection<CtLiteral<?>> ctLiterals = CtModelExt.INSTANCE.queryLiterals(ctQueryable, TypeUtils.doublePrimitive());
    Set<CtLiteral<?>> uniqueLiterals = handleLiterals(ctLiterals);
    doubleCache.put(key, uniqueLiterals);
    return uniqueLiterals.stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public Collection<CtLiteral<?>> searchCharLiterals(CtQueryable ctQueryable) {
    String key = toKey(ctQueryable);
    if (charCache.containsKey(key)) {
      return charCache.get(key).stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
    }
    Collection<CtLiteral<?>> ctLiterals = CtModelExt.INSTANCE.queryLiterals(ctQueryable, TypeUtils.charPrimitive());
    Set<CtLiteral<?>> uniqueLiterals = handleLiterals(ctLiterals);
    charCache.put(key, uniqueLiterals);
    return uniqueLiterals.stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  public Collection<CtLiteral<?>> searchStringLiterals(CtQueryable ctQueryable) {
    String key = toKey(ctQueryable);
    if (stringCache.containsKey(key)) {
      return stringCache.get(key).stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
    }
    Collection<CtLiteral<?>> ctLiterals = CtModelExt.INSTANCE.queryLiterals(ctQueryable, TypeUtils.stringType());
    Set<CtLiteral<?>> uniqueLiterals = handleLiterals(ctLiterals).stream().filter(
            lit -> !((String) lit.getValue()).matches(".*\\.txt") &&
                    !((String) lit.getValue()).matches(".*\\.log")).collect(Collectors.toSet());
    stringCache.put(key, uniqueLiterals);
    return uniqueLiterals.stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toList());
  }

  private Set<CtLiteral<?>> handleLiterals(Collection<CtLiteral<?>> literals) {
    Set<CtLiteral<?>> newLiterals = literals.stream().filter(literal -> literal.getRoleInParent() != null
                    && literal.getParent(CtAnnotation.class) == null
                    && !(literal.getRoleInParent().equals(CtRole.DEFAULT_EXPRESSION)
                    && literal.getParent() instanceof CtField<?>))
            .collect(Collectors.toSet());
    return newLiterals.stream().map(CtLiteral::getValue).map(CodeFactory::createLiteral).collect(Collectors.toSet());
  }

  private static String toKey(CtQueryable ctQueryable) {
    if (ctQueryable instanceof CtExecutable<?>) {
      return ExecutableKey.of((CtExecutable<?>) ctQueryable).toString();
    } else if (ctQueryable instanceof CtType<?>) {
      return ((CtType<?>) ctQueryable).getQualifiedName();
    } else if (ctQueryable instanceof CtPackage) {
      return ((CtPackage) ctQueryable).getQualifiedName();
    }
    throw new UnsupportedOperationException(
            String.format("query root of LiteralSearcher should be either CtExecutable, CtType, or CtPackage. Got=%s",
                    ctQueryable.getClass().getSimpleName()));
  }
}
