# BetterReads

Book tracking and recommendation platform built with Spring Boot. Uses OpenLibrary as an external data source with local caching and persistence.

## Tech Stack

Java 25 · Spring Boot 3.5.11 · PostgreSQL · Flyway · Caffeine · WebClient

## Getting Started

```bash
./gradlew build
./gradlew test
export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" && ./gradlew check
```

## Project Structure

```
src/           Java source and resources
config/        Quality check configs (Checkstyle, PMD, dependency-check)
docs/          Planning docs and architecture notes
```

## Documentation

- [Overview and Search](docs/01-overview-and-search.md)
- [Backend Architecture and Roadmap](docs/02-backend-architecture-and-roadmap.md)
- [Recommendations and ML](docs/03-recommendations-and-ml.md)
- [Deployment and Frontend](docs/04-deployment-and-frontend.md)
- [Project Structure](docs/05-project-structure.md)
- [Database Schema](docs/06-database-schema.md)
- [Current Phase](docs/roadmap/current-phase.md)
