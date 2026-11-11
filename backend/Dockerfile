# syntax=docker/dockerfile:1

# Etapa de compilación: JDK 21 y Maven Wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/main/ src/main/
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -Dmaven.test.skip=true package \
    && cp target/arise-backend-*.jar /workspace/app.jar

# Etapa de ejecución: JRE 21 con solo el JAR.
FROM eclipse-temurin:21-jre
RUN groupadd --system arise \
    && useradd --system --gid arise --no-create-home --shell /usr/sbin/nologin arise
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER arise
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
