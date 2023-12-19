package npetest;

import npetest.analysis.TCClassLoader;
import npetest.analysis.executor.Executor;
import npetest.analysis.npeanalysis.NPEAnalysisManager;
import npetest.analysis.npeanalysis.PathAnalyzer;
import npetest.cli.CLIOptions;
import npetest.commons.Configs;
import npetest.commons.Configs.StoppingConditionType;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.astmodel.CtModelExt.CtModelExtBuilder;
import npetest.commons.filters.NonTestCodeFilter;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.logger.LoggingUtils;
import npetest.commons.misc.Timer;
import npetest.commons.spoon.TypeAccessibilityChecker;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import npetest.synthesizer.MutantGenerator;
import npetest.synthesizer.SeedGenerator;
import npetest.synthesizer.generators.stoppers.StoppingConditionFactory;
import npetest.synthesizer.mutation.MutationCore;
import npetest.synthesizer.result.FinalResult;
import npetest.synthesizer.result.SeedGenerationResult;
import npetest.synthesizer.search.mut.MUTSelector;
import npetest.synthesizer.search.mut.MUTSelectorFactory;
import npetest.synthesizer.search.mutation.MutationTargetSelectorFactory;
import npetest.synthesizer.seed.SeedManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtModifiable;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.PrettyPrinter;
import spoon.reflect.visitor.chain.CtQueryable;
import spoon.reflect.visitor.filter.TypeFilter;

import java.security.Permission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Command(name = "npetest", version = "1.0", mixinStandardHelpOptions = true)
public class Main extends CLIOptions {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  private Launcher launcher;

  private SeedGenerator seedGenerator;

  private MutantGenerator mutantGenerator;

  private final FinalResult finalResult = new FinalResult();

  public static void main(String[] args) {
    // internally call `generateMethodSequence` method
    CommandLine command = new CommandLine(new Main());
    int exitCode = command
            .setOptionsCaseInsensitive(true)
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);

    System.setSecurityManager(null);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    try {
      launcher = validateCommandLineOptionsAndParameters();
      validateTargetClasspath();
    } catch (Exception e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    Timer.GLOBAL_TIMER.setup(timeBudget);
    buildModel();

    setupWorkList();
    if (CtModelExt.INSTANCE.getMUTs().isEmpty()) {
      logger.info("WorkList of Testable MUTs is empty! Check input!");
      return;
    }
    LoggingUtils.logList(logger, CtModelExt.INSTANCE.getMUTs(), "Methods under test");

    prepareTestGeneration();
    generateTestCases();
  }

  private void prepareTestGeneration() {
    MUTSelector mutSelector = MUTSelectorFactory.create(Configs.MUT_SELECTION_STRATEGY);

    if (filterMUT) {      
      // refine the MUT set to only have the null returning methods.
      logger.info("* Running Static analysis...");
      for (ExecutableKey m : CtModelExt.INSTANCE.getMUTs()) {
        NPEAnalysisManager.runImpurityAnalyzer(m.getKey());
      }


      NPEAnalysisManager.runPathAnalyzer();

      float elapsedTime = Timer.GLOBAL_TIMER.getElapsedTime();
      logger.info("* Static analysis finished: {}s", elapsedTime);

      Set<ExecutableKey> tmpSet = new HashSet<>(CtModelExt.INSTANCE.getMUTs());

      for (ExecutableKey m : tmpSet) {
        if (!PathAnalyzer.getInstance().hasNPEPath(m.getKey())) {
          CtModelExt.INSTANCE.removeMUTs(m);
        }
      }
    }

    this.seedGenerator = new SeedGenerator.Builder()
            .stoppingCondition(StoppingConditionFactory.create(Configs.STOPPING_CONDITION_TYPE, mutSelector))
            .mutSelector(mutSelector)
            .build();

    this.mutantGenerator = new MutantGenerator.Builder()
            .stoppingCondition(StoppingConditionFactory.create(StoppingConditionType.TIME, mutSelector))
            .seedManager(SeedManagerFactory.create(Configs.SEED_SELECTION_STRATEGY))
            .mutationTargetSelector(MutationTargetSelectorFactory.create(Configs.TEST_CASE_MUTATION_STRATEGY))
            .build();

    MutationCore.setup(Configs.TEST_CASE_MUTATION_STRATEGY);
    setupJVMEnvironment();
    setupTestGeneration();
  }

  private void generateTestCases() {
    logger.info("* Start seed test case generation: {}s", +Timer.GLOBAL_TIMER.getElapsedTime());
    logger.info("  Remaining time budget: {}s\n", Timer.GLOBAL_TIMER.calculateRemainingTime());

    seedGenerator.setup();
    seedGenerator.run();
    SeedGenerationResult seedGenerationResult = seedGenerator.getResult();
    finalResult.addTestCases(seedGenerationResult.getUniqueResults());
    finalResult.addTestCases(seedGenerationResult.getNPETriggeringObjects());

    logger.info("* Start mutating generated test cases: {}s", + Timer.GLOBAL_TIMER.getElapsedTime());
    logger.info("  Remaining time budget: {}s\n", Timer.GLOBAL_TIMER.calculateRemainingTime());

    mutantGenerator.setup(seedGenerationResult);
    mutantGenerator.run();

    logger.info("* Time out! (Actual elapsed time: {}s)", Timer.GLOBAL_TIMER.getElapsedTime());

    finalResult.addTestCases(mutantGenerator.getResults());

    finalResult.report();
  }

