FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY common ./common
COPY auth-service/pom.xml     ./auth-service/pom.xml
COPY user-service/pom.xml     ./user-service/pom.xml
COPY customer-service/pom.xml ./customer-service/pom.xml
COPY shop-service/pom.xml     ./shop-service/pom.xml
COPY order-service/pom.xml    ./order-service/pom.xml
COPY driver-service/pom.xml   ./driver-service/pom.xml
COPY logistics-service/pom.xml ./logistics-service/pom.xml
COPY gateway-service ./gateway-service
RUN mvn package -pl gateway-service -am -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/gateway-service/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
