package org.encinet.mik.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.Inet6Address;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class GeoUtilTest {

    @Test
    void resolvesIpv4CountriesAndLocales() throws Exception {
        assertLookup("1.0.0.0", "AU", "en_au");
        assertLookup("1.0.0.255", "AU", "en_au");
        assertLookup("1.0.1.0", "CN", "zh_cn");
        assertLookup("1.34.0.0", "TW", "zh_tw");
        assertLookup("1.36.0.0", "HK", "zh_hk");
        assertLookup("8.8.8.8", "US", "en_us");
        assertLookup("1.0.16.0", "JP", "ja_jp");
    }

    @Test
    void resolvesIpv6CountriesAndLocales() throws Exception {
        assertLookup("2001:250::", "CN", "zh_cn");
        assertLookup("2001:218:6000:2::", "HK", "zh_hk");
        assertLookup("2001:288::", "TW", "zh_tw");
        assertLookup("2001:4860:4860::8888", "US", "en_us");
        assertLookup("2404:6800:4004:80a::200e", "AU", "en_au");
    }

    @Test
    void returnsUnknownForNonPublicAddresses() throws Exception {
        assertLookup("127.0.0.1", GeoUtil.UNKNOWN_COUNTRY, GeoUtil.DEFAULT_LANGUAGE);
        assertLookup("10.0.0.1", GeoUtil.UNKNOWN_COUNTRY, GeoUtil.DEFAULT_LANGUAGE);
        assertLookup("192.168.1.1", GeoUtil.UNKNOWN_COUNTRY, GeoUtil.DEFAULT_LANGUAGE);
        assertLookup("::1", GeoUtil.UNKNOWN_COUNTRY, GeoUtil.DEFAULT_LANGUAGE);
        assertLookup("fe80::1", GeoUtil.UNKNOWN_COUNTRY, GeoUtil.DEFAULT_LANGUAGE);
        assertEquals(GeoUtil.UNKNOWN_COUNTRY, GeoUtil.countryCode(null));
    }

    @Test
    void handlesIpv4MappedIpv6() throws Exception {
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xFF;
        mapped[11] = (byte) 0xFF;
        mapped[12] = 8;
        mapped[13] = 8;
        mapped[14] = 8;
        mapped[15] = 8;
        InetAddress address = Inet6Address.getByAddress(null, mapped, -1);

        assertEquals("US", GeoUtil.countryCode(address));
        assertEquals("en_us", GeoUtil.languageCode(address));
    }

    @Test
    void mapsOnlyExplicitMinecraftLocales() {
        assertEquals("zh_cn", GeoUtil.languageCodeForCountry("CN"));
        assertEquals("zh_hk", GeoUtil.languageCodeForCountry("HK"));
        assertEquals("zh_hk", GeoUtil.languageCodeForCountry("MO"));
        assertEquals("zh_tw", GeoUtil.languageCodeForCountry("TW"));
        assertEquals("de_de", GeoUtil.languageCodeForCountry("DE"));
        assertEquals("en_us", GeoUtil.languageCodeForCountry("AQ"));
        assertEquals("en_us", GeoUtil.languageCodeForCountry("CU"));
        assertEquals("en_us", GeoUtil.languageCodeForCountry(null));
        assertEquals("en_us", GeoUtil.languageCodeForCountry("cn"));
    }

    @Test
    void reusesCountryAndLocaleStrings() throws Exception {
        InetAddress address = InetAddress.getByName("8.8.8.8");
        assertSame(GeoUtil.countryCode(address), GeoUtil.countryCode(address));
        assertSame(GeoUtil.languageCode(address), GeoUtil.languageCode(address));
    }

    private static void assertLookup(String address, String country, String locale) throws Exception {
        InetAddress inetAddress = InetAddress.getByName(address);
        assertEquals(country, GeoUtil.countryCode(inetAddress), address);
        assertEquals(locale, GeoUtil.languageCode(inetAddress), address);
    }
}
