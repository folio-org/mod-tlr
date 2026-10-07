FROM docker.io/folioci/eclipse-temurin:25-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
