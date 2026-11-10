package org.encinet.mik.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Assert real QQ endpoint selection while routing HTTP exchanges to a local test server. */
public final class QqTestHttpClient {
    private QqTestHttpClient() { }

    public static HttpClient create(int port) {
        HttpClient delegate = HttpClient.newHttpClient();
        HttpClient client = mock(HttpClient.class, withSettings().defaultAnswer(delegatesTo(delegate)));
        doAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            URI uri = request.uri();
            assertEquals("https", uri.getScheme());
            assertEquals("api.bot.qq.com", uri.getHost());
            assertEquals(-1, uri.getPort());
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            URI local = URI.create("http://127.0.0.1:" + port + uri.getRawPath() + query);
            HttpRequest redirected = HttpRequest.newBuilder(request, (_, _) -> true).uri(local).build();
            HttpResponse.BodyHandler<String> handler = invocation.getArgument(1);
            return delegate.sendAsync(redirected, handler);
        }).when(client).sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        return client;
    }
}
