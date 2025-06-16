FROM eclipse-temurin:17-jdk

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy project files
COPY check-deployed-package/pom.xml .
COPY check-deployed-package/Check.java src/main/java/com/logdash/check/Check.java

# Download dependencies and compile
RUN mvn dependency:resolve compile -B --no-transfer-progress

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
