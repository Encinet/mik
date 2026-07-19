package org.encinet.mik.module.ban;

public final class BanServiceException extends Exception {

    BanServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    BanServiceException(String message) {
        super(message);
    }
}
