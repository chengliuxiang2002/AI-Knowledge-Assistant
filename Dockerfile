FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=build /app/target/ai-assistant-1.0.0.jar app.jar
EXPOSE 8123
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
