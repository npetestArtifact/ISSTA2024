package npetest.synthesizer.typeadaption;

import npetest.commons.spoon.TypeUtils;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class ObjectTypeReplacement {
  private ObjectTypeReplacement() {
  }

  public static List<CtTypeReference<?>> getCandidateTypesForJavaLangObject(CtTypeReference<?> baseObjectType) {
    List<CtTypeReference<?>> candidates = TypeUtils.gatherTypeArguments(baseObjectType.filterChildren(new TypeFilter<>(CtTypeReference.class)));
    if (baseObjectType.getSuperclass() != null) {
      candidates.addAll(TypeUtils.gatherTypeArguments(baseObjectType.getSuperclass().filterChildren(new TypeFilter<>(CtTypeReference.class))));
    }
    if (baseObjectType.getSuperInterfaces() != null && !baseObjectType.getSuperInterfaces().isEmpty()) {
      for (CtTypeReference<?> superInterface : baseObjectType.getSuperInterfaces()) {
        candidates.addAll(TypeUtils.gatherTypeArguments(superInterface.filterChildren(new TypeFilter<>(CtTypeReference.class))));
      }
    }
    return candidates;
  }
}