  private void buildModel() {
    CtModelExt model = null;
    try {
      auxiliaryClasspath = auxiliaryClasspath.length == 1 && auxiliaryClasspath[0].equals("") ? new String[]{} : auxiliaryClasspath;
      setSpoonEnvironment(launcher, javaVersion, auxiliaryClasspath);
      Factory factory = launcher.getFactory();
      PrettyPrinter printer = autoImport
              ? launcher.getEnvironment().createPrettyPrinterAutoImport()
              : launcher.getEnvironment().createPrettyPrinter();

      ClassLoader spoonLoader = factory.getEnvironment().getInputClassLoader();

      TCClassLoader loader = TCClassLoader.init(targetClasspath, auxiliaryClasspath, spoonLoader);

      model = new CtModelExtBuilder()
              .setFactory(factory)
              .setLauncher(launcher)
              .setLoader(loader)
              .setPrinter(printer)
              .setTargetClasspath(targetClasspath)
              .setAuxiliaryClasspath(auxiliaryClasspath)
              .build();
    } catch (Exception e) {
      logger.error(e.getMessage());
      System.exit(1);
    }
    assert model != null;
    model.createTypeInheritanceGraph();
    TypeUtils.setup(model.getFactory());
    CodeFactory.setup(model.getFactory());
  }

  private void setSpoonEnvironment(Launcher launcher, int javaVersion, String[] auxiliaryClasspath) {
    Environment env = launcher.getEnvironment();
    env.setNoClasspath(true);
    env.setSourceClasspath(auxiliaryClasspath);
    env.setIgnoreDuplicateDeclarations(true);
    env.setIgnoreSyntaxErrors(true);
    env.setShouldCompile(false);
    env.setComplianceLevel(javaVersion);
    env.disableConsistencyChecks();
  }

  private void setupWorkList() {
    logger.info("* Setting up work list of MUTs... ");
    NonTestCodeFilter.INSTANCE.setup(excludeTestDirectories);
    if (uuts.cutNames != null) {
      for (String cutName : uuts.cutNames) {
        List<CtType<?>> cuts = validateCUT(cutName);
        cuts.forEach(this::addMUTsToModel);
//        cuts.forEach(this::addIndirectMUTsToModel);
      }
    } else if (uuts.methodKeys != null) {
      for (String methodKey : uuts.methodKeys) {
        validateMUT(methodKey);
      }
    } else {
      addMUTsForWholeProject();
    }
    LoggingUtils.logFinishTask(logger);
  }

  private void validateMUT(String methodKey) {
    String typeName = methodKey.split("#")[0];
    CtType<?> cut = CtModelExt.INSTANCE.getCtTypeFromModel(typeName);
    if (cut == null) return;

    String methodSignature = methodKey.split("#")[1];
    CtMethod<?> mut = validateMUTSignature(methodSignature, cut);
    if (mut == null) return;
    CtModelExt.INSTANCE.addMUT(ExecutableKey.of(mut));
  }

  private CtMethod<?> validateMUTSignature(String methodSignature, CtType<?> cut) {
    String methodName = methodSignature.substring(0, methodSignature.indexOf('('));
    List<CtMethod<?>> muts = cut.getMethodsByName(methodName).stream().filter(
            m -> m.getSignature().equals(methodSignature)).collect(Collectors.toList());
    if (muts.isEmpty()) {
      logger.error("Failed to find MUT: {}", methodSignature);
      return null;
    }

    if (muts.size() > 1) {
      /* This case cannot happen */
      logger.error("Multiple MUTs are found: {}", methodSignature);
      return null;
    }
    return muts.get(0);
  }

  private List<CtType<?>> validateCUT(String cutName) {
    CtType<?> cut = CtModelExt.INSTANCE.getCtTypeFromModel(cutName);
    if (cut == null) {
      logger.error("* Unknown input class: {}", cutName);
      return new ArrayList<>();
    }

    boolean isAbstract = cut.isInterface() || cut.isAbstract();
    List<CtType<?>> effectiveCUTs = isAbstract ? CtModelExt.INSTANCE.findConcreteSubtypes(cut) : new ArrayList<>();
    effectiveCUTs.add(cut);

    for (CtType<?> effectiveCUT : effectiveCUTs) {
      CtModelExt.INSTANCE.addCUT(effectiveCUT);
    }

    return effectiveCUTs;
  }

