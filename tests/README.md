# Tests Overview

Test code lives under the top-level `tests/` directory and is split by area:

- `tests/java/.../api`: API contract and utility behavior
- `tests/java/.../framework`: shared framework/lifecycle behavior
- `tests/java/.../features`: feature logic and edge handling

The goal is simple: catch regressions where they matter most while keeping tests maintainable.

## Running Tests

```bash
mvn -q test
```

Full gate (tests + coverage checks):

```bash
mvn -B verify
```

## Coverage

Coverage checks are enforced in CI for core areas and selected feature logic.
Exact class-level targets may evolve, so check CI output when coverage fails.

Coverage report path after `mvn verify`:

- `target/site/jacoco/index.html`
