package npetest.analysis.executor;

import npetest.analysis.compiler.JavaSourceCode;
import npetest.analysis.complexityanalysis.ComplexityAnalyzer;
import npetest.analysis.dynamicanalysis.ActualRuntimeType;
import npetest.analysis.dynamicanalysis.DynamicInformation;
import npetest.analysis.dynamicanalysis.MethodTrace;
import npetest.analysis.npeanalysis.NPEAnalysisManager;
import npetest.commons.Configs;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.logger.LoggingConfiguration;
import npetest.commons.spoon.TypeUtils;
import npetest.language.metadata.ExecutionResult;
import npetest.language.sequence.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtShadowable;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;

import javax.tools.JavaFileObject.Kind;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class ExecutionHelper {
  private static final Logger logger = LoggerFactory.getLogger(ExecutionHelper.class);

  public static final ExecutionHelper INSTANCE = new ExecutionHelper();

  public static final int TEST_TIMEOUT = 5000;

  private final PrintStream dummyStream = new PrintStream(new ByteArrayOutputStream());

  private PrintStream originalStdOut;

  private PrintStream originalStdErr;

  private ExecutionHelper() {
  }

  private static final Map<String, Integer> objectExecutionCounts = new HashMap<>();

  private static int tcCount = 0;

  public ExecutionResult runObjectSequence(CtPackage ctPackage, CtStatementList statements,
                                           CtTypeReference<?> instanceType) {
    logger.debug("Running object sequence({})", instanceType);
    if (TypeUtils.isNull(instanceType)) {
      return ExecutionResult.ofNormalExecution();
    }
    String objectStubName = getObjectStubName(instanceType);
    DynamicInformation.prepareRunningObjectSequence();
    return runSequence(ctPackage, statements, objectStubName);
  }

  public ExecutionResult runTestCase(CtPackage ctPackage, TestCase testCase) {
    String testStubName = getTestStubName();
    DynamicInformation.prepareRunningTestCase();
    return runSequence(ctPackage, testCase.getCtStatements(), testStubName);
  }


  public ExecutionResult runSequence(CtPackage ctPackage, CtStatementList statements, String stubName) {
    if (statements == null) {
      return ExecutionResult.ofNormalExecution();
    }
    String packageName = ctPackage.getQualifiedName();
    URI uri = URI.create("string:///" + stubName + Kind.SOURCE.extension);

    JavaSourceCode stubFile = new JavaSourceCode.Builder()
            .packageName(packageName)
            .stubName(stubName)
            .uri(uri)
            .build();

    stubFile.setupCode(getSequenceString(statements));

    // disable logging from SUT
    if (!LoggingConfiguration.isLogLevelDebug()) {
      LoggingConfiguration.disable();
      turnOffSystemOutAndErr();
    }

    Executor.ExecutionTask executionTask = Executor.getTask(stubFile);
    ExecutorService service = Executors.newSingleThreadExecutor();
    try {

      // Execute...
      service.submit(executionTask).get(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

    } catch (ExecutionException | TimeoutException e) {
      // Failed to execute tests
      logger.debug("**** Failed to execute tests!");
    } catch (InterruptedException e) {
      // Unexpected scenario where the thread is interrupted
      logger.debug("**** Failed to execute tests!");
      Thread.currentThread().interrupt();
    }

     turnOnSystemOutAndErr();
    // enable logging from SUT
    LoggingConfiguration.enable();

    // on-demand static analysis
    Set<ExecutableKey> calledMethodsSet = MethodTrace.getInstance().getCalledMethodsSet();

    calledMethodsSet.stream().filter(e -> !((CtShadowable) e.getCtElement()).isShadow())
            .map(ExecutableKey::toString)
            .forEach(m -> {
              NPEAnalysisManager.runAnalyzers(m);
              ComplexityAnalyzer.getInstance().analyze(m);
            });

    ExecutionResult result = executionTask.getResult();
    logger.debug("- Execution result: {}", result.getSummary());
    return result;
  }

  private String getSequenceString(CtStatementList statements) {
    StringBuilder builder = new StringBuilder();
    int size = statements.getStatements().size();
    for (int i = 0; i < size; i++) {
      appendMethodTraceInspection(builder);
      CtStatement statement = statements.getStatement(i);
      builder.append(unescapeStatement(statement.toString())).append("; ");
      appendRuntimeTypeInspection(builder, statement);
      builder.append("\n");
    }
    return builder.toString();
  }

  public String unescapeStatement(String statement) {
    return statement.replace("\n", "\\n")
            .replace("\t", "\\t")
            .replace("\r", "\\r")
            .replace("\b", "\\b")
            .replace("\f", "\\f");
  }

  private void appendMethodTraceInspection(StringBuilder builder) {
    builder.append("npetest.analysis.dynamicanalysis.MethodTrace.getInstance().incrementLine(); ");
  }

  private static void appendRuntimeTypeInspection(StringBuilder builder, CtStatement statement) {
    if (statement instanceof CtLocalVariable<?> &&
            !TypeUtils.isPrimitive(((CtLocalVariable<?>) statement).getType()) &&
            !TypeUtils.isBoxingType(((CtLocalVariable<?>) statement).getType()) &&
            !TypeUtils.isString(((CtLocalVariable<?>) statement).getType()) &&
            ActualRuntimeType.getInstance().isEnabled()) {
      String variableName = ((CtLocalVariable<?>) statement).getSimpleName();
      builder.append(String.format(
              "if (%s != null) { npetest.analysis.dynamicanalysis.ActualRuntimeType.getInstance().updateCreatedType" +
                      "(\"%s\", %s.getClass()); }",
              variableName, variableName, variableName));
    }
  }

  private void turnOffSystemOutAndErr() {
    originalStdOut = System.out;
    originalStdErr = System.err;
    System.out.flush();
    System.err.flush();
    System.setOut(dummyStream);
    System.setErr(dummyStream);
  }

  private void turnOnSystemOutAndErr() {
    dummyStream.flush();
    System.setOut(originalStdOut);
    System.setErr(originalStdErr);
    originalStdOut = null;
    originalStdErr = null;
  }

  private static String getTestStubName() {
    return Configs.RUNTIME_TC_PREFIX + tcCount++;
  }

  private static String getObjectStubName(CtTypeReference<?> instanceType) {
    String simpleName;
    if (instanceType.isArray()) {
      simpleName = String.format("%s%s", ((CtArrayTypeReference<?>) instanceType).getComponentType().getSimpleName(),
              "Array");
    } else if (instanceType.isAnonymous()) {
      String qualifiedName = instanceType.getQualifiedName();
      String[] anonymousSplit = qualifiedName.split("\\$");
      String[] typeNameSplit = anonymousSplit[0].split("\\.");
      simpleName = String.format("%s_%s", typeNameSplit[typeNameSplit.length - 1],
              anonymousSplit[anonymousSplit.length - 1]);
    } else {
      simpleName = instanceType.getSimpleName();
    }

    int cnt = objectExecutionCounts.getOrDefault(simpleName, 0);
    String wrappingClassName = Configs.RUNTIME_OBJ_PREFIX + simpleName + cnt++;
    objectExecutionCounts.put(simpleName, cnt);
    return wrappingClassName;
  }
}
