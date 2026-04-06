FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY notification/build/libs/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
