# Builder image
FROM adoptopenjdk/maven-openjdk11 as builder

# Set working dir
WORKDIR /app

# Copy source file
COPY src src
COPY pom.xml .

# Build project
RUN mvn clean package -DskipTests

# Pull base image
FROM yti-docker-java11-base:alpine

ENV TZ=Europe/Ljubljana

RUN apk add --no-cache tzdata

# Copy from builder 
COPY --from=builder /app/target/termed-api-exec.jar ${deploy_dir}/termed-api-exec.jar

# Set default command on run
ENTRYPOINT ["/bootstrap.sh", "termed-api-exec.jar", "-j", "-Djava.security.egd=file:/dev/./urandom"]
