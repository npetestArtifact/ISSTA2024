package npetest.language.sequence;

import npetest.commons.spoon.ASTUtils;
import npetest.commons.spoon.TypeUtils;
import npetest.language.CodeFactory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatementList;

import java.util.concurrent.atomic.AtomicInteger;

public class Sequence {
  private static final AtomicInteger idGen = new AtomicInteger();

  private final String id;

  private boolean isNull;

  private CtExpression<?> inlineValue;

  private CtStatementList statements;

  public Sequence(CtStatementList statements) {
    ASTUtils.removeDuplicatedVariable(statements);
    this.statements = statements;
    this.isNull = false;
    this.id = "Seq-" + idGen.getAndIncrement();
  }

  public Sequence(CtLocalVariable<?> lv) {
    this.statements = CodeFactory.createStatementList(lv);
    this.isNull = TypeUtils.isNull(lv.getDefaultExpression().getType());
    this.id = "Seq-" + idGen.getAndIncrement();
  }

  public Sequence(CtExpression<?> inlineValue) {
    this.inlineValue = inlineValue;
    this.isNull = TypeUtils.isNull(inlineValue.getType());
    this.id = String.format("<inline_value>:%s", inlineValue.toString());
  }

  public CtExpression<?> getAccessExpression() {
    if (inlineValue != null) {
      return inlineValue;
    }

    CtLocalVariable<?> lv = statements.getLastStatement();
    return CodeFactory.createVariableAccess(lv);
  }

  public CtStatementList getCtStatementList() {
    return statements;
  }

  public boolean isNull() {
    return isNull;
  }

  public boolean isInlineValue() {
    return inlineValue != null;
  }

  public String getId() {
    return id;
  }
}
