
# Stage 1: Build the Spring Boot application
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw

RUN ./mvnw -B dependency:go-offline

COPY src/ src/

RUN ./mvnw -B -DskipTests package

# Stage 2: Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 appuser

COPY --from=build --chown=10001:10001 \
    /app/target/ballot-1.0.0.jar /app/app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
