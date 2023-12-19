package npetest.analysis.npeanalysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import npetest.commons.astmodel.CtModelExt;
import npetest.language.CodeFactory;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtWhile;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtPackage;

public class PathBuilder {

  private static final Logger logger = LoggerFactory.getLogger(PathBuilder.class);

  private static final PathBuilder instance = new PathBuilder();

  String typeKey = "";

  private int totalPaths = 0;
  private boolean constructorPath = false;

  SourcePosition startPosition = null;

  private Set<List<CtElement>> pathList = new HashSet<>();

  // CtExpression<?> 


  // private Map<String, Set<List<CtElement>>> returnPaths = new HashMap<>();
  
  public static PathBuilder getInstance() {
    return instance;
  }

  public void setStartPosition(SourcePosition startPos) {
    this.startPosition = startPos;
  }

  public void setConstruct(boolean b) {
    this.constructorPath = b;
  }

  private void forwardCtWhile(String methodKey, CtBlock<?> block, CtElement condition, CtElement targetPoint, List<CtElement> prevList) {
    CtElement trueEle = CodeFactory.createLiteral(true);
    List<CtElement> truePath = new ArrayList<>(prevList);
    CtElement newStart = null;

    List<CtElement> ifElements  = condition.getDirectChildren(); 
    CtElement condEle = ifElements.get(0);

    List<CtElement> blocks = ifElements.stream().filter(e -> e instanceof CtBlock).collect(Collectors.toList());
    //TRUE PATH
    truePath.add(trueEle);
    truePath.add(condEle);

    CtBlock<?> trueBlock = (CtBlock<?>)blocks.get(0);
    newStart = trueBlock.getDirectChildren().get(0);    

    startPosition = newStart.getPosition();
    pathFromStart(methodKey, (CtBlock<?>)blocks.get(0), newStart, targetPoint, truePath);

    return;    
  }
  

  // List<CtElement> ll = new ArrayList<>();
  // CtElement prev = null;


  private void forwardCtForEach(String methodKey, CtBlock<?> block, CtElement condition, CtElement targetPoint, List<CtElement> prevList) {
    CtElement trueEle = CodeFactory.createLiteral(true);
    List<CtElement> truePath = new ArrayList<>(prevList);
    CtElement newStart = null;

    List<CtElement> ifElements  = condition.getDirectChildren(); 
    CtElement condEle = ifElements.get(0);

    List<CtElement> blocks = ifElements.stream().filter(e -> e instanceof CtBlock).collect(Collectors.toList());
    //TRUE PATH
    truePath.add(trueEle);
    truePath.add(condEle);

    CtBlock<?> trueBlock = (CtBlock<?>)blocks.get(0);
    newStart = trueBlock.getDirectChildren().get(0);    

    startPosition = newStart.getPosition();
    pathFromStart(methodKey, (CtBlock<?>)blocks.get(0), newStart, targetPoint, truePath);

    return;
  }

  
  private void forwardCtIfTest(String methodKey, CtBlock<?> block, CtElement condition, CtElement targetPoint, List<CtElement> prevList) {
    CtElement trueEle = CodeFactory.createLiteral(true);
    List<CtElement> truePath = new ArrayList<CtElement>(prevList);
    CtElement newStart = null;

    List<CtElement> ifElements  = condition.getDirectChildren(); 
    CtElement condEle = ifElements.get(0);

    List<CtElement> blocks = ifElements.stream().filter(e -> e instanceof CtBlock).collect(Collectors.toList());
    //TRUE PATH
    truePath.add(trueEle);
    truePath.add(condEle);

    CtBlock<?> trueBlock = (CtBlock<?>)blocks.get(0);
    newStart = trueBlock.getDirectChildren().get(0);    

    // logger.debug("FORWARDCTIF TEST");
    // printSelectedEle((CtElement)blocks.get(0));
    // printSelectedEle(newStart);

    // logger.debug("TEST DONE");

    startPosition = newStart.getPosition();

    pathFromStart(methodKey, (CtBlock<?>)blocks.get(0), newStart, targetPoint, truePath);

    return;
  }


  // lhs = condele ? 2 : 3
  private void forwardCtCond(String methodKey, CtBlock<?> block, CtElement condition, CtElement targetPoint, List<CtElement> prevList) {
    CtElement trueEle = CodeFactory.createLiteral(true);
    List<CtElement> truePath = new ArrayList<CtElement>(prevList);
    CtElement newStart = condition;

    List<CtElement> ctCondElements  = condition.getDirectChildren(); 
    CtElement ctConds = ctCondElements.get(2);

    List<CtElement> condElements = ctConds.getDirectChildren();

    CtElement condEle = condElements.get(1);

    //TRUE PATH
    truePath.add(trueEle);
    truePath.add(condition);
    // truePath.add(condEle);
    
    // truePath.add(condElements.get(2));
    
    startPosition = newStart.getPosition();

    pathFromStart(methodKey, block, newStart, targetPoint, truePath);

    return;
  }


