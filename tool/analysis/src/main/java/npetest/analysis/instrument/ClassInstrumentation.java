package npetest.analysis.instrument;

import npetest.analysis.dynamicanalysis.BasicBlockCoverage;
import npetest.analysis.npeanalysis.NullableFieldAnalyzer;
import npetest.commons.keys.KeyUtils;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.analysis.ControlFlow;
import javassist.bytecode.analysis.ControlFlow.Block;
import javassist.compiler.CompileError;
import javassist.compiler.Javac;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

public class ClassInstrumentation {

  public static boolean enabled;

  public static String url;

  private ClassInstrumentation() {
  }

  private static final Logger logger = LoggerFactory.getLogger(ClassInstrumentation.class);

  public static byte[] instrumentAllMethods(ClassPool classPool, String className)
          throws NotFoundException, IOException, CannotCompileException {
    if (enabled) {
      NullableFieldAnalyzer.getInstance().analyze(className);
    }

    CtClass cc;
    try {
      cc = classPool.get(className);
    } catch (NotFoundException e) {
      return null;
    } 

    if (cc.getURL().toString().contains("java.base") || cc.getURL().toString().contains("npetest")
        || !cc.getURL().toString().contains(url)) return null;

    // if (!className.contains("npetest") && !className.contains("spoon") && !Modifier.isNative(cc.getModifiers())) {
    // if (!Modifier.isNative(cc.getModifiers())) {
    CtMethod[] methods = cc.getDeclaredMethods();
    for (CtMethod method : methods) {
      if (method.isEmpty() || (AccessFlag.BRIDGE & method.getMethodInfo().getAccessFlags()) != 0
              || method.getName().startsWith("access$")
              || KeyUtils.ofCtBehavior(method) == null 
              || Modifier.isNative(method.getModifiers())) {
        continue;
      }

      instrumentMethodEntryHook(className, method);
      // instrumentBasicBlockHook(cc, className, method);
    }
    // } else return null;


    byte[] bytes = cc.toBytecode();
    cc.detach();
    return bytes;
  }


  private static void instrumentMethodEntryHook(String className, CtMethod method) {
    if (!enabled) {
      return;
    }
    String parameters = KeyUtils.ofCtBehavior(method);
    
    String methodKey = className + "\\#" + method.getName() + parameters;

    String entryCode = "npetest.analysis.dynamicanalysis.MethodTrace.getInstance().recordMethodEntry(\"" + methodKey + "\");";
  
    try {
      method.insertBefore(entryCode);
      logger.debug("Succeed to instrument method - {}", methodKey);
    } catch (CannotCompileException ex) {
      // this must not happen
      ex.printStackTrace();
      logger.debug("Failed to instrument method - {}", methodKey);
    }
  }

  private static void instrumentBasicBlockHook(CtClass cc, String className, CtMethod method) {
    if (!enabled) {
      return;
    }

    String parameters = KeyUtils.ofCtBehavior(method);
    String methodKey = className + "\\#" + method.getName() + parameters;

    ControlFlow controlFlow;
    try {
      controlFlow = new ControlFlow(method);
    } catch (BadBytecode e) {
      logger.debug("BadBytecode in analyzing control flow - {}", methodKey);
      return;
    }

    MethodInfo methodInfo = method.getMethodInfo();
    CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
    CodeIterator iterator = codeAttribute.iterator();
    int stackSizeIncrement = 0;
    Block[] blocks = controlFlow.basicBlocks();
    if (blocks.length == 0) {
      return;
    }

    SortedMap<Integer, Integer> positionInfo = new TreeMap<>(Collections.reverseOrder());
    for (Block block : blocks) {
      positionInfo.put(block.position(), block.index());
    }

    BasicBlockCoverage.getInstance().setupBasicBlockInfo(methodKey, blocks);

    for (Entry<Integer, Integer> entry : positionInfo.entrySet()) {
      int position = entry.getKey();
      int index = entry.getValue();
      String hook = String.format("npetest.analysis.dynamicanalysis.BasicBlockCoverage.getInstance().recordBasicBlockHit(\"%s\", \"%d\");", methodKey, index);
      try {
        Javac javac = new Javac(cc);
        javac.compileStmnt(hook);
        Bytecode bytecode = javac.getBytecode();
        iterator.insertAt(position, bytecode.get());
        stackSizeIncrement += bytecode.getMaxStack();
      } catch (CompileError | BadBytecode e) {
        int lineNumber = method.getMethodInfo().getLineNumber(position);
        logger.debug("Failed to instrument basic block of {} (bb_index: {}, line: {})", methodKey, index, lineNumber);
      }
    }

    codeAttribute.setMaxStack(codeAttribute.getMaxStack() + stackSizeIncrement);
  }
}
