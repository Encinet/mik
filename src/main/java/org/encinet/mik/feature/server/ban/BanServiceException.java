package org.encinet.mik.feature.server.ban;

public final class BanServiceException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 1L;


    BanServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    BanServiceException(String message) {
        super(message);
    }
}
