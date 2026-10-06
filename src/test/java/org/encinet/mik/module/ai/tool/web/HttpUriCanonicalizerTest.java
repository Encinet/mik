package org.encinet.mik.module.ai.tool.web;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpUriCanonicalizerTest {

    @Test
    void supportsInternationalHostsAndPathsAsAsciiLinks() {
        URI canonical = HttpUriCanonicalizer.canonicalize(
                URI.create("https://例子.测试/路径?q=值#段落"), 1_000);

        assertEquals("https://xn--fsqu00a.xn--0zwm56d/"
                + "%E8%B7%AF%E5%BE%84?q=%E5%80%BC", canonical.toASCIIString());
    }

    @Test
    void preservesExistingEscapesInsteadOfEncodingPercentSignsTwice() {
        URI canonical = HttpUriCanonicalizer.canonicalize(
                URI.create("HTTPS://EXAMPLE.TEST/a%20b/%2F?q=%E2%9C%93#fragment"), 1_000);

        assertEquals("https://example.test/a%20b/%2F?q=%E2%9C%93",
                canonical.toASCIIString());
    }

    @Test
    void rejectsCredentialsInvalidPortsAndScopedIpv6() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpUriCanonicalizer.canonicalize(
                        URI.create("https://user:secret@example.test/"), 1_000));
        assertThrows(IllegalArgumentException.class, () ->
                HttpUriCanonicalizer.canonicalize(
                        URI.create("https://example.test:70000/"), 1_000));
        assertThrows(IllegalArgumentException.class, () ->
                HttpUriCanonicalizer.canonicalize(
                        URI.create("http://[fe80::1%25eth0]/"), 1_000));
    }
}
