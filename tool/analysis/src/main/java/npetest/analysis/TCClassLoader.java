package npetest.analysis;

import npetest.analysis.compiler.JavaByteCode;
import npetest.analysis.compiler.JavaSourceCode;
import npetest.analysis.dynamicanalysis.MethodTrace;
import npetest.analysis.instrument.ClassInstrumentation;
import npetest.commons.exceptions.UnexpectedFailure;
import npetest.commons.logger.LoggingUtils;
import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TCClassLoader extends URLClassLoader {
  private static final Logger logger = LoggerFactory.getLogger(TCClassLoader.class);

  // private ClassPool classPool_copy = new ClassPool();
  private final ClassPool classPool = new ClassPool();

  private final Map<String, Class<?>> loadedClasses = new java.util.HashMap<>();

  private TCClassLoader(URL[] urls, ClassLoader spoonLoader) {
    super(urls, spoonLoader);
    try {

      ClassInstrumentation.url = urls[0].getPath().toString();

      
      this.classPool.appendClassPath(urls[0].getPath());

      for (URL url : ((URLClassLoader) spoonLoader).getURLs()) {
        this.classPool.appendClassPath(url.getPath());
      }

      this.classPool.appendClassPath(new ClassClassPath(MethodTrace.getInstance().getClass()));


    } catch (NotFoundException e) {
      logger.error("* Abort!! Failed to setup TCClassLoader.");
      throw new UnexpectedFailure(e);
    }
  }

  public void addMethodTrace() {
    this.classPool.appendClassPath(new ClassClassPath(MethodTrace.getInstance().getClass()));

  }

  public static TCClassLoader init(String targetCP, String[] auxiliaryClasspathList, ClassLoader spoonLoader) {
    List<String> cpEntries = new ArrayList<>(Arrays.asList(auxiliaryClasspathList));
    logger.info("* Setting up classpath");
    logger.info("  projectCP: ");
    logger.info("  - {}", targetCP);
    URL[] urls = null;
    try {
      URL url = new File(targetCP).toURI().toURL();
      urls = new URL[]{url};
    } catch (MalformedURLException e) {
      // mustn't happen
    }

    List<String> auxiliaryClasspath = new ArrayList<>(cpEntries);
    LoggingUtils.logList(logger, auxiliaryClasspath, "Auxiliary Classpath");
    return new TCClassLoader(urls, spoonLoader);
  }

  @Override
  public Class<?> findClass(String name) throws ClassNotFoundException {
    return super.findClass(name);
  }

  @Override
  public Class<?> loadClass(String name) {
    /* Load class that resides in
     * target/dependency/*.jar or system class loader
     */
    boolean isArray = name.contains("[");
    if (isArray) {
      return loadArrayClass(name);
    } else {
      return loadNonArrayClass(name);
    }
  }

  private Class<?> loadNonArrayClass(String name) {
    logger.debug("* Loading {}", name);

    Class<?> aClass = null;
    try {
      aClass = Class.forName(name, true, this.getParent());
      if (aClass.getClassLoader() != null &&
              aClass.getClassLoader().equals(this.getParent())) {
        // Class belonging to auxiliary classpath
        return aClass;
      }
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      // Class belonging to target program
    }


    /* Load instrumented class */
    try {
      Class<?> loadedClass = loadedClasses.get(name);
      if (loadedClass != null) {
        return loadedClass;
      }

      byte[] bytes = ClassInstrumentation.instrumentAllMethods(classPool, name);


      /*
       * bytes == null if the class of `name` is not target program
       */
      if (bytes != null) {
        // instrument target program
        Class<?> instrumentedClass = defineClass(name, bytes, 0, bytes.length);
        loadedClasses.put(name, instrumentedClass);
        logger.debug("Successfully instrumented class - {}", name);
        return instrumentedClass;
      }
    } catch (NotFoundException ex) {
      logger.debug("Failed to find class from ClassPool - {}", name);
    } catch (NoClassDefFoundError | IOException | CannotCompileException ex) {
      logger.debug("Failed to define instrumented class - {}", name);
    }

    try {
      if (aClass != null) {
        // class automatically loaded by JVM, and not belonging to target program
        logger.debug("{} is automatically loaded by JVM", name);
        return aClass;
      } else {
        logger.debug("Loading original class (instrumentation failure) - {}", name);
        return super.loadClass(name);
      }
    } catch (Exception | NoClassDefFoundError ex) {
      logger.error("Class loading failed - {}", name);
      return null;
    }
  }

  private Class<?> loadArrayClass(String name) {
    int braceIndex = name.indexOf('[');
    String elementClassName = name.substring(0, braceIndex);
    Class<?> elementClass = loadClass(elementClassName);
    if (elementClass == null) {
      return null;
    }
    String actualClassName = makeArrayClassName(name);

    try {
      return Class.forName(actualClassName, true, elementClass.getClassLoader());
    } catch (ClassNotFoundException e) {
      logger.error("Class loading failed - {}", actualClassName);
      return null;
    }
  }

  private String makeArrayClassName(String name) {
    int braceIndex = name.indexOf('[');
    String className = name.substring(0, braceIndex);
    int count = 0;
    for (int i = 0; i < name.length(); i++) {
      if (name.charAt(i) == '[')
        count++;
    }
    StringBuilder arrayPrefix = new StringBuilder();
    for (int i = 0; i < count; i++) {
      arrayPrefix.append("[");
    }
    return String.format("%sL%s;", arrayPrefix, className);
  }

  public Class<?> defineTestClass(JavaSourceCode javaSourceCode, JavaByteCode compiledByteCode) {
    String binaryClassName = javaSourceCode.getQualifiedClassName();

    byte[] bytes = compiledByteCode.getBytes();
    
    try {
      return super.defineClass(binaryClassName, bytes, 0, bytes.length);
    } catch (Exception e) {
      return null;
    }
  }
}