  private CtElement getOuterCtInvocation(CtElement e) {
    CtElement parent = e.getParent();

    if (parent instanceof CtBlock) return e;
    else {
      return getOuterCtInvocation(parent);
    }
  }


  public void pathFromStart(String methodKey, CtBlock<?> block, CtElement startPoint, CtElement targetPoint, List<CtElement> prevList) {
    boolean preventInfLoop = false;
    CtElement prev = null;
    CtElement falseCond = CodeFactory.createLiteral(false);
    List<CtElement> result = new ArrayList<CtElement>(prevList);

    ListIterator<CtElement> iter = block.getDirectChildren().listIterator(block.getDirectChildren().indexOf(startPoint));   

    if (block.filterChildren(e -> e.equals(startPoint)).list().size() > 1) preventInfLoop = true;

    // if (!startPoint.getPosition().equals(startPosition)) preventInfLoop = true;

    // logger.debug("START PATH FROM START");
    
    while (iter.hasNext()) {
      prev = iter.next();

      if (preventInfLoop && prev.getPosition().equals(startPosition)) {
        preventInfLoop = false;
        continue;
      }

      if (preventInfLoop) continue;

      if (!constructorPath && (prev.equals(targetPoint) || prev.equals(targetPoint.getParent()) || prev.equals(getOuterCtInvocation(targetPoint)))) {
        result.add(prev);

        result.add(targetPoint);

        totalPaths++;

        pathList.add(result);

        return;
      }  else if (prevList.contains(prev)) {
        continue;
      }    
      // We do not collect any paths to throw/return
      else if (prev instanceof CtReturn || prev instanceof CtThrow) {
        // logger.debug("RETURN VAL or THROW");
        // printSelectedEle(prev);
        // logger.debug("DONE RETURN");
        return;
      }      
      else if (prev instanceof CtBreak) {
        result.add(prev);
        
      } 

      else if (prev instanceof CtAssignment) {
        List<CtElement> checkPrev = prev.getDirectChildren();
        
        if (checkPrev.stream().filter(e -> e instanceof CtConditional).count() > 0) {
          // logger.debug("CTCONDITION!");
          forwardCtCond(methodKey, block, prev, targetPoint, result);

          CtElement falseEle = CodeFactory.createLiteral(false);
          
          result.add(falseEle);
          result.add(prev);

        } else {
          // logger.debug("CTASSIGN");
          // logger.debug(prev.toString());
          result.add(prev);
        }
      }
      
      else if (prev instanceof CtIf) {
        // START TRUE BRANCH
        // logger.debug("START CTIF");
        // printAllEleFromClass(prev);
        // printSelectedEle(prev);
        forwardCtIfTest(methodKey, block, prev, targetPoint, result);


        // FALSE BRANCH
        // logger.debug("CTIF FALSE BRANCH");
        // printAllEleFromClass(prev);
        // printSelectedEle(prev);
        // logger.debug("DONE");

        List<CtElement> condEle = prev.getDirectChildren();
        result.add(falseCond);
        result.add(condEle.get(0));

        List<CtElement> blocks = condEle.stream().filter(e -> e instanceof CtBlock).collect(Collectors.toList());

        if (blocks.size() > 1) {
          CtBlock<?> falseBlock = (CtBlock<?>)blocks.get(1);
          startPosition = falseBlock.getDirectChildren().get(0).getPosition();
          pathFromStart(methodKey, falseBlock, falseBlock.getDirectChildren().get(0), targetPoint, result);
          return;
        }
        
      } else if (prev instanceof CtForEach) {
        // START TRUE BRANCH
        forwardCtForEach(methodKey, block, prev, targetPoint, result);

        // FALSE BRANCH
        List<CtElement> condEle = prev.getDirectChildren();
        result.add(falseCond);
        result.add(condEle.get(0));        
      } else if (prev instanceof CtWhile) {
        // START TRUE BRANCH
        forwardCtWhile(methodKey, block, prev, targetPoint, result);

        // FALSE BRANCH
        List<CtElement> condEle = prev.getDirectChildren();
        result.add(falseCond);
        result.add(condEle.get(0));        
      } 
      else {
        result.add(prev);        
      }

    }

    // logger.debug("BEFORE ENTERING TO FIND EXIT ELEMENT");
    // printSelectedEle(prev);
    // if (!(block.getParent() instanceof CtMethod || block.getParent() instanceof CtMethodImpl || block.getParent().equals(iter))) {
    if (!block.getParent().equals(CtModelExt.INSTANCE.getMethodFromKey(methodKey))) {

      // logger.debug("REACH TO THE END OF THE BLOCKS NOT METHOD");
      
      CtElement exitEle = findExitElement(methodKey, block, startPoint, targetPoint, result);

      if (exitEle == null) {
        if (constructorPath) pathList.add(result);
        return;
      }

      // printSelectedEle(exitEle);
      
      startPosition = exitEle.getPosition(); 
      pathFromStart(methodKey, (CtBlock<?>) exitEle.getParent(), exitEle, targetPoint, result);
    }

    return;
  }

