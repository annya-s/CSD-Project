# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
# Preserve successful downloads across builds and retry interrupted transfers.
# -U also retries dependencies marked missing by a previous failed download.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    for attempt in 1 2 3; do \
        mvn -B -U -Dmaven.test.skip=true package && exit 0; \
        if [ "$attempt" -eq 3 ]; then exit 1; fi; \
        echo "Maven attempt $attempt failed; retrying in 3 seconds..."; \
        sleep 3; \
    done

FROM python:3.12-slim-bookworm
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jre-headless libgomp1 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY ml/requirements.txt /app/ml/requirements.txt
RUN python -m venv /opt/venv \
    && /opt/venv/bin/python -m pip install --no-cache-dir -r /app/ml/requirements.txt
COPY ml/*.py ml/yield_locations.json /app/ml/
COPY ml/model/yield/ /app/ml/model/yield/
COPY --from=build /build/target/farm-0.0.1-SNAPSHOT.jar /app/app.jar
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
EXPOSE 8080
CMD ["java", "-Dml.directory=/app/ml", "-Dml.python=/opt/venv/bin/python", "-Dserver.address=0.0.0.0", "-jar", "/app/app.jar"]
