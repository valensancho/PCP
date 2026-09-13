package org.pcp.exactas.set;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deliberately tiny DFS checker for completed Boolean integer-set operations. */
final class LinearizabilityChecker {
    enum Type { ADD, REMOVE, CONTAINS }
    record Operation(int threadId, long invocationSequence, long responseSequence, Type type, int key, boolean result) { }

    boolean isLinearizable(List<Operation> history) {
        return search(new HashSet<>(), new ArrayList<>(history), history);
    }

    private boolean search(Set<Integer> model, List<Operation> remaining, List<Operation> all) {
        if (remaining.isEmpty()) return true;
        for (int i = 0; i < remaining.size(); i++) {
            Operation candidate = remaining.get(i);
            if (!predecessorsDone(candidate, remaining, all)) continue;
            Set<Integer> nextModel = new HashSet<>(model);
            if (!matches(nextModel, candidate)) continue;
            List<Operation> nextRemaining = new ArrayList<>(remaining);
            nextRemaining.remove(i);
            if (search(nextModel, nextRemaining, all)) return true;
        }
        return false;
    }

    private boolean predecessorsDone(Operation candidate, List<Operation> remaining, List<Operation> all) {
        for (Operation other : all) {
            if (other.responseSequence < candidate.invocationSequence && remaining.contains(other)) return false;
        }
        return true;
    }

    private boolean matches(Set<Integer> model, Operation op) {
        boolean expected = switch (op.type) {
            case ADD -> model.add(op.key);
            case REMOVE -> model.remove(op.key);
            case CONTAINS -> model.contains(op.key);
        };
        return expected == op.result;
    }
}
