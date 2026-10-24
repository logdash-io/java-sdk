#!/bin/sh
# Checks the SDK as Maven Central serves it: installs it in a clean container, sends logs and metrics
# with it, and reads them back.
#   LOGDASH_API_KEY: the ingest key of the smoke-test project, which sends.
#   LOGDASH_PERSONAL_API_KEY: an ldp_ personal API key with logs:read and metrics:read, restricted to
#     that one project, which reads back.
#   LOGDASH_VERSION: the version to check; empty checks the latest release.
#   LOGDASH_API_URL: the API both halves talk to, https://api.logdash.io by default.
set -eu

: "${LOGDASH_API_KEY:?must be the ingest key of the smoke-test project}"
: "${LOGDASH_PERSONAL_API_KEY:?must be a personal API key that reads the smoke-test project}"
API=${LOGDASH_API_URL:-https://api.logdash.io}
CENTRAL=https://repo.maven.apache.org/maven2/io/logdash/logdash
VERSION=${LOGDASH_VERSION:-$(curl -fsS "$CENTRAL/maven-metadata.xml" | sed -n 's:.*<release>\(.*\)</release>.*:\1:p')}
VERSION=${VERSION#v}
if [ -z "$VERSION" ]; then
    echo "Error: no version to check, and Maven Central names no latest release"
    exit 1
fi

# A release reaches Maven Central some minutes after the Central Portal publishes it.
echo "Waiting until Maven Central serves logdash $VERSION..."
tries=0
until curl -fsIo /dev/null "$CENTRAL/$VERSION/logdash-$VERSION.pom" && curl -fsIo /dev/null "$CENTRAL/$VERSION/logdash-$VERSION.jar"; do
    tries=$((tries + 1))
    if [ "$tries" -ge 120 ]; then
        echo "Error: Maven Central does not serve logdash $VERSION after an hour"
        exit 1
    fi
    sleep 30
done

LOGS_SEED=$(openssl rand -hex 4)
METRICS_SEED=$(awk 'BEGIN { srand(); print int(rand() * 1000000) + 1 }')
echo "Logs seed: $LOGS_SEED, metrics seed: $METRICS_SEED"

docker build --no-cache --build-arg LOGDASH_VERSION="$VERSION" -t logdash-java-check -f check-deployed-package/Dockerfile .
docker run --rm -e LOGDASH_API_KEY -e LOGDASH_API_URL="$API" -e LOGS_SEED="$LOGS_SEED" -e METRICS_SEED="$METRICS_SEED" logdash-java-check

# GETs an API path with the personal key: prints the response, and fails on an HTTP error.
get() {
    curl -sS --fail-with-body -H "Authorization: Bearer $LOGDASH_PERSONAL_API_KEY" "$API$1"
}

WHOAMI=$(get /personal-api-keys/whoami) || {
    echo "Error: whoami rejects LOGDASH_PERSONAL_API_KEY: $WHOAMI"
    exit 1
}
PROJECT_ID=$(printf '%s' "$WHOAMI" | jq -r 'if .access.kind == "projects" and (.access.ids | length) == 1 then .access.ids[0] else empty end')
if [ -z "$PROJECT_ID" ]; then
    echo "Error: LOGDASH_PERSONAL_API_KEY must be restricted to the one smoke-test project: $WHOAMI"
    exit 1
fi
echo "Reading back from project $PROJECT_ID"

EXPECTED_LOGS=$(jq -n --arg seed "$LOGS_SEED" '[
    ["info", "This is an info log \($seed)"],
    ["error", "This is an error log \($seed)"],
    ["warning", "This is a warning log \($seed)"],
    ["debug", "This is a debug log \($seed)"],
    ["http", "This is a http log \($seed)"],
    ["silly", "This is a silly log \($seed)"],
    ["verbose", "This is a verbose log \($seed)"]
]')
EXPECTED_USERS=$((METRICS_SEED + 1))

# Ingestion is asynchronous, so each read-back retries for a minute.
LOGS='' MISSING='' tries=0
until
    LOGS=$(get "/projects/$PROJECT_ID/logs/v2?limit=100&searchString=$LOGS_SEED") &&
        MISSING=$(printf '%s' "$LOGS" | jq -r --argjson want "$EXPECTED_LOGS" '$want - [.[] | [.level, .message]] | .[] | join(": ")') &&
        [ -z "$MISSING" ]
do
    tries=$((tries + 1))
    if [ "$tries" -ge 12 ]; then
        printf 'Error: logs not found:\n%s\nResponse: %s\n' "$MISSING" "$LOGS"
        exit 1
    fi
    sleep 5
done
echo "✓ Found a log of each of the 7 levels"

METRICS='' USERS='' tries=0
until
    METRICS=$(get "/projects/$PROJECT_ID/metrics") &&
        USERS=$(printf '%s' "$METRICS" | jq -r '.[] | select(.name == "users_java") | .value') &&
        [ "$USERS" = "$EXPECTED_USERS" ]
do
    tries=$((tries + 1))
    if [ "$tries" -ge 12 ]; then
        echo "Error: metric users_java is ${USERS:-missing}, expected $EXPECTED_USERS. Response: $METRICS"
        exit 1
    fi
    sleep 5
done
echo "✓ Found metric users_java = $USERS"
