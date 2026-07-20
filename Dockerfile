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

# Container-aware heap sizing for small Render instances.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
