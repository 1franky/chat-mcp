FROM eclipse-temurin:21.0.11_10-jdk-noble@sha256:35685c7e23352983a48882d97cd9875f5284c228db71d1e2476e5e6c1bab1080 AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
COPY config/ config/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21.0.11_10-jre-noble@sha256:373787d1d45a87f084fda43e7de0e9acf5eedee049446efac738f13587ec4c64

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app /var/lib/ai-data-chat/documents \
    && chown -R app:app /app /var/lib/ai-data-chat

COPY --from=build --chown=app:app \
    /workspace/target/ai-data-chat-backend-0.1.0-SNAPSHOT.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
