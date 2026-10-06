package org.encinet.mik.module.ai.tool.web;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicWebUriPolicyTest {

    @Test
    void allowsOnlyHostsWhoseEntireDnsAnswerIsPublic() throws Exception {
        Map<String, InetAddress[]> answers = Map.of(
                "public.example", addresses("93.184.216.34"),
                "private.example", addresses("10.0.0.4"),
                "mixed.example", addresses("93.184.216.34", "192.168.1.20"));
        PublicWebUriPolicy policy = new PublicWebUriPolicy(host -> answers.get(host));

        URI allowed = policy.requirePublic(
                URI.create("HTTPS://PUBLIC.EXAMPLE/news/../page?q=1#section"));

        assertEquals("https://public.example/page?q=1", allowed.toASCIIString());
        assertThrows(WebFetchException.class, () -> policy.requirePublic(
                URI.create("https://private.example/")));
        assertThrows(WebFetchException.class, () -> policy.requirePublic(
                URI.create("https://mixed.example/")));
    }

    @Test
    void rejectsCredentialsLocalNamesAndSpecialPurposeAddresses() {
        PublicWebUriPolicy policy = new PublicWebUriPolicy();

        for (String url : new String[]{
                "file:///etc/passwd",
                "http://user:password@example.com/",
                "http://localhost/",
                "http://service.internal/",
                "http://127.0.0.1/",
                "http://10.1.2.3/",
                "http://169.254.169.254/latest/meta-data/",
                "http://172.20.1.2/",
                "http://192.168.1.2/",
                "http://100.64.0.1/",
                "http://[::1]/",
                "http://[fc00::1]/",
                "http://[fe80::1]/"}) {
            assertThrows(WebFetchException.class,
                    () -> policy.requirePublic(URI.create(url)), url);
        }
    }

    @Test
    void recognizesPublicAndReservedAddressBytes() throws Exception {
        assertEquals(true, PublicWebUriPolicy.isGloballyRoutable(
                InetAddress.getByName("93.184.216.34").getAddress()));
        assertEquals(true, PublicWebUriPolicy.isGloballyRoutable(
                InetAddress.getByName("2606:4700:4700::1111").getAddress()));
        assertEquals(false, PublicWebUriPolicy.isGloballyRoutable(
                InetAddress.getByName("203.0.113.8").getAddress()));
        assertEquals(false, PublicWebUriPolicy.isGloballyRoutable(
                InetAddress.getByName("2001:db8::1").getAddress()));
    }

    private static InetAddress[] addresses(String... values) throws Exception {
        InetAddress[] result = new InetAddress[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = InetAddress.getByName(values[index]);
        }
        return result;
    }
}
