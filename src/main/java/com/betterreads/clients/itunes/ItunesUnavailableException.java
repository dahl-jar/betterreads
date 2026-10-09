package com.betterreads.clients.itunes;

public class ItunesUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final String MESSAGE = "Apple lookup unavailable";

    public ItunesUnavailableException(final Throwable cause) {
        super(MESSAGE, cause);
    }

    public ItunesUnavailableException() {
        super(MESSAGE);
    }
}
