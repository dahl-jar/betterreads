package com.betterreads.common.exception;
import java.io.Serial;

public class BusinessRuleException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(final String message) {
        super(message);
    }
}
