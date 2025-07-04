# Logdash Java SDK

[![GitHub Release](https://img.shields.io/github/v/release/logdash-io/java-sdk)](https://github.com/logdash-io/java-sdk/releases)
[![Maven Central](https://img.shields.io/maven-central/v/io.logdash/logdash)](https://central.sonatype.com/artifact/io.logdash/logdash)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-17+-blue.svg)](https://openjdk.java.net/projects/jdk/17/)
[![CI/CD](https://github.com/logdash-io/java-sdk/workflows/CI/CD%20Pipeline/badge.svg)](https://github.com/logdash-io/java-sdk/actions)
[![Coverage](https://img.shields.io/codecov/c/github/logdash-io/java-sdk)](https://codecov.io/gh/logdash-io/java-sdk)

> **Official Java SDK for [Logdash.io](https://logdash.io/) - Zero-configuration observability platform designed for developers working on side projects and prototypes.**

## Why Logdash?

Most observability solutions feel overwhelming for small projects and prototypes. Logdash provides **instant logging and real-time metrics** without complex configurations. Just add the SDK and start monitoring your application immediately.

## Key Features

- **🚀 Zero Configuration**: Start logging and tracking metrics in seconds
- **📊 Real-time Dashboard**: Cloud-hosted interface with live data updates
- **📝 Structured Logging**: Multiple log levels with rich context support
- **📈 Custom Metrics**: Track counters, gauges, and business metrics effortlessly
- **⚡ Asynchronous**: Non-blocking operations with automatic resource management
- **🛡️ Production Ready**: Built with enterprise-grade patterns and error handling
- **🔧 Framework Agnostic**: Works with Spring Boot, Quarkus, Micronaut, or standalone apps
- **☕ Java 17+ Compatible**: Supports Java 17, 21, and all newer versions

## Pre-requisites

Setup your free project in less than 2 minutes at [logdash.io](https://logdash.io/)

## Installation

### Maven Central

```xml
<dependency>
    <groupId>io.logdash</groupId>
    <artifactId>logdash</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Gradle

```groovy
dependencies {
    implementation 'io.logdash:logdash:0.2.0'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.logdash:logdash:0.2.0")
}
```

### Local Installation

```bash
# Clone and install locally
git clone https://github.com/logdash-io/java-sdk.git
cd java-sdk
mvn clean install
```

Then use in your project:

```xml
<dependency>
    <groupId>io.logdash</groupId>
    <artifactId>logdash</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Quick Start

### Basic Usage

```java
import io.logdash.sdk.Logdash;
import java.util.Map;

public class Application {
    public static void main(String[] args) {
        // Initialize with your API key from https://logdash.io
        var logdash = Logdash.builder()
                .apiKey("your-api-key-here")
                .build();

        var logger = logdash.logger();
        var metrics = logdash.metrics();

        // Start logging and tracking metrics immediately
        logger.info("Application started");
        metrics.mutate("app_starts", 1);

        // Your application logic here
        processUserRequest(logger, metrics);

        // Graceful shutdown (optional)
        logdash.flush();
        logdash.close();
    }

    private static void processUserRequest(var logger, var metrics) {
        logger.info("Processing user request", Map.of(
                "userId", 12345,
                "endpoint", "/api/users",
                "method", "GET"
        ));

        metrics.set("active_users", 150);
        metrics.mutate("api_requests", 1);
        metrics.mutate("response_time_ms", -25);
    }
}
```

### Try-with-resources Pattern

```java
public class Application {
    public static void main(String[] args) {
        try (var logdash = Logdash.create("your-api-key")) {
            logdash.logger().info("Application started");
            logdash.metrics().mutate("app.starts", 1);

            // Your application code here

            // Automatic cleanup on close
        }
    }
}
```

## Framework Integration

### Spring Boot

**Configuration:**

```yaml
# application.yml
logdash:
  api-key: ${LOGDASH_API_KEY:your-development-key}
  enable-console-output: false
```

**Integration:**

```java
@SpringBootApplication
public class Application {

    @Value("${logdash.api-key}")
    private String logdashApiKey;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "logdash.api-key")
    public Logdash logdash() {
        return Logdash.builder()
                .apiKey(logdashApiKey)
                .enableConsoleOutput(false) // Use Spring's logging
                .build();
    }
}

@RestController
public class UserController {

    private final LogdashLogger logger;
    private final LogdashMetrics metrics;

    public UserController(Logdash logdash) {
        this.logger = logdash.logger();
        this.metrics = logdash.metrics();
    }

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        logger.info("Fetching user", Map.of("userId", id));
        metrics.mutate("user.fetch.requests", 1);

        var user = userService.findById(id);

        logger.debug("User retrieved", Map.of(
                "userId", id,
                "username", user.getUsername()
        ));

        return user;
    }
}
```

### Standalone Applications

```java
public class StandaloneApp {
    public static void main(String[] args) {
        var logdash = Logdash.builder()
                .apiKey(System.getenv("LOGDASH_API_KEY"))
                .build();

        var logger = logdash.logger();
        var metrics = logdash.metrics();

        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Application shutting down");
            logdash.close();
        }));

        // Your application logic
        runApplication(logger, metrics);
    }
}
```

## Logging

### Log Levels & Context

```java
var logger = logdash.logger();

// Simple messages
logger.error("Database connection failed");
logger.warn("High memory usage detected");
logger.info("User session started");
logger.http("GET /api/health - 200 OK");
logger.verbose("Loading user preferences");
logger.debug("Cache hit for key: user_123");
logger.silly("Detailed trace information");

// Structured logging with context
logger.info(
    "Payment processed",
    Map.of(
        "userId", 12345,
        "amount", 29.99,
        "currency", "USD",
        "paymentMethod", "credit_card",
        "transactionId", "txn_abc123"
    )
);

logger.error(
    "API call failed",
    Map.of(
        "endpoint", "https://external-api.com/data",
        "statusCode", 503,
        "retryAttempt", 3,
        "errorMessage", "Service temporarily unavailable"
    )
);
```

## Metrics

Track business and technical metrics to monitor application performance:

```java
var metrics = logdash.metrics();

// Counters - track events and occurrences
metrics.mutate("page_views", 1);
metrics.mutate("api_requests", 5);
metrics.mutate("response_time_ms", -100);
metrics.mutate("available_licenses", -1);

// Gauges - track current values
metrics.set("active_users", 1_250);
metrics.set("memory_usage_mb", 512.5);
metrics.set("queue_size", 0);
```

## View Your Data

To see the logs or metrics, go to your project dashboard at [logdash.io](https://logdash.io/app/clusters)

![Logs Dashboard](docs/images/logs.png)

![Metrics Dashboard](docs/images/delta.png)

## Configuration

### Development Configuration

```java
Logdash logdash = Logdash.builder()
        .apiKey("your-api-key")
        .enableConsoleOutput(true)      // See logs locally
        .enableVerboseLogging(true)     // Debug SDK behavior
        .build();
```

### Production Configuration

```java
Logdash logdash = Logdash.builder()
        .apiKey(System.getenv("LOGDASH_API_KEY"))
        .enableConsoleOutput(false)     // Use your app's logging
        .enableVerboseLogging(false)    // Disable debug output
        .requestTimeoutMs(10000)        // 10s timeout
        .maxRetries(5)                  // More retries in prod
        .maxConcurrentRequests(20)      // Higher concurrency
        .build();
```

## Configuration

### All Configuration Options

|        Parameter        |         Default          |           Description            |
|-------------------------|--------------------------|----------------------------------|
| `apiKey`                | `null`                   | Your Logdash API key (required)  |
| `baseUrl`               | `https://api.logdash.io` | Logdash API endpoint             |
| `enableConsoleOutput`   | `true`                   | Show logs/metrics in console     |
| `enableVerboseLogging`  | `false`                  | Enable SDK debug output          |
| `requestTimeoutMs`      | `15000`                  | HTTP request timeout             |
| `maxRetries`            | `3`                      | Failed request retry attempts    |
| `retryDelayMs`          | `500`                    | Base delay between retries       |
| `maxConcurrentRequests` | `10`                     | Maximum concurrent HTTP requests |
| `shutdownTimeoutMs`     | `10000`                  | Graceful shutdown timeout        |

## Performance Considerations

- **Initialize once** at application startup and reuse the instance
- **Use structured logging** for better searchability and performance
- **Avoid logging sensitive data** (passwords, tokens, PII)
- **The SDK is async** - logging calls return immediately without blocking
- **Memory efficient** - automatic batching and resource management

## Getting Your API Key

1. Visit [Logdash.io](https://logdash.io)
2. Create a free account
3. Generate your API key in the dashboard
4. View live demo at [demo-dashboard](https://logdash.io/demo-dashboard)

## Examples

Check out the [`examples/`](./examples) directory for a complete sample application demonstrating:

- Basic SDK usage patterns
- Structured logging examples
- Metrics tracking patterns
- Error handling strategies
- Framework integration examples

## Troubleshooting

### Common Issues

**Logs not appearing in dashboard:**

- Verify your API key is correct and active
- Check network connectivity to `api.logdash.io`
- Enable verbose logging to see HTTP requests: `.enableVerboseLogging(true)`

**High latency or timeouts:**

- Increase `requestTimeoutMs` for slower networks
- Reduce `maxConcurrentRequests` to limit resource usage
- Check your network connection stability

**Missing logs in production:**

- Ensure SDK is initialized before first log call
- Verify API key environment variable is set
- Check firewall settings for outbound HTTPS (port 443)

## Requirements

- **Java 17 or higher** (tested on Java 17, 21, 22)
- **Internet connection** for sending data to Logdash
- **Valid API key** from [logdash.io](https://logdash.io)

## Contributing

We welcome contributions! Here's how to get started:

### Development Setup

```bash
git clone https://github.com/logdash-io/java-sdk.git
cd java-sdk
mvn clean compile
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests  
mvn failsafe:integration-test

# All tests with coverage
mvn clean verify
```

### GitFlow Workflow

```bash
# Start new feature
git checkout dev && git pull origin dev
git checkout -b feat/awesome-feature

# Development and testing
mvn clean verify
git add . && git commit -m "feat: add awesome feature"
git push origin feat/awesome-feature

# Create pull request to dev branch
```

### Code Quality

- Follow existing code style
- Ensure all tests pass
- Add tests for new features
- Update documentation as needed

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support & Resources

- 📖 **Documentation**: [https://logdash.io/docs](https://logdash.io/docs)
- 🚀 **Live Demo**: [https://logdash.io/demo-dashboard](https://logdash.io/demo-dashboard)
- 🐛 **Issues**: [GitHub Issues](https://github.com/logdash-io/java-sdk/issues)
- 💬 **Community**: [Discord Server](https://discord.gg/naftPW4Hxe)
- 📧 **Support**: [contact@logdash.io](mailto:contact@logdash.io)
- 🔗 **Main Repository**: [https://github.com/logdash-io/](https://github.com/logdash-io/)

---

**Made with ❤️ for developers who want simple, effective observability without the complexity.**
