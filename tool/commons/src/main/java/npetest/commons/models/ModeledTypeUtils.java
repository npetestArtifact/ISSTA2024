package npetest.commons.models;

import npetest.commons.Configs;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.keys.ExecutableKey;
import npetest.commons.misc.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ModeledTypeUtils {
  private static final Logger logger = LoggerFactory.getLogger(ModeledTypeUtils.class);

  private static int i = 0;

  public static void postProcess(ModeledType<?> modeledType, CtExecutable<?> generator,
                                 CtStatementList ctStatementList) throws IOException {
    String qualifiedName = modeledType.getQualifiedName();
    switch (qualifiedName) {
      case "java.io.File":
        prepareFile(generator, ctStatementList);
        break;
      case "java.net.InetAddress":
        replaceAddress(generator, ctStatementList);
        break;
      default:
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static void prepareFile(CtExecutable<?> generator, CtStatementList ctStatementList) throws IOException {
    File fileObjectsDir = new File(Configs.OUTPUT_DIR + "/npetest_file_objects");
    if (fileObjectsDir.exists() && i == 0) {
      FileUtils.deleteRecursively(fileObjectsDir);
    }

    if (!fileObjectsDir.exists() && !fileObjectsDir.mkdirs()) {
      logger.debug("Failed to create directory {}", fileObjectsDir.getAbsolutePath());
      return;
    }

    fileObjectsDir.mkdirs();
    String filePath = String.format("%s/file%d", fileObjectsDir.getAbsolutePath(), i++);
    boolean created = new File(filePath).createNewFile();
    if (!created) {
      logger.debug("Failed to create dummy file {}", filePath);
      return;
    }

    List<CtInvocation<?>> ctInvocations = ctStatementList.getLastStatement()
            .filterChildren(new TypeFilter<>(CtConstructorCall.class))
            .select((CtConstructorCall<?> inv) -> inv.getExecutable() != null &&
                    ExecutableKey.of(inv.getExecutable()).equals(ExecutableKey.of(generator)))
            .list();

    if (ctInvocations.size() != 1) {
      logger.error("Failed to replace dummy file name for File constructor call");
      return;
    }

    CtInvocation<?> ctInvocation = ctInvocations.get(0);
    CtExpression<?> ctExpression = ctInvocation.getArguments().get(0);
    ctExpression.replace(CtModelExt.INSTANCE.getFactory().createLiteral(filePath));
  }

  private static void replaceAddress(CtExecutable<?> generator, CtStatementList ctStatementList) {
    List<CtInvocation<?>> invocations = ctStatementList.getLastStatement()
            .filterChildren(new TypeFilter<>(CtInvocation.class))
            .select((CtInvocation<?> inv) -> inv.getExecutable() != null &&
                    ExecutableKey.of(inv.getExecutable()).equals(ExecutableKey.of(generator)))
            .list();

    if (invocations.size() != 1) {
      logger.error("Failed to replace ip address for InetAddress factory method call");
      return;
    }

    CtInvocation<?> ctInvocation = invocations.get(0);
    CtExpression<?> ctExpression = ctInvocation.getArguments().get(0);
    ctExpression.replace(CtModelExt.INSTANCE.getFactory().createLiteral("127.0.0.1"));
  }
}
