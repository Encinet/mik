package org.encinet.mik.util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.zip.GZIPInputStream;

/**
 * Allocation-light, offline IP country lookup for IPv4 and IPv6.
 *
 * <p>The bundled database is compiled from the PDDL-licensed user-country dataset from
 * sapics/ip-location-db. IPv4 uses a direct /16 prefix table. IPv6 uses a /16 prefix table and
 * adds sparse /24 pages for dense prefixes. Both paths finish with a binary search over sorted,
 * non-overlapping ranges and do not require locks, network access, or per-result objects.
 */
public final class GeoUtil {

    public static final String UNKNOWN_COUNTRY = "ZZ";
    public static final String DEFAULT_LANGUAGE = "en_us";

    private static final int MAGIC = 0x4D494B47; // MIKG
    private static final int FORMAT_VERSION = 1;
    private static final int PREFIX_COUNT = 1 << 16;
    private static final int IPV6_PAGE_WIDTH = 257;
    private static final int MAX_RANGE_COUNT = 2_000_000;
    private static final String DATABASE_RESOURCE = "/geo/user-country-v1.dat.gz";
    private static final String[] COUNTRY_CODES = buildCountryCodes();
    private static final String[] LANGUAGE_CODES = buildLanguageCodes();

    private GeoUtil() {}

    /** Returns the ISO 3166-1 alpha-2 country/region code, or {@value #UNKNOWN_COUNTRY}. */
    public static String countryCode(InetAddress address) {
        short encoded = lookupCountry(address);
        return encoded < 0 ? UNKNOWN_COUNTRY : COUNTRY_CODES[encoded];
    }

    /**
     * Returns a Minecraft locale code explicitly associated with the resolved country/region.
     * Countries without a corresponding Minecraft locale fall back to {@value #DEFAULT_LANGUAGE}.
     */
    public static String languageCode(InetAddress address) {
        short encoded = lookupCountry(address);
        return encoded < 0 ? DEFAULT_LANGUAGE : LANGUAGE_CODES[encoded];
    }

    /** Maps an ISO country/region code to a Minecraft locale without heuristic locale inference. */
    public static String languageCodeForCountry(String countryCode) {
        if (countryCode == null || countryCode.length() != 2
                || countryCode.charAt(0) < 'A' || countryCode.charAt(0) > 'Z'
                || countryCode.charAt(1) < 'A' || countryCode.charAt(1) > 'Z') {
            return DEFAULT_LANGUAGE;
        }
        int encoded = (countryCode.charAt(0) - 'A') * 26 + countryCode.charAt(1) - 'A';
        return LANGUAGE_CODES[encoded];
    }

