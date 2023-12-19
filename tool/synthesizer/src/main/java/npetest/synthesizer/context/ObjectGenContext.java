package npetest.synthesizer.context;

import npetest.commons.keys.TypeKey;
import npetest.commons.spoon.TypeUtils;
import npetest.synthesizer.generators.ObjectPool;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ObjectGenContext {
  private ObjectGenContext() {
  }

  private static final Set<String> failedTypes = new HashSet<>();

  private static final Map<TypeKey, Integer> workingObjectCount = new HashMap<>();

  private static final Deque<TypeKey> workingObjectTypes = new ArrayDeque<>();

  private static final int WORKING_OBJECT_TYPES_LIMIT = 10;

  private static final int WORKING_OBJECT_RECURSION_LIMIT = 3;

  public static void startGeneration(CtTypeReference<?> instanceType) {
    addWorkingInstanceType(instanceType);
    ObjectPool.updateTrial(instanceType);
  }

  public static void addWorkingInstanceType(CtTypeReference<?> instanceType) {
    if (TypeUtils.isNull(instanceType)) {
      return;
    }
    TypeKey key = TypeKey.of(instanceType);
    Integer count = workingObjectCount.getOrDefault(key, 0);
    count++;
    workingObjectCount.put(key, count);
    workingObjectTypes.add(key);
  }

  public static boolean checkWorkingObjectTypesLimit() {
    return workingObjectCount.values().stream().mapToInt(Integer::intValue).sum() >= WORKING_OBJECT_TYPES_LIMIT;
  }

  public static boolean checkInstanceRecursionLimit(CtTypeReference<?> instanceType) {
    if (TypeUtils.isNull(instanceType)) {
      return false;
    }
    TypeKey key = TypeKey.of(instanceType);
    Integer count = workingObjectCount.getOrDefault(key, 0);
    return count >= WORKING_OBJECT_RECURSION_LIMIT;
  }

  public static void done(CtTypeReference<?> instanceType) {
    if (TypeUtils.isNull(instanceType)) {
      return;
    }
    TypeKey key = TypeKey.of(instanceType);
    int count = workingObjectCount.get(key);
    count--;
    if (count == 0) {
      workingObjectCount.remove(key);
    } else {
      workingObjectCount.put(key, count);
    }
    workingObjectTypes.removeLastOccurrence(key);
  }

  public static CtType<?> peek() {
    TypeKey typeKey = workingObjectTypes.peekLast();
    return typeKey == null ? null : ((CtType<?>) typeKey.getCtElement());
  }

  public static void clear() {
    workingObjectTypes.clear();
  }

  public static void failure(CtTypeReference<?> instanceType) {
    done(instanceType);
    failedTypes.add(instanceType.getQualifiedName());
  }

  public static boolean hasFailed(CtTypeReference<?> type) {
    return failedTypes.contains(type.getQualifiedName());
  }
}
