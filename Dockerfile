FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY .mvn/settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/carecopilot-backend-0.1.0-SNAPSHOT.jar /app/carecopilot.jar
RUN useradd --system --uid 10001 --home-dir /app carecopilot \
  && chown -R carecopilot:carecopilot /app
USER 10001
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar /app/carecopilot.jar"]
