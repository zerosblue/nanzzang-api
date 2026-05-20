FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon && \
    find build/libs -name "*.jar" ! -name "*plain*" -exec cp {} app.jar \;

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/app.jar app.jar
RUN mkdir -p uploads
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Djava.net.preferIPv4Stack=true", \
  "-Xmx512m", "-Xms256m", \
  "-jar", "app.jar"]
