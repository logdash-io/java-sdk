FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

# The SDK version to install from Maven Central, into this image's empty local repository
ARG LOGDASH_VERSION
ENV LOGDASH_VERSION=${LOGDASH_VERSION}

COPY check-deployed-package/pom.xml .
COPY check-deployed-package/Check.java src/main/java/io/logdash/check/Check.java

RUN mvn compile -B --no-transfer-progress -Dlogdash.version="$LOGDASH_VERSION"

CMD ["sh", "-c", "exec mvn exec:java -B --no-transfer-progress -q -Dlogdash.version=\"$LOGDASH_VERSION\""]
