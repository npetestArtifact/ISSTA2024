package npetest.language.sequence.visitors;

import npetest.language.sequence.Sequence;

public interface SequenceVisitor {
  void visit(Sequence absSequence);
}
