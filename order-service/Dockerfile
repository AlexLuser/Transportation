FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY common ./common
COPY auth-service/pom.xml     ./auth-service/pom.xml
COPY user-service/pom.xml     ./user-service/pom.xml
COPY gateway-service/pom.xml  ./gateway-service/pom.xml
COPY customer-service/pom.xml ./customer-service/pom.xml
COPY shop-service/pom.xml     ./shop-service/pom.xml
COPY driver-service/pom.xml   ./driver-service/pom.xml
COPY logistics-service/pom.xml ./logistics-service/pom.xml
COPY order-service ./order-service
RUN mvn package -pl order-service -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/order-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
