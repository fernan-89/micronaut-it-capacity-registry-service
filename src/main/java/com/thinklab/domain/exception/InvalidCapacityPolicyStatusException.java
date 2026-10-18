package com.thinklab.domain.exception;

/** Domain Exception: an illegal CapacityPolicy lifecycle transition, or the policy changed while the write was applied. RFC 7807 mapping: HTTP 409 Conflict. */
public class InvalidCapacityPolicyStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-CAP-00409";

    public InvalidCapacityPolicyStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