    private static String explicitLanguageCode(String countryCode) {
        return switch (countryCode.charAt(0)) {
            case 'A' -> switch (countryCode) {
                case "AL" -> "sq_al";
                case "AM" -> "hy_am";
                case "AR" -> "es_ar";
                case "AT" -> "de_at";
                case "AU" -> "en_au";
                case "AZ" -> "az_az";
                default -> DEFAULT_LANGUAGE;
            };
            case 'B' -> switch (countryCode) {
                case "BA" -> "bs_ba";
                case "BE" -> "nl_be";
                case "BG" -> "bg_bg";
                case "BR" -> "pt_br";
                case "BY" -> "be_by";
                default -> DEFAULT_LANGUAGE;
            };
            case 'C' -> switch (countryCode) {
                case "CA" -> "en_ca";
                case "CH" -> "de_ch";
                case "CL" -> "es_cl";
                case "CN" -> "zh_cn";
                case "CZ" -> "cs_cz";
                default -> DEFAULT_LANGUAGE;
            };
            case 'D' -> switch (countryCode) {
                case "DE" -> "de_de";
                case "DK" -> "da_dk";
                default -> DEFAULT_LANGUAGE;
            };
            case 'E' -> switch (countryCode) {
                case "EC" -> "es_ec";
                case "EE" -> "et_ee";
                case "ES" -> "es_es";
                default -> DEFAULT_LANGUAGE;
            };
            case 'F' -> switch (countryCode) {
                case "FI" -> "fi_fi";
                case "FO" -> "fo_fo";
                case "FR" -> "fr_fr";
                default -> DEFAULT_LANGUAGE;
            };
            case 'G' -> switch (countryCode) {
                case "GB" -> "en_gb";
                case "GE" -> "ka_ge";
                case "GR" -> "el_gr";
                default -> DEFAULT_LANGUAGE;
            };
            case 'H' -> switch (countryCode) {
                case "HK" -> "zh_hk";
                case "HR" -> "hr_hr";
                case "HU" -> "hu_hu";
                default -> DEFAULT_LANGUAGE;
            };
            case 'I' -> switch (countryCode) {
                case "ID" -> "id_id";
                case "IE" -> "ga_ie";
                case "IL" -> "he_il";
                case "IN" -> "hi_in";
                case "IR" -> "fa_ir";
                case "IS" -> "is_is";
                case "IT" -> "it_it";
                default -> DEFAULT_LANGUAGE;
            };
            case 'J' -> "JP".equals(countryCode) ? "ja_jp" : DEFAULT_LANGUAGE;
            case 'K' -> switch (countryCode) {
                case "KG" -> "ky_kg";
                case "KR" -> "ko_kr";
                case "KZ" -> "kk_kz";
                default -> DEFAULT_LANGUAGE;
            };
            case 'L' -> switch (countryCode) {
                case "LA" -> "lo_la";
                case "LI" -> "li_li";
                case "LT" -> "lt_lt";
                case "LU" -> "lb_lu";
                case "LV" -> "lv_lv";
                default -> DEFAULT_LANGUAGE;
            };
            case 'M' -> switch (countryCode) {
                case "MK" -> "mk_mk";
                case "MN" -> "mn_mn";
                case "MO" -> "zh_hk";
                case "MT" -> "mt_mt";
                case "MX" -> "es_mx";
                case "MY" -> "ms_my";
                default -> DEFAULT_LANGUAGE;
            };
            case 'N' -> switch (countryCode) {
                case "NG" -> "ig_ng";
                case "NL" -> "nl_nl";
                case "NO" -> "no_no";
                case "NZ" -> "en_nz";
                default -> DEFAULT_LANGUAGE;
            };
            case 'P' -> switch (countryCode) {
                case "PH" -> "fil_ph";
                case "PL" -> "pl_pl";
                case "PT" -> "pt_pt";
                default -> DEFAULT_LANGUAGE;
            };
            case 'R' -> switch (countryCode) {
                case "RO" -> "ro_ro";
                case "RS" -> "sr_sp";
                case "RU" -> "ru_ru";
                default -> DEFAULT_LANGUAGE;
            };
            case 'S' -> switch (countryCode) {
                case "SA" -> "ar_sa";
                case "SE" -> "sv_se";
                case "SI" -> "sl_si";
                case "SK" -> "sk_sk";
                case "SO" -> "so_so";
                default -> DEFAULT_LANGUAGE;
            };
            case 'T' -> switch (countryCode) {
                case "TH" -> "th_th";
                case "TR" -> "tr_tr";
                case "TW" -> "zh_tw";
                default -> DEFAULT_LANGUAGE;
            };
            case 'U' -> switch (countryCode) {
                case "UA" -> "uk_ua";
                case "US" -> "en_us";
                case "UY" -> "es_uy";
                default -> DEFAULT_LANGUAGE;
            };
            case 'V' -> switch (countryCode) {
                case "VE" -> "es_ve";
                case "VN" -> "vi_vn";
                default -> DEFAULT_LANGUAGE;
            };
            case 'Z' -> "ZA".equals(countryCode) ? "af_za" : DEFAULT_LANGUAGE;
            default -> DEFAULT_LANGUAGE;
        };
    }

