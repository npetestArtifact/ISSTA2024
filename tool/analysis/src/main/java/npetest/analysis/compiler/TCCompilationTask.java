package npetest.analysis.compiler;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import javax.tools.*;
import javax.tools.JavaFileObject.Kind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TCCompilationTask {
  private static final Logger logger = LoggerFactory.getLogger(TCCompilationTask.class);

  private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

  private final DiagnosticCollector<? super JavaFileObject> diagnostics;

  private final JavaCompiler.CompilationTask compilationTask;

  private final JavaFileManager fileManager;

  public TCCompilationTask(JavaSourceCode javaSourceCode, JavaByteCode javaByteCode, List<String> options) {
    this.diagnostics = new DiagnosticCollector<>();
    this.fileManager = createFileManager(javaByteCode);
    this.compilationTask = compiler.getTask(
            new StringWriter(), fileManager, diagnostics, options, null, Collections.singletonList(javaSourceCode));
  }

  public boolean compile() {
    return compilationTask.call();
  }

  public JavaFileManager createFileManager(JavaByteCode byteCode) {
    StandardJavaFileManager standardJavaFileManager = getStandardJavaFileManager(diagnostics);
    return new ForwardingJavaFileManager<StandardJavaFileManager>(standardJavaFileManager) {
      @Override
      public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) {
        return byteCode;
      }
    };
  }

  private StandardJavaFileManager getStandardJavaFileManager(
          DiagnosticListener<? super JavaFileObject> diagnosticListener) {
    return compiler.getStandardFileManager(diagnosticListener, null, null);
  }

  public List<? extends Diagnostic<?>> getDiagnostics() {
    return diagnostics.getDiagnostics();
  }

  public void terminate() {
    try {
      fileManager.close();
    } catch (IOException e) {
      logger.info("exception occur in closing JavaFileManager");
    }
  }
}
