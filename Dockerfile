FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY build/libs/urlshortener.jar app.jar
ENV SPRING_PROFILES_ACTIVE=docker,broker
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
