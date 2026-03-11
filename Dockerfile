# ============ Stage 1: Build ============
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml first and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the JAR
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -B

# ============ Stage 2: Run ============
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Set timezone to IST
ENV TZ=Asia/Kolkata
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone && \
    apk del tzdata

# Copy the built JAR from the build stage
COPY --from=build /app/target/Workforce-0.0.1-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 8080

# Environment variables (can be overridden at runtime)
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Kolkata"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
