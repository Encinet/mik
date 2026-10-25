# 地理数据库

`GeoUtil` 使用 [`sapics/ip-location-db`](https://github.com/sapics/ip-location-db) 的 PDDL 许可 `user-country` 数据。编译后的资源随插件发布，服务器运行时离线查询。

在项目根目录执行以下命令，`java` 和 `javac` 使用 JDK 25。

## 下载数据

```bash
curl -fsSL https://github.com/sapics/ip-location-db/releases/download/latest/user-country-ipv4-num.csv \
  -o /tmp/user-country-ipv4-num.csv
curl -fsSL https://github.com/sapics/ip-location-db/releases/download/latest/user-country-ipv6.csv \
  -o /tmp/user-country-ipv6.csv
```

## 编译与校验

先编译工具，再生成资源并逐段校验 IPv4、IPv6 的起止地址：

```bash
javac -d /tmp/mik-geo-tools \
  tools/geo/GeoDatabaseCompiler.java \
  tools/geo/GeoDatabaseVerifier.java \
  src/main/java/org/encinet/mik/shared/util/GeoUtil.java
java -cp /tmp/mik-geo-tools GeoDatabaseCompiler \
  /tmp/user-country-ipv4-num.csv \
  /tmp/user-country-ipv6.csv \
  src/main/resources/geo/user-country-v1.dat.gz
java -cp /tmp/mik-geo-tools:src/main/resources GeoDatabaseVerifier \
  /tmp/user-country-ipv4-num.csv /tmp/user-country-ipv6.csv
```

校验器同时输出查询耗时。通过后提交生成的 `src/main/resources/geo/user-country-v1.dat.gz`。

修改二进制布局时，同步更新[编译器](GeoDatabaseCompiler.java)与 [GeoUtil](../../src/main/java/org/encinet/mik/shared/util/GeoUtil.java) 的 `FORMAT_VERSION`，重新生成并校验资源。
