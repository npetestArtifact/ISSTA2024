package npetest.commons.keys;

import spoon.reflect.declaration.CtElement;

public interface SpoonElementKey {
  String getKey();

  CtElement getCtElement();
}
