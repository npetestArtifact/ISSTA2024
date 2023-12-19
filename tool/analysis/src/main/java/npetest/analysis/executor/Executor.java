package npetest.analysis.executor;

import npetest.analysis.TCClassLoader;
import npetest.analysis.compiler.JavaByteCode;
import npetest.analysis.compiler.JavaSourceCode;
import npetest.analysis.compiler.TCCompilationTask;
import npetest.commons.astmodel.CtModelExt;
import npetest.commons.misc.Debugger;
import npetest.language.metadata.ExecutionResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Executor {
  private static final Logger logger = LoggerFactory.getLogger(Executor.class);
  private Executor() {}

  private static final List<String> options = new ArrayList<>();

  public static ExecutionTask getTask(JavaSourceCode stubFile) {
    return new ExecutionTask(stubFile, options);
  }

  public static void setup() {
    setupClassPaths();
  }

  private static void setupClassPaths() {
    options.add("-classpath");
    StringBuilder builder = new StringBuilder(CtModelExt.INSTANCE.getTargetClasspath()).append(':');
    for (String s : CtModelExt.INSTANCE.getAuxiliaryClasspath()) {
      builder.append(s).append(":");
    }
    Arrays.stream(System.getProperty("java.class.path").split(":"))
            .filter(s -> s.contains("npetest"))
            .forEach(s -> builder.append(s).append(':'));
    String classPaths = builder.toString();
    options.add(classPaths.substring(0, classPaths.length() - 1));
  }

  static class ExecutionTask implements Runnable {
    private final JavaSourceCode javaSourceCode;

    private final JavaByteCode javaByteCode;

    private final List<String> options;

    private volatile boolean executionFinished;

    private ExecutionResult result = null;

    public ExecutionTask(JavaSourceCode stubFile, List<String> options) {
      this.javaSourceCode = stubFile;
      this.javaByteCode = new JavaByteCode();
      this.options = options;
      this.executionFinished = false;
    }

    @Override
    public void run() {
      TCCompilationTask compilationTask = new TCCompilationTask(javaSourceCode, javaByteCode, options);
      boolean compileResult = compilationTask.compile();

      if (compileResult) {
        // Compile Success
        try {
          

          Class<?> testClazz = ((TCClassLoader) CtModelExt.INSTANCE.getMainLoader())
                  .defineTestClass(javaSourceCode, javaByteCode);


          if (testClazz == null) {
            this.result = ExecutionResult.ofExecutionFailure();


          } else {


            @SuppressWarnings("deprecation") Object object = testClazz.newInstance();


            Method method = testClazz.getMethod("test");          


            method.invoke(testClazz.cast(object));


            this.result = ExecutionResult.ofNormalExecution();

          }
        } catch (InvocationTargetException e) {
          // The fault occur during test execution
          this.result = ExecutionResult.fromCrash(e.getCause());
        } catch (Exception e) {
          // Mustn't happen
          this.result = ExecutionResult.ofExecutionFailure();
        }

        this.executionFinished = true;
        compilationTask.terminate();
        
      } else {

        Debugger.logCompileFailure(javaSourceCode.getCode(), compilationTask.getDiagnostics());
        this.result = ExecutionResult.ofCompileFailure();
      }
    }



    public ExecutionResult getResult() {
      return this.result == null ? ExecutionResult.ofExecutionFailure() : this.result;
    }
  }
}
