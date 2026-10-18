package com.thinklab.domain.exception;

/** Domain Exception: a capacity policy name already used in the organisation. RFC 7807 mapping: HTTP 409 Conflict. */
public class DuplicateCapacityPolicyException extends BusinessException {

    private static final String ERROR_CODE = "ERR-CAP-00409";

    public DuplicateCapacityPolicyException(String message) {
        super(ERROR_CODE, message);
    }
}
