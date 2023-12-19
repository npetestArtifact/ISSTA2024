package npetest.synthesizer.search.mut;

import npetest.analysis.dynamicanalysis.MethodTrace;
import npetest.analysis.npeanalysis.PathAnalyzer;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.keys.ParameterKey;
import npetest.commons.misc.RandomUtils;
import npetest.commons.misc.WeightedCollection;
import npetest.synthesizer.context.InvocationGenerationContext;
import npetest.synthesizer.context.TestGenContext;
import npetest.synthesizer.result.TestEvaluator;
import spoon.reflect.declaration.CtExecutable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GuidedMUTSelector extends MUTSelector {
  private final Set<ExecutableKey> selectedMethodSet = new HashSet<>();
  @Override
  public ExecutableKey choose() {

    Set<ExecutableKey> unsaturatedMUTs = new HashSet<>();
    for (ExecutableKey mutKey : muts) {
      CtExecutable<?> mut = mutKey.getCtElement();
      int size = mut.getParameters().size();
      int wholeSpace = 2;
      for (int i = 0; i < size; i++) {
        ParameterKey paramKey = ParameterKey.of(mut, i);
        Integer paramSpace = InvocationGenerationContext.parameterTypeSpaces.getOrDefault(paramKey, 0);
        wholeSpace *= paramSpace;
      }

      Integer generatedCount = TestGenContext.generationCount.getOrDefault(mutKey, 0);
      if (generatedCount <= wholeSpace) {
        unsaturatedMUTs.add(mutKey);
      }
    }

    Map<ExecutableKey, Float> reachableMethodScores = unsaturatedMUTs.stream()
            .collect(Collectors.toMap(m -> m,
                    m -> {
                      return PathAnalyzer.getInstance().getScore(m.getKey());
                    }));
                    

    WeightedCollection<ExecutableKey> wc = new WeightedCollection<>(reachableMethodScores);
    
    return wc.next();
    
    // Set<ExecutableKey> unsaturatedMUTs = new HashSet<>();
    // for (ExecutableKey mutKey : muts) {
    //   CtExecutable<?> mut = mutKey.getCtElement();
    //   int size = mut.getParameters().size();
    //   int wholeSpace = 2;
    //   for (int i = 0; i < size; i++) {
    //     ParameterKey paramKey = ParameterKey.of(mut, i);
    //     Integer paramSpace = InvocationGenerationContext.parameterTypeSpaces.getOrDefault(paramKey, 0);
    //     wholeSpace *= paramSpace;
    //   }

    //   Integer generatedCount = TestGenContext.generationCount.getOrDefault(mutKey, 0);
    //   if (generatedCount <= wholeSpace) {
    //     unsaturatedMUTs.add(mutKey);
    //   }
    // }


    // return RandomUtils.select(unsaturatedMUTs);
    
    // Set<ExecutableKey> unselectedMethods = muts.stream()
    //         .filter(m -> !selectedMethodSet.contains(m))
    //         .collect(Collectors.toSet());
    // ExecutableKey select = RandomUtils.select(unselectedMethods);
    // if (select != null) {
    //   selectedMethodSet.add(select);
    //   return select;
    // }
    
    // Map<ExecutableKey, Float> reachableMethodScores = muts.stream()
    //         .collect(Collectors.toMap(m -> m,
    //                 m -> {
    //                   Set<ExecutableKey> reachableMethods = MethodTrace.getInstance().getReachableMethods(m);
    //                   return reachableMethods != null
    //                           ? TestEvaluator.calculateNpePathScore(reachableMethods)
    //                           : 0;
    //                 }));
                    

    // WeightedCollection<ExecutableKey> wc = new WeightedCollection<>(reachableMethodScores);
    // return wc.next();
  }
}
