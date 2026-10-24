#!/bin/bash
set -e

echo "🔧 Building local Logdash SDK..."
mvn clean install -DskipTests

echo "🚀 Testing SpringBoot example with local snapshot..."
cd examples/example-springboot
mvn clean spring-boot:run -Plocal-dev -Dlogdash-sdk.version=0.0.0-SNAPSHOT

echo "✅ Development cycle completed!"
