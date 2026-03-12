FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :risk:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/risk/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
