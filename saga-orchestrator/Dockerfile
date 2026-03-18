FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :saga-orchestrator:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/saga-orchestrator/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
