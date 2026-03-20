# Testing and Quality

## Test Layout

Tests live in a dedicated top-level source root:

- `tests/java/nl/hauntedmc/proxyfeatures/api`
- `tests/java/nl/hauntedmc/proxyfeatures/framework`
- `tests/java/nl/hauntedmc/proxyfeatures/features`

Framework tests mirror the same internal package structure as production code.

## Local Commands

Run only tests:

```bash
mvn -q test
```

Run tests + coverage gate (includes JaCoCo check):

```bash
mvn -B verify
```

Run linting (Checkstyle):

```bash
mvn -B -DskipTests checkstyle:check
```

## Coverage Policy

JaCoCo enforces **100% line coverage** for:

- `nl.hauntedmc.proxyfeatures.framework.*`
- `nl.hauntedmc.proxyfeatures.framework.*.*`

This keeps framework behavior fully regression-tested while API/feature coverage expands incrementally.

## CI Workflows

- `CI Tests and Coverage`: runs `mvn verify`
- `CI Lint`: runs `mvn -DskipTests checkstyle:check`

Both workflows trigger on:

- Pull requests (`opened`, `synchronize`, `reopened`)
- Pushes to `main`
