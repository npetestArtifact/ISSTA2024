package npetest.synthesizer.typeadaption;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import spoon.SpoonException;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ClassTypingContext;

public class TypeAdapter {
  private TypeAdapter() {
  }

  public static CtTypeReference<?> adaptType(CtTypeReference<?> targetType, CtTypeReference<?> baseType) {
    ClassTypingContext classTypingContext = new ClassTypingContext(baseType);
    CtTypeReference<?> result = null;
    try {
      if (targetType instanceof CtTypeParameterReference) {
        if (targetType.getParent() instanceof CtArrayTypeReference<?>) {
          result = applyManualAdaption(baseType, targetType, classTypingContext);
        } else {
          result = classTypingContext.adaptType(targetType);
        }
      }
    } catch (SpoonException e) {
      result = applyManualAdaption(baseType, targetType, classTypingContext);
    }

    if (result != null && !result.equals(targetType)) {
      return result;
    }

    if (result == null) {
      result = targetType.clone();
    }

    Map<String, CtTypeReference<?>> substitutionRule = TypeArgumentSubstitution.inferSubstitutionRule(baseType);

    if (result instanceof CtTypeParameterReference) {
      return substitutionRule.get(result.getSimpleName());
    }

    List<CtTypeParameterReference> typeParameterReferences = result
            .filterChildren(new TypeFilter<>(CtTypeParameterReference.class))
            .select(t -> !t.equals(targetType)).list();

    for (CtTypeParameterReference typeParameterReference : typeParameterReferences) {
      if (substitutionRule.containsKey(typeParameterReference.getSimpleName())) {
        typeParameterReference.replace(substitutionRule.get(typeParameterReference.getSimpleName()));
      } else {
        return null;
      }
    }

    return result;
  }

  private static CtTypeReference<?> applyManualAdaption(CtTypeReference<?> baseType, CtTypeReference<?> targetType,
                                                        ClassTypingContext classTypingContext) {
    // Manually find type parameter
    Optional<CtTypeParameter> targetTypeParameter =
            baseType.getTypeDeclaration().getFormalCtTypeParameters().stream()
                    .filter(t -> t.toString().equals(targetType.toString()))
                    .findFirst();
    try {
      return targetTypeParameter.map(classTypingContext::adaptType).orElse(null);
    } catch (Exception e) {
      return targetType;
    }
  }
}
