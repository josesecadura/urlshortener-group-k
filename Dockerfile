# Compilamos el proyecto dentro de Docker para no depender de un JAR local.
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY . .
RUN sed -i 's/\r$//' ./gradlew \
    && chmod +x ./gradlew \
    && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /app/build/libs/urlshortener.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]
