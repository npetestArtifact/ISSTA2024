package npetest.synthesizer.generators;

import npetest.commons.Configs;
import npetest.commons.misc.RandomUtils;
import npetest.language.CodeFactory;
import npetest.language.VariableType;
import npetest.language.sequence.Sequence;
import spoon.reflect.code.*;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class ArrayGenerator {
  private ArrayGenerator() {
  }

  public static Sequence generatePrimitiveArray(CtArrayTypeReference<?> arrayType) {
    int length = RandomUtils.random.nextInt(Configs.MAX_ARRAY_LENGTH + 1);
    CtNewArray<?> newArray = CodeFactory.createNewArray(arrayType);
    CtTypeReference<?> elementType = arrayType.getComponentType();
    List<CtExpression<?>> arrayElements = new ArrayList<>();
    for (int i = 0; i < length; i++) {
      arrayElements.add(PrimitiveExpressionGenerator.createRandomPrimitiveExpression(elementType));
    }
    CodeFactory.setArrayElements(newArray, arrayElements);
    CtLocalVariable<?> newLocalVariable = CodeFactory.createNewLocalVariable(newArray);
    return new Sequence(newLocalVariable);
  }

  public static Sequence generateNonPrimitiveArray(CtArrayTypeReference<?> arrayType) {
    int length = RandomUtils.random.nextInt(Configs.MAX_ARRAY_LENGTH) + 1;
    CtNewArray<?> newArray = CodeFactory.createNewArray(arrayType);
    CtTypeReference<?> elementType = arrayType.getComponentType().clone();
    elementType.setActualTypeArguments(new ArrayList<>());
    List<CtExpression<?>> elementReferences = new ArrayList<>();
    CtStatementList result = CodeFactory.createStatementList();
    for (int i = 0; i < length; i++) {
      Sequence elementCreationSequence = ObjectInstantiator.instantiate(VariableType.fromInstanceType(elementType), false);
      if (!elementCreationSequence.isInlineValue()) {
        for (CtStatement statement : elementCreationSequence.getCtStatementList()) {
          result.addStatement(statement);
        }
      }
      CtExpression<?> elementReference = elementCreationSequence.getAccessExpression();
      elementReferences.add(elementReference);
    }
    CodeFactory.setArrayElements(newArray, elementReferences);
    CtLocalVariable<?> localVariable = CodeFactory.createNewLocalVariable(newArray);
    result.addStatement(localVariable);
    return new Sequence(result);
  }

}