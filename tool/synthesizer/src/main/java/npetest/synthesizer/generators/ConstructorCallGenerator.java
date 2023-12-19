package npetest.synthesizer.generators;

import npetest.language.CodeFactory;
import npetest.language.VariableType;
import npetest.language.sequence.Sequence;
import npetest.synthesizer.context.GenerationHistory;
import npetest.synthesizer.context.InvocationGenerationContext;
import npetest.synthesizer.typeadaption.TypeConcretizer;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class ConstructorCallGenerator {

  public static Sequence generateObjectWithConstructor(CtTypeReference<?> declType, CtTypeReference<?> instanceType,
                                                       CtConstructor<?> constructor) {
    GenerationHistory.updateUsedGenerators(constructor);
    InvocationGenerationContext.add(constructor);
    CtConstructorCall<?> constructorCall = CodeFactory.createConstructorCall(constructor, instanceType);
    List<CtTypeReference<?>> methodTypeArguments =
            TypeConcretizer.setupTypeArgumentsOfExecutable(constructor);
    List<VariableType> inputTypes = TypeConcretizer.setupInputTypes(constructor, constructorCall,
            instanceType, methodTypeArguments);
    List<CtExpression<?>> arguments = new ArrayList<>();
    CtStatementList result = CodeFactory.createStatementList();
    for (VariableType variableType : inputTypes) {
      Sequence argumentCreationSequence = ObjectInstantiator.instantiate(variableType, false);
      if (!argumentCreationSequence.isInlineValue()) {
        for (CtStatement ctStatement : argumentCreationSequence.getCtStatementList()) {
          result.addStatement(ctStatement);
        }
      }
      CtExpression<?> argument = argumentCreationSequence.getAccessExpression();
      arguments.add(argument);
    }

    InvocationGenerationContext.remove(constructor);

    constructorCall.setArguments(arguments);
    CtLocalVariable<?> objectDeclaration = CodeFactory.createNewLocalVariable(declType, constructorCall);
    result.addStatement(objectDeclaration);
    return new Sequence(result);
  }
}
