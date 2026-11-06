FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home aulacita
WORKDIR /app
COPY --from=build /workspace/target/aulacita-1.0.0-SNAPSHOT.jar /app/aulacita.jar
RUN mkdir -p /app/uploads && chown -R 10001:10001 /app
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/aulacita.jar"]
