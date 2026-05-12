FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY common ./common
# 复制其他模块的 pom.xml 占位，让父 POM 的模块检查通过（不需要源码）
COPY user-service/pom.xml     ./user-service/pom.xml
COPY gateway-service/pom.xml  ./gateway-service/pom.xml
COPY customer-service/pom.xml ./customer-service/pom.xml
COPY shop-service/pom.xml     ./shop-service/pom.xml
COPY order-service/pom.xml    ./order-service/pom.xml
COPY driver-service/pom.xml   ./driver-service/pom.xml
COPY logistics-service/pom.xml ./logistics-service/pom.xml
# 复制本服务源码并构建
COPY auth-service ./auth-service
RUN mvn package -pl auth-service -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/auth-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
