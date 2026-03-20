# Contributing to ProxyFeatures

Thanks for contributing.

## Prerequisites

- Java 21
- Maven 3.9+
- A running Velocity environment for manual testing
- Dependency plugins available (`dataregistry`, `dataprovider`)

## Development Setup

```bash
git clone <repo-url>
cd ProxyFeatures
mvn -q -DskipTests compile
```

## Branching and Commits

- Create a feature branch from `main`.
- Keep commits focused and reviewable.
- Use clear commit messages (`type: concise summary`).

Examples:

- `fix: prevent queue ticket leakage on failed connect`
- `docs: add security and contribution guides`

## Code Standards

- Prefer typed config accessors over raw casts.
- Avoid blocking calls on proxy-critical paths unless isolated and time-bounded.
- Fail safely on malformed external input.
- Add logs for operationally relevant failures.
- Keep changes minimal and local to the problem.

## Validation Before PR

Run at minimum:

```bash
mvn -q -DskipTests compile
mvn -q test
```

Also validate manually for changed feature paths.

## Pull Requests

- Fill out the PR template.
- Document config/schema changes.
- Include migration notes for breaking changes.
- Add or update docs when behavior changes.
- Keep issue/PR templates aligned when workflows or standards change.

## Security

Do not open public issues for vulnerabilities.
Use the process in [SECURITY.md](SECURITY.md).
