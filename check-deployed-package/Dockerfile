FROM eclipse-temurin:17-jdk

ARG GITHUB_TOKEN
ARG GITHUB_ACTOR

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Set environment variables for Maven authentication
ENV GITHUB_TOKEN=${GITHUB_TOKEN}
ENV GITHUB_ACTOR=${GITHUB_ACTOR}

# Copy Maven settings with GitHub authentication
COPY check-deployed-package/settings.xml /root/.m2/settings.xml

# Copy project files
COPY check-deployed-package/pom.xml .
COPY check-deployed-package/Check.java src/main/java/com/logdash/check/Check.java

# Download dependencies and compile
RUN mvn dependency:resolve compile -B --no-transfer-progress \
    -Dgithub.username=${GITHUB_ACTOR} \
    -Dgithub.password=${GITHUB_TOKEN}

# Add environment variable validation
ENV LOGDASH_API_KEY=""
ENV LOGS_SEED=""
ENV METRICS_SEED=""

# Run the application with environment validation
CMD if [ -z "$LOGDASH_API_KEY" ]; then \
        echo "Error: LOGDASH_API_KEY environment variable is required"; \
        exit 1; \
    fi; \
    mvn exec:java -B --no-transfer-progress