  private void addMUTsToModel(CtType<?> cut) {
    addMUTsFromCtQueryable(cut);
  }

  private void addMUTsFromCtQueryable(CtQueryable queryRoot) {
    List<CtMethod<?>> muts = queryRoot.filterChildren(new TypeFilter<>(CtMethod.class))
            .select((CtMethod<?> m) -> m.getParent(CtPackage.class) != null)
            .select((CtMethod<?> m) -> !m.isPrivate())
//            .select((CtMethod<?> m) -> !SimpleOverloadingExecutableFilter.INSTANCE.matches(m))
//            .select((CtMethod<?> m) -> !m.getDeclaringType().getQualifiedName().equals(m.getType().getQualifiedName())
//                    && !(m.getDeclaringType().isSubtypeOf(m.getType()) &&
//                    !(m.getType().getTypeDeclaration() == null || m.getType().getTypeDeclaration().isShadow())))
            .select((CtMethod<?> m) -> m.getDeclaringType().isTopLevel())
            .select((CtMethod<?> m) -> {
              CtType<?> declaringType = m.getDeclaringType();
              while (declaringType != null) {
                if (declaringType.isPrivate()) {
                  return false;
                }
                declaringType = declaringType.getDeclaringType();
              }
              return true;
            })
            .list();

    List<CtMethod<?>> targetMethods = muts.stream()
            .filter(NonTestCodeFilter.INSTANCE)
//            .filter(DereferenceExistenceFilter.INSTANCE)
            .collect(Collectors.toList());

//    List<CtMethod<?>> publicMethods = targetMethods.stream()
//            .filter(CtModifiable::isPublic)
//            .collect(Collectors.toList());
//    if (!publicMethods.isEmpty()) {
//      publicMethods.forEach(m -> CtModelExt.INSTANCE.addMUT(ExecutableKey.of(m)));
//    } else {

    targetMethods.forEach(m -> CtModelExt.INSTANCE.addMUT(ExecutableKey.of(m)));
//    }
  }

  private void addIndirectMUTsToModel(CtType<?> cut) {
    if (TypeAccessibilityChecker.withoutPublicConstructor(cut) && !cut.isAbstract()) {
      CtPackage ctPackage = cut.getParent(CtPackage.class);
      List<CtMethod<?>> callingMethods = ctPackage.filterChildren(new TypeFilter<>(CtInvocation.class))
              .select((CtInvocation<?> inv) -> inv.getTarget() instanceof CtVariableAccess<?>
                      && inv.getTarget().getType().getQualifiedName().equals(cut.getQualifiedName()))
              .select((CtInvocation<?> inv) -> inv.getParent(CtMethod.class) != null)
              .map((CtInvocation<?> inv) -> inv.getParent(CtMethod.class))
              .select((CtMethod<?> m) -> TypeAccessibilityChecker.isGloballyAccessible(m.getDeclaringType()))
              .select((CtMethod<?> m) -> m.getDeclaringType().getPackage().getQualifiedName().equals(ctPackage.getQualifiedName()))
              .list();

      for (CtMethod<?> callingMethod : callingMethods) {
        if (!callingMethod.isPrivate()) {
          CtModelExt.INSTANCE.addMUT(ExecutableKey.of(callingMethod));
        } else {
          CtClass<?> declaringType = callingMethod.getParent(CtClass.class);
          List<CtMethod<?>> publicCallingMethods = declaringType.filterChildren(new TypeFilter<>(CtInvocation.class))
                  .select((CtInvocation<?> inv) -> inv.getExecutable().getDeclaringType().getQualifiedName()
                          .equals(declaringType.getQualifiedName()))
                  .select((CtInvocation<?> inv) -> inv.getExecutable() != null &&
                          ExecutableKey.of(inv.getExecutable()).equals(ExecutableKey.of(callingMethod)))
                  .select((CtInvocation<?> inv) -> inv.getParent(CtMethod.class) != null)
                  .map((CtInvocation<?> inv) -> inv.getParent(CtMethod.class))
                  .select(CtModifiable::isPublic)
                  .select((CtMethod<?> m) -> TypeAccessibilityChecker.isGloballyAccessible(m.getDeclaringType()))
                  .list();
          publicCallingMethods.forEach(m -> CtModelExt.INSTANCE.addMUT(ExecutableKey.of(m)));
        }
      }
    }
  }

  private void addMUTsForWholeProject() {
    addMUTsFromCtQueryable(CtModelExt.INSTANCE.getCtModel());
  }

  public void setupJVMEnvironment() {
    setupSecurityManager();
  }

  private void setupSecurityManager() {
    System.setSecurityManager(new SecurityManager() {
      @Override
      public void checkExit(int status) {
        throw new SecurityException();
      }

      @Override
      public void checkPermission(Permission perm) {
        if ("exitVM".equals(perm.getName())) {
          throw new SecurityException();
        }
      }
    });
  }

  public void setupTestGeneration() {
    Executor.setup();
  }
}
