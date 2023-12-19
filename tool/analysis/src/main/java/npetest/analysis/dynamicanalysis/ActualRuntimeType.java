package npetest.analysis.dynamicanalysis;

import java.util.HashMap;
import java.util.Map;

import npetest.commons.astmodel.CtModelExt;
import spoon.reflect.declaration.CtType;

public class ActualRuntimeType {
  private static final ActualRuntimeType instance = new ActualRuntimeType();

  public static ActualRuntimeType getInstance() {
    return instance;
  }

  private static final Map<String, CtType<?>> variableNameToActualTypes = new HashMap<>();

  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void enable() {
    enabled = true;
  }

  public void disable() {
    enabled = false;
  }

  public void reset() {
    enabled = false;
    variableNameToActualTypes.clear();
  }

  @SuppressWarnings("unused")
  public void updateCreatedType(String variableName, Class<?> clazz) {
    CtType<?> ctType = CtModelExt.INSTANCE.queryCtType(clazz.getName());
    if (ctType != null) {
      variableNameToActualTypes.put(variableName, ctType);
    }
  }

  public Map<String, CtType<?>> getActualTypes() {
    return variableNameToActualTypes;
  }
}
