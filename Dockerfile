FROM maven:3.8.4-openjdk-17-slim AS cert-generation
WORKDIR /app
COPY . .
RUN chmod +x ./docker/tls/generate-certificates.sh
RUN ./docker/tls/generate-certificates.sh

FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY --from=cert-generation /app /app
RUN mvn clean install -DskipTests

FROM openjdk:17.0.1-jdk-slim
WORKDIR /app
COPY --from=build /app/siga-webapp/target/siga-webapp-*.jar app.jar

EXPOSE 8443

CMD ["java", "-jar", "app.jar"]
