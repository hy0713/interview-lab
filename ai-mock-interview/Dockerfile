FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src/main ./src/main
RUN mvn -B -ntp package -Dmaven.test.skip=true
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/ai-mock-interview-2.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
