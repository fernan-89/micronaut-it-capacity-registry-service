package com.thinklab.domain.exception;

/** Domain Exception: a REQUESTER tried to use the capacity registry (staff only, ADR-032). RFC 7807 mapping: HTTP 403 Forbidden. */
public class CapacityAccessDeniedException extends BusinessException {

    private static final String ERROR_CODE = "ERR-CAP-00403";

    public CapacityAccessDeniedException(String message) {
        super(ERROR_CODE, message);
    }
}
