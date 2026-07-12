# Geo database update

`GeoUtil` uses the PDDL-licensed `user-country` database from
[`sapics/ip-location-db`](https://github.com/sapics/ip-location-db). The generated resource is
committed so production servers never download or parse geolocation data at runtime.

To refresh it:

```bash
curl -fsSL https://github.com/sapics/ip-location-db/releases/download/latest/user-country-ipv4-num.csv \
  -o /tmp/user-country-ipv4-num.csv
curl -fsSL https://github.com/sapics/ip-location-db/releases/download/latest/user-country-ipv6.csv \
  -o /tmp/user-country-ipv6.csv
/path/to/jdk/bin/javac -d /tmp/geo-compiler tools/geo/GeoDatabaseCompiler.java
/path/to/jdk/bin/java -cp /tmp/geo-compiler GeoDatabaseCompiler \
  /tmp/user-country-ipv4-num.csv \
  /tmp/user-country-ipv6.csv \
  src/main/resources/geo/user-country-v1.dat.gz
```

The binary format is versioned. If the compiler layout changes, update `FORMAT_VERSION` in both
the compiler and `GeoUtil`.

For an exhaustive endpoint check against both source files:

```bash
/path/to/jdk/bin/javac -d /tmp/geo-verify \
  src/main/java/org/encinet/mik/util/GeoUtil.java tools/geo/GeoDatabaseVerifier.java
/path/to/jdk/bin/java \
  -cp /tmp/geo-verify:src/main/resources GeoDatabaseVerifier \
  /tmp/user-country-ipv4-num.csv /tmp/user-country-ipv6.csv
```
