FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY common ./common
COPY auth-service/pom.xml     ./auth-service/pom.xml
COPY user-service/pom.xml     ./user-service/pom.xml
COPY gateway-service/pom.xml  ./gateway-service/pom.xml
COPY customer-service/pom.xml ./customer-service/pom.xml
COPY shop-service/pom.xml     ./shop-service/pom.xml
COPY order-service/pom.xml    ./order-service/pom.xml
COPY logistics-service/pom.xml ./logistics-service/pom.xml
COPY driver-service ./driver-service
RUN mvn package -pl driver-service -am -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/driver-service/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
