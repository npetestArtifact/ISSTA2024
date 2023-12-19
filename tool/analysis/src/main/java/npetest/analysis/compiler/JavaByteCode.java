package npetest.analysis.compiler;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import javax.tools.SimpleJavaFileObject;

public class JavaByteCode extends SimpleJavaFileObject {
  private final ByteArrayOutputStream outputStream;

  public JavaByteCode() {
    super(URI.create("string:///" + "Test" + Kind.CLASS.extension), Kind.CLASS);
    outputStream = new ByteArrayOutputStream();
  }

  @Override
  public OutputStream openOutputStream() {
    return outputStream;
  }

  public byte[] getBytes() {
    return outputStream.toByteArray();
  }
}
