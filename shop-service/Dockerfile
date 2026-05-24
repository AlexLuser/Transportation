FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY common ./common
COPY auth-service/pom.xml     ./auth-service/pom.xml
COPY user-service/pom.xml     ./user-service/pom.xml
COPY gateway-service/pom.xml  ./gateway-service/pom.xml
COPY customer-service/pom.xml ./customer-service/pom.xml
COPY order-service/pom.xml    ./order-service/pom.xml
COPY driver-service/pom.xml   ./driver-service/pom.xml
COPY logistics-service/pom.xml ./logistics-service/pom.xml
COPY shop-service ./shop-service
RUN mvn package -pl shop-service -am -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/shop-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