  private CtElement findExitElement(String methodKey, CtBlock<?> block, CtElement startPoint, CtElement targetPoint, List<CtElement> prevList) {
    // logger.debug("ITS INSIDE THE BLOCKS...");

    CtElement prevCond = startPoint.getParent().getParent();
    // CtElement result = null;

    SourcePosition tmpStartPosition = prevCond.getPosition();

    if (prevCond.getParent() instanceof CtClass || prevCond.getParent() instanceof CtPackage) return null;
    
    CtBlock<?> newBlock = (CtBlock<?>) prevCond.getParent();

    int outerIndex = newBlock.getDirectChildren().indexOf(prevCond) + 1;

    if (outerIndex == newBlock.getDirectChildren().size()) {

      CtElement tt = newBlock.getParent();
      if (tt instanceof CtForEach || tt instanceof CtWhile) {
        CtElement falseCond = CodeFactory.createLiteral(false);
        List<CtElement> condStmts  = tt.getDirectChildren();   
        prevList.add(falseCond);
        prevList.add(condStmts.get(0));
      } 

      if (tt.equals(CtModelExt.INSTANCE.getMethodFromKey(methodKey)))
        return null;

      startPosition = tt.getPosition();

      // return findExitElement(methodKey, newBlock, startPoint, targetPoint, prevList);
      return findExitElement(methodKey, newBlock, tt, targetPoint, prevList);
    }

    List<CtElement> tmpCheck = newBlock.filterChildren(e -> e.equals(prevCond)).list();

    if (tmpCheck.size() > 1) {
      CtElement prev;
      ListIterator<CtElement> iter = newBlock.getDirectChildren().listIterator(newBlock.getDirectChildren().indexOf(prevCond));    

      while (iter.hasNext()) {
        prev = iter.next();
        if (prev.getPosition().equals(tmpStartPosition)) {
          prev = iter.next();
          return prev;
        } else continue;
      }
    } else {
      // logger.debug("DEBUGGING");
      // printSelectedEle(newBlock.getDirectChildren().get(outerIndex));
      return newBlock.getDirectChildren().get(outerIndex);

    }

    return null;
       
  }

  public Set<List<CtElement>> getPathList() {
    return pathList;
  }

  public int getPathNum() {
    return totalPaths;
  }

  public void calculatePath(String methodKey, CtBlock<?> blk, CtElement startPoint, CtElement targetPoint, List<CtElement> prevList) {
    pathList = new HashSet<>();
    totalPaths = 0;
    pathFromStart(methodKey, blk, startPoint, targetPoint, prevList);
  }


  private void printSelectedEle (CtElement element) {
      //Find the level in the Syntax Tree of the element
      int n = 0;
      CtElement parent = element.getParent();
      while (parent != null) {
          n++;
          parent = parent.getParent();
      }

      // Print the element
      try {
          String s = "";
          if (n > 0) s = String.format("%0" + n + "d", 0).replace("0","-");
          logger.debug(s + ", " + element.getClass().getSimpleName() + ", " + element.toString());
      } catch (NullPointerException ex) {
          logger.error("Unknown Element");
      }
  }
  private void printAllEleFromClass (CtElement targetClass) {
    //Find the level in the Syntax Tree of the element
    int n = 0;
    for (CtElement element: targetClass.getElements(null)) {
      n = 0;
      CtElement parent = element.getParent();
      while (parent != null) {
          n++;
          parent = parent.getParent();

      }

      // Print the element
      try {
          String s = "";
          if (n > 0) s = String.format("%0" + n + "d", 0).replace("0","-");
          logger.debug(s + ", " + element.getClass().getSimpleName() + ", " + element.toString());
      } catch (NullPointerException ex) {
          logger.error("Unknown Element");
      }
    }
  }

}
