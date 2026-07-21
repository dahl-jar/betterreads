# Project structure

```text
src/main/java/com/betterreads/<feature>/       feature controllers, services, repositories, entities, DTOs, and mappers
src/main/java/com/betterreads/integration/     external API and storage adapters
src/main/java/com/betterreads/common/          shared web, exception, scheduling, crypto, and utility code
src/main/resources/db/migration/               Flyway migrations
src/test/java/                                 deterministic tests and architecture rules
src/liveTest/java/                             tests that call external services
src/localDbVerification/java/                  operator checks against a configured database
config/                                        static-analysis configuration
```

Catalog source code has four packages:

- `catalog/service/source/model` contains source records and enums.
- `catalog/service/source/port` contains source interfaces and response carriers.
- `catalog/service/source/merge` contains field selection and book filtering.
- `catalog/service/source/quality` contains title, description, and genre rules.

Catalog image storage is defined by `catalog/image/store/ImageStore`. `integration/minio` provides its MinIO implementation.

ArchUnit checks these rules:

- Controllers, repositories, and entities use their named packages.
- Controllers do not access repositories.
- Repositories do not depend on services or controllers.
- Repository and entity access stays inside the owning feature.
- Catalog code does not depend on MinIO implementation types.
- Source model and port packages contain their assigned type kinds.

The API uses record DTOs. Integration response types stay inside their integration package.
