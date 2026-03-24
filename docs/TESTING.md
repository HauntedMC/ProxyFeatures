# Testing and Quality

Testing in this project is designed to catch regressions early while keeping contributor workflow practical.

## Test Structure

Tests are organized under `tests/java` and generally mirror production package boundaries:

- API tests for public-facing contracts
- framework tests for core lifecycle/config/loading behavior
- feature tests for feature-specific logic and edge cases

## Local Commands

Run tests:

```bash
mvn -q test
```

Run the full quality gate (tests + coverage checks):

```bash
mvn -B verify
```

Run lint checks:

```bash
mvn -B -DskipTests checkstyle:check
```

## What to Test

When you change behavior, add or update tests close to that behavior:

- feature changes: validate feature logic and expected edge cases
- framework changes: validate lifecycle/config/loader contracts
- API changes: validate conversion, fallback, and error-handling behavior

Focus on user-visible behavior and regression-prone logic. Avoid writing tests that only duplicate framework boilerplate.

## Coverage Expectations

Coverage gates are enforced in CI for core areas and selected feature logic. The exact target set can evolve over time, so treat coverage as a quality signal, not a box-checking exercise.

If `mvn verify` fails on coverage:

1. confirm the new behavior has tests;
2. cover failure/edge paths, not only happy paths;
3. rerun locally before opening a PR.

## Coverage Reports

- IntelliJ: run tests with coverage and inspect the Coverage tool window.
- HTML report: `target/site/jacoco/index.html` after `mvn verify`.

## CI

CI validates tests, coverage, and linting on pull requests and main branch updates.
