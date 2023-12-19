package npetest.synthesizer.search.mut;

import npetest.commons.keys.ExecutableKey;
import npetest.commons.keys.ParameterKey;
import npetest.commons.misc.RandomUtils;
import npetest.synthesizer.context.InvocationGenerationContext;
import npetest.synthesizer.context.TestGenContext;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtExecutable;

import java.util.HashSet;
import java.util.Set;

public class DefaultMUTSelector extends MUTSelector {
  private Set<ExecutableKey> selectedMethodSet;

  @Override
  public ExecutableKey choose() {

    return RandomUtils.select(muts);

  //   Set<ExecutableKey> unsaturatedMUTs = new HashSet<>();
  //   for (ExecutableKey mutKey : muts) {
  //     CtExecutable<?> mut = mutKey.getCtElement();
  //     int size = mut.getParameters().size();
  //     int wholeSpace = 2;
  //     for (int i = 0; i < size; i++) {
  //       ParameterKey paramKey = ParameterKey.of(mut, i);
  //       Integer paramSpace = InvocationGenerationContext.parameterTypeSpaces.getOrDefault(paramKey, 0);
  //       wholeSpace *= paramSpace;
  //     }

  //     Integer generatedCount = TestGenContext.generationCount.getOrDefault(mutKey, 0);
  //     if (generatedCount <= wholeSpace) {
  //       unsaturatedMUTs.add(mutKey);
  //     }
  //   }
  //   return RandomUtils.select(unsaturatedMUTs);
  
  }  

}
