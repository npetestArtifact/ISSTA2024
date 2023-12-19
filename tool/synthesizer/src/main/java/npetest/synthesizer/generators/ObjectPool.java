package npetest.synthesizer.generators;

import npetest.commons.keys.TypeKey;
import npetest.commons.keys.TypeReferenceKey;
import npetest.commons.misc.RandomUtils;
import npetest.commons.spoon.TypeUtils;
import npetest.language.VariableType;
import npetest.language.sequence.Sequence;
import npetest.commons.cluster.ConstructorCluster;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectPool {
  private ObjectPool() {
  }

  private static final Map<TypeReferenceKey, List<Sequence>> objects = new HashMap<>();

  private static final Map<TypeKey, Integer> objectCreationTrials = new HashMap<>();

  public static void updateTrial(CtTypeReference<?> instanceType) {
    if (TypeUtils.isNull(instanceType)) {
      return;
    }
    TypeKey key = TypeKey.of(instanceType);
    int count = objectCreationTrials.getOrDefault(key, 0);
    objectCreationTrials.put(key, count + 1);
  }

  public static void put(VariableType inputType, Sequence objectSequence) {
    CtTypeReference<?> declType = inputType.getDeclType();
    CtTypeReference<?> instanceType = inputType.getInstanceType();
    TypeReferenceKey key = TypeReferenceKey.of(TypeUtils.isNull(instanceType) ? declType : instanceType);
    List<Sequence> objectSequences = objects.computeIfAbsent(key, k -> new ArrayList<>());
    objectSequences.add(objectSequence);
  }

  public static boolean exploit(VariableType inputType) {
    CtTypeReference<?> declType = inputType.getDeclType();
    CtTypeReference<?> instanceType = inputType.getInstanceType();
    if (TypeUtils.isNull(instanceType)) {
      return false;
    }

    TypeReferenceKey key = TypeReferenceKey.of(TypeUtils.isNull(instanceType) ? declType : instanceType);
    List<Sequence> sequences = objects.computeIfAbsent(key, k -> new ArrayList<>());

    if (sequences.isEmpty()) {
      return false;
    }

    int size = ConstructorCluster.getInstance().countConstructors(instanceType);
    float p = size == 0 ? 0 : ((float) sequences.size() / size);
    return RandomUtils.p() < p;
  }

  public static Sequence selectExistingObject(VariableType inputType) {
    CtTypeReference<?> instanceType = inputType.getInstanceType();
    TypeReferenceKey key = TypeReferenceKey.of(instanceType);
    List<Sequence> sequences = objects.get(key);
    return RandomUtils.select(sequences);
  }
}
