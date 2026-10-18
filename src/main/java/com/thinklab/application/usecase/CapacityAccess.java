package com.thinklab.application.usecase;

import com.thinklab.domain.exception.CapacityAccessDeniedException;

/** The capacity registry is for IT staff and for the tools they run (ADR-032): a REQUESTER is refused everywhere. */
final class CapacityAccess {

    static final String REQUESTER_ROLE = "REQUESTER";

    private CapacityAccess() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static void requireStaff(String role, String operation) {
        if (REQUESTER_ROLE.equals(role)) {
            throw new CapacityAccessDeniedException("A requester cannot " + operation + ": capacitys are handled by IT staff.");
        }
    }
}
