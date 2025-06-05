FROM eclipse-temurin:17-jdk

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy pom.xml and source files
COPY check-deployed-package/pom.xml .
COPY check-deployed-package/Check.java src/main/java/com/logdash/check/Check.java

# Download dependencies and compile
RUN mvn compile

CMD ["mvn", "exec:java"] 