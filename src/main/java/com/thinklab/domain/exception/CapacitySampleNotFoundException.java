package com.thinklab.domain.exception;

/** Domain Exception: no capacity run with that id in the tenant (another tenant's answers the same). RFC 7807 mapping: HTTP 404 Not Found. */
public class CapacitySampleNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-CAP-00404";

    public CapacitySampleNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
