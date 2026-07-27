FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :payment:bootJar -x test --no-daemon

# run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/payment/build/libs/*-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
