package com.firefly.ecm.adapter.logalty.exceptions;

/**
 * Custom runtime exception representing an error returned by Logalty services
 * when the response code is not DOCUMENT_ACCEPTED.
 * Contains the same information we log today (code, reason, and reference id when available).
 */
public class LogaltyCallException extends RuntimeException {

    private final String code;
    private final String reason;
    private final String reference;

    public LogaltyCallException(String message) {
        super(message);
        this.code = null;
        this.reason = null;
        this.reference = null;
    }

    public LogaltyCallException(String message, String code, String reason, String reference) {
        super(message);
        this.code = code;
        this.reason = reason;
        this.reference = reference;
    }

    public String getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }

    public String getReference() {
        return reference;
    }
}
