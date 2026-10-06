package org.encinet.mik.module.ai.runtime;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionException;

public final class LimitedHttpBody {
    private LimitedHttpBody() {
    }

    public static HttpResponse.BodyHandler<byte[]> bytes(int maximumBytes) {
        return ignored -> HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofInputStream(), input -> {
                    try (input) {
                        byte[] body = input.readNBytes(maximumBytes + 1);
                        if (body.length > maximumBytes) {
                            throw new CompletionException(new IOException(
                                    "HTTP response exceeds " + maximumBytes + " bytes"));
                        }
                        return body;
                    } catch (IOException error) {
                        throw new CompletionException(error);
                    }
                });
    }
}
