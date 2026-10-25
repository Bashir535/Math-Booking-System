FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src/ ./src/
COPY --from=frontend /frontend/dist/ ./src/main/resources/static/
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S booking && adduser -S -G booking booking
WORKDIR /app
COPY --from=backend --chown=booking:booking /build/target/booking-system-0.0.1-SNAPSHOT.jar app.jar
USER booking
EXPOSE 8090
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8090/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
