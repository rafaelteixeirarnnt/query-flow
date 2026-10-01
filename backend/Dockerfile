FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S queryhub && adduser -S queryhub -G queryhub
WORKDIR /app
COPY --from=build /build/target/queryhub-0.1.0.jar app.jar
USER queryhub
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","app.jar"]
