package npetest.analysis.compiler;

import java.net.URI;
import javax.tools.SimpleJavaFileObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaSourceCode extends SimpleJavaFileObject {
  private static final Logger logger = LoggerFactory.getLogger(JavaSourceCode.class);
  private final String packageName;

  private final String stubName;

  private String charContent;

  public JavaSourceCode(Builder builder) {
    super(builder.uri, Kind.SOURCE);
    this.packageName = builder.packageName;
    this.stubName = builder.stubName;
  }

  public String getCode() {
    return charContent;
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) {
    return charContent;
  }

  public String getQualifiedClassName() {
    return String.format("%s.%s", packageName, stubName);
  }

  public void setupCode(String sequenceString) {
    this.charContent = "package " + packageName + ";\n" +
            "public class " + stubName + " { \n" +
            "  public void test() throws Exception { \n" +
            sequenceString +
            "  }\n" +
            "}\n";
  }

  public static class Builder {
    private URI uri;

    private String stubName;

    private String packageName;

    public JavaSourceCode build() {
      return new JavaSourceCode(this);
    }

    public Builder uri(URI uri) {
      this.uri = uri;
      return this;
    }

    public Builder stubName(String stubName) {
      this.stubName = stubName;
      return this;
    }

    public Builder packageName(String packageName) {
      this.packageName = packageName;
      return this;
    }
  }
}
