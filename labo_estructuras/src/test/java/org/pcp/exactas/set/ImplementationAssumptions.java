package org.pcp.exactas.set;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class ImplementationAssumptions {
    private ImplementationAssumptions() {
    }

    static void assumeImplemented(ConcurrentIntSet set) {
        assumeTrue(set.isImplemented(),
                () -> set.getClass().getSimpleName() + " is not implemented yet");
    }
}