    private static short lookupCountry(InetAddress address) {
        if (address == null) {
            return -1;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return DatabaseHolder.DATABASE.lookupIpv4(ipv4ToInt(bytes, 0));
        }
        if (address instanceof Inet6Address) {
            if (isIpv4Mapped(bytes)) {
                return DatabaseHolder.DATABASE.lookupIpv4(ipv4ToInt(bytes, 12));
            }
            return DatabaseHolder.DATABASE.lookupIpv6(toLong(bytes, 0), toLong(bytes, 8));
        }
        return -1;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16 || bytes[10] != (byte) 0xFF || bytes[11] != (byte) 0xFF) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static int ipv4ToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static long toLong(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFFL);
        }
        return value;
    }

    private static String[] buildCountryCodes() {
        String[] codes = new String[26 * 26];
        for (int first = 0; first < 26; first++) {
            for (int second = 0; second < 26; second++) {
                codes[first * 26 + second] = new String(new char[]{
                        (char) ('A' + first), (char) ('A' + second)
                });
            }
        }
        return codes;
    }

    private static String[] buildLanguageCodes() {
        String[] codes = new String[COUNTRY_CODES.length];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = explicitLanguageCode(COUNTRY_CODES[i]);
        }
        return codes;
    }

    private static final class DatabaseHolder {
        private static final CountryDatabase DATABASE = loadDatabase();
    }

    private static CountryDatabase loadDatabase() {
        InputStream resource = GeoUtil.class.getResourceAsStream(DATABASE_RESOURCE);
        if (resource == null) {
            throw new ExceptionInInitializerError("Missing GeoUtil database " + DATABASE_RESOURCE);
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(resource, 1 << 20), 1 << 20))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid GeoUtil database magic");
            }
            int version = input.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported GeoUtil database version " + version);
            }
            int ipv4Count = checkedCount(input.readInt(), "IPv4");
            int ipv6Count = checkedCount(input.readInt(), "IPv6");
            int[] ipv4Prefixes = readInts(input, PREFIX_COUNT + 1);
            int[] ipv6Prefixes = readInts(input, PREFIX_COUNT + 1);
            int ipv6PageCount = checkedCount(input.readInt(), "IPv6 page");
            if (ipv6PageCount > 0xFFFF) {
                throw new IOException("Too many IPv6 pages: " + ipv6PageCount);
            }
            short[] ipv6PageByPrefix = readShorts(input, PREFIX_COUNT);
            int[] ipv6PageOffsets = readInts(input, Math.multiplyExact(ipv6PageCount, IPV6_PAGE_WIDTH));

            int[] ipv4Starts = new int[ipv4Count];
            int[] ipv4Ends = new int[ipv4Count];
            short[] ipv4Countries = new short[ipv4Count];
            for (int i = 0; i < ipv4Count; i++) {
                ipv4Starts[i] = input.readInt();
                ipv4Ends[i] = input.readInt();
                ipv4Countries[i] = checkedCountry(input.readShort());
            }

            long[] ipv6StartHi = new long[ipv6Count];
            long[] ipv6StartLo = new long[ipv6Count];
            long[] ipv6EndHi = new long[ipv6Count];
            long[] ipv6EndLo = new long[ipv6Count];
            short[] ipv6Countries = new short[ipv6Count];
            for (int i = 0; i < ipv6Count; i++) {
                ipv6StartHi[i] = input.readLong();
                ipv6StartLo[i] = input.readLong();
                ipv6EndHi[i] = input.readLong();
                ipv6EndLo[i] = input.readLong();
                ipv6Countries[i] = checkedCountry(input.readShort());
            }
            if (input.read() != -1) {
                throw new IOException("Trailing data in GeoUtil database");
            }

            validateOffsets(ipv4Prefixes, ipv4Count, "IPv4 prefix");
            validateOffsets(ipv6Prefixes, ipv6Count, "IPv6 prefix");
            validateOffsets(ipv6PageOffsets, ipv6Count, "IPv6 page");
            return new CountryDatabase(
                    ipv4Prefixes, ipv4Starts, ipv4Ends, ipv4Countries,
                    ipv6Prefixes, ipv6PageByPrefix, ipv6PageOffsets,
                    ipv6StartHi, ipv6StartLo, ipv6EndHi, ipv6EndLo, ipv6Countries);
        } catch (EOFException e) {
            throw new ExceptionInInitializerError("Truncated GeoUtil database");
        } catch (IOException | ArithmeticException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static int checkedCount(int count, String name) throws IOException {
        if (count < 0 || count > MAX_RANGE_COUNT) {
            throw new IOException("Invalid " + name + " count: " + count);
        }
        return count;
    }

    private static short checkedCountry(short country) throws IOException {
        if (country < 0 || country >= COUNTRY_CODES.length) {
            throw new IOException("Invalid encoded country: " + country);
        }
        return country;
    }

    private static int[] readInts(DataInputStream input, int count) throws IOException {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = input.readInt();
        }
        return values;
    }

    private static short[] readShorts(DataInputStream input, int count) throws IOException {
        short[] values = new short[count];
        for (int i = 0; i < count; i++) {
            values[i] = input.readShort();
        }
        return values;
    }

    private static void validateOffsets(int[] offsets, int rangeCount, String name) throws IOException {
        int previous = 0;
        for (int offset : offsets) {
            if (offset < previous || offset > rangeCount) {
                throw new IOException("Invalid " + name + " offset: " + offset);
            }
            previous = offset;
        }
    }

    private record CountryDatabase(
            int[] ipv4Prefixes,
            int[] ipv4Starts,
            int[] ipv4Ends,
            short[] ipv4Countries,
            int[] ipv6Prefixes,
            short[] ipv6PageByPrefix,
            int[] ipv6PageOffsets,
            long[] ipv6StartHi,
            long[] ipv6StartLo,
            long[] ipv6EndHi,
            long[] ipv6EndLo,
            short[] ipv6Countries
    ) {
        private short lookupIpv4(int ip) {
            int prefix = ip >>> 16;
            int low = Math.max(0, ipv4Prefixes[prefix] - 1);
            int high = ipv4Prefixes[prefix + 1];
            int candidate = floorIpv4(ip, low, high);
            return candidate >= 0 && Integer.compareUnsigned(ip, ipv4Ends[candidate]) <= 0
                    ? ipv4Countries[candidate]
                    : -1;
        }

        private int floorIpv4(int ip, int low, int highExclusive) {
            int high = highExclusive - 1;
            int result = -1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (Integer.compareUnsigned(ipv4Starts[middle], ip) <= 0) {
                    result = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            return result;
        }

        private short lookupIpv6(long ipHi, long ipLo) {
            int prefix16 = (int) (ipHi >>> 48);
            int low;
            int high;
            int page = Short.toUnsignedInt(ipv6PageByPrefix[prefix16]);
            if (page == 0) {
                low = Math.max(0, ipv6Prefixes[prefix16] - 1);
                high = ipv6Prefixes[prefix16 + 1];
            } else {
                int prefix8 = (int) ((ipHi >>> 40) & 0xFF);
                int base = (page - 1) * IPV6_PAGE_WIDTH;
                low = Math.max(0, ipv6PageOffsets[base + prefix8] - 1);
                high = ipv6PageOffsets[base + prefix8 + 1];
            }

            int candidate = floorIpv6(ipHi, ipLo, low, high);
            return candidate >= 0
                            && compare128(ipHi, ipLo, ipv6EndHi[candidate], ipv6EndLo[candidate]) <= 0
                    ? ipv6Countries[candidate]
                    : -1;
        }

        private int floorIpv6(long ipHi, long ipLo, int low, int highExclusive) {
            int high = highExclusive - 1;
            int result = -1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (compare128(ipv6StartHi[middle], ipv6StartLo[middle], ipHi, ipLo) <= 0) {
                    result = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            return result;
        }

        private static int compare128(long leftHi, long leftLo, long rightHi, long rightLo) {
            int high = Long.compareUnsigned(leftHi, rightHi);
            return high != 0 ? high : Long.compareUnsigned(leftLo, rightLo);
        }
    }
}
