FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :settlement:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/settlement/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
