# ── Stage 1: Build ──────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom first so dependency layer is cached
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Run ────────────────────────────────────────
# JDK (not JRE) so POST /api/code/execute has `javac` to compile user Java code,
# alongside the toolchains for Python and C/C++ (Java 21 already in this image).
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

RUN apk add --no-cache python3 gcc g++ musl-dev

COPY --from=build /app/target/Student-BackEnd-0.0.1-SNAPSHOT.jar app.jar

# Container-aware heap sizing for small Render instances. G1GC copes with memory
# pressure better than the default; string dedup trims heap for repeated cached JSON;
# Xss512k halves per-thread stack (40 threads → ~20MB saved).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Xss512k"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
