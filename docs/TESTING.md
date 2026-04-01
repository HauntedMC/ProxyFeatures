# Testing and Quality

Testing in this project is designed to catch regressions early while keeping contributor workflow practical.

## Test Structure

Tests are organized under `src/test/java` and generally mirror production package boundaries:

- API tests for public-facing contracts
- framework tests for core lifecycle/config/loading behavior
- feature tests for feature-specific logic and edge cases

Shared test resources live under `src/test/resources`.

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

Generate a local coverage report:

```bash
mvn -q test jacoco:report
```

## What to Test

When you change behavior, add or update tests close to that behavior:

- feature changes: validate feature logic and expected edge cases
- framework changes: validate lifecycle/config/loader contracts
- API changes: validate conversion, fallback, and error-handling behavior

Focus on user-visible behavior and regression-prone logic. Avoid writing tests that only duplicate framework boilerplate.

## Test Quality Bar

Use these rules when adding or reviewing tests:

- prefer behavior assertions over "does not throw" assertions;
- avoid getter/setter/record-equality-only tests unless they protect non-trivial invariants;
- avoid static mocking when normal dependency injection or instance mocks are possible;
- assert observable outcomes (state, return values, emitted messages, interactions) for both happy and failure paths.

## Coverage Expectations

Coverage gates are enforced in CI for core areas and selected feature logic. The exact target set can evolve over time, so treat coverage as a quality signal, not a box-checking exercise.

If `mvn verify` fails on coverage:

1. confirm the new behavior has tests;
2. cover failure/edge paths, not only happy paths;
3. rerun locally before opening a PR.

## Coverage Reports

- IntelliJ: run tests with coverage and inspect the Coverage tool window.
- HTML report: `target/site/jacoco/index.html` after `mvn verify`.
- CSV summary: `target/site/jacoco/jacoco.csv` after `mvn -q test jacoco:report`.

## Full Feature Scan Workflow

Use this when you want a full feature/class/method scan, not just per-change verification:

1. Run `mvn -q test jacoco:report`.
2. Review `target/site/jacoco/index.html` and sort by missed lines/branches.
3. Use `target/site/jacoco/jacoco.csv` to quickly spot high-risk classes with high `LINE_MISSED` and `BRANCH_MISSED`.
4. Drill into those classes in the HTML report and add tests for behavior-heavy methods first (commands, services, listeners).

Prioritize methods with both high `line_missed` and high `branch_total`; these are typically the highest regression-risk paths.

## CI

CI validates tests, coverage, and linting on pull requests and main branch updates.
