package npetest.synthesizer.result;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import npetest.analysis.executor.ExecutionHelper;
import npetest.language.CodeFactory;
import npetest.language.metadata.ExecutionResult;
import npetest.language.sequence.TestCase;
import npetest.commons.Configs;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.spoon.TypeUtils;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;

public class TestSuite {
  private final CtCompilationUnit ctCompilationUnit;

  private final CtClass<?> ctClass;

  private final CtType<?> mainClass;

  private final List<TestCase> testCases = new ArrayList<>();

  private final String filename;

  private File file;

  private String packagePath;

  public TestSuite(CtType<?> mainClass, int index, boolean isSeed) {
    this.mainClass = mainClass;
    String suffix = Configs.TESTS_PER_SUITE == 0 ? "" : "_" + index;
    ctClass = CodeFactory.createClass(String.format("%s_%s%s", mainClass.getSimpleName(),
            isSeed ? Configs.SEED_TEST_SUFFIX : Configs.MUTANT_TEST_SUFFIX, suffix));
    ctClass.addModifier(ModifierKind.PUBLIC);
    filename = String.format("%s.java", ctClass.getSimpleName());
    ctCompilationUnit = CodeFactory.createCompilationUnit();
    ctCompilationUnit.setDeclaredPackage(mainClass.getPosition().getCompilationUnit().getDeclaredPackage());
  }

  public void addResultTC(TestCase testCase) {
    testCases.add(testCase);
  }

  public void buildCtCompilationUnit() throws IOException {
    int digit = String.valueOf(testCases.size()).length();
    DecimalFormat df = new DecimalFormat(
            new String(new char[digit]).replace("\0", "0"));
    int i = 0;
    for (TestCase testCase : testCases) {
      if(Configs.WRITE_EXCEPTION_TESTS_ONLY) {
        if(testCase.getResult().getSummary() != ExecutionResult.ExecutionSummary.NPE &&
        testCase.getResult().getSummary() != ExecutionResult.ExecutionSummary.CRASH) {
          continue;
        }
      }

      CtMethod<?> testMethod = buildTestMethod(testCase);
      if (Configs.PRINT_COMMENT) {
        String executionSummaryComment = CommentUtils.createExecutionComment(testCase.getResult());
        testMethod.addComment(CodeFactory.createComment(executionSummaryComment));
        String metadataComment = CommentUtils.createMetaDataComment(testCase);
        metadataComment += "\nScore     : " + testCase.getScore();
        testMethod.addComment(CodeFactory.createComment(metadataComment));
      }
      ctClass.addMethod(testMethod);
      String methodName = "test" + df.format(i++);
      testMethod.setSimpleName(methodName);
      testCase.setCanonicalPathName(getFile().getPath() + "#" + methodName);
    }
    mainClass.getPosition().getCompilationUnit().getDeclaredPackage().addType(ctClass);
    ctCompilationUnit.addDeclaredType(ctClass);
  }

  private CtMethod<?> buildTestMethod(TestCase testCase) {
    CtStatementList statements = testCase.getCtStatements();
    CtMethod<?> ctMethod = CtModelExt.INSTANCE.getFactory().createMethod();
    ctMethod.setValueByRole(CtRole.TYPE, TypeUtils.voidPrimitive());
    ctMethod.addModifier(ModifierKind.PUBLIC);
    Set<CtTypeReference<?>> thrownTypes = new HashSet<>();
    thrownTypes.add(CtModelExt.INSTANCE.getFactory().createSimplyQualifiedReference("java.lang.Exception"));
    ctMethod.setValueByRole(CtRole.THROWN, thrownTypes);

    CtTypeReference<?> testAnnotationType = CodeFactory.createTypeReference(Configs.ANNOTATION);
    CtAnnotation<?> testAnnotation = CtModelExt.INSTANCE.getFactory().createAnnotation();
    testAnnotation.addValue("timeout", ExecutionHelper.TEST_TIMEOUT);
    testAnnotation.setValueByRole(CtRole.ANNOTATION_TYPE, testAnnotationType);
    ctMethod.addAnnotation(testAnnotation);

    CtBlock<?> ctBlock = CtModelExt.INSTANCE.getFactory().createBlock();
    ctMethod.setBody(ctBlock);

    statements.forEach(ctBlock::addStatement);
    return ctMethod;
  }

  public String toString() {
    try {
      return CtModelExt.INSTANCE.getPrinter().prettyprint(ctCompilationUnit);
    } catch (Exception e) {
      StringBuilder builder = new StringBuilder();
      for (StackTraceElement stackTraceElement : e.getStackTrace()) {
        builder.append(stackTraceElement).append('\n');
      }
      return "Failed to print testSuite by SpoonException\n" + builder;
    }
  }

  public void setPackagePath() {
    CtPackage declaredPackage = mainClass.getPosition().getCompilationUnit().getDeclaredPackage();
    this.packagePath = declaredPackage.getQualifiedName().replace(".", "/");
  }

  public void setFile() {
    this.file = Paths.get(Configs.TEST_DIRECTORY, packagePath, filename).toFile();
    ctCompilationUnit.setFile(file);
  }

  public File getFile() {
    return file;
  }

  public int size() {
    return testCases.size();
  }
}
