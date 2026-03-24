# Development Notes

This page is for contributors who want a fast, reliable local workflow.

## Local Setup

```bash
mvn -q -DskipTests compile
```

Useful commands during development:

```bash
mvn -q test
mvn -B verify
mvn -B -DskipTests checkstyle:check
mvn -B package
```

## Recommended Workflow

1. Create a branch for one focused change.
2. Implement the change with tests in the same pass.
3. Run local validation (`test` at minimum).
4. Update docs when behavior or operator workflow changes.
5. Open a PR with context, impact, and any migration notes.

## Engineering Guidelines

- Keep feature boundaries clean and avoid unnecessary coupling.
- Prefer typed configuration access over raw casts.
- Make external calls fail-safe and time-bounded.
- Clean up tasks/listeners/resources during disable and reload paths.
- Favor simple, explicit code over clever abstractions.

## Before You Open a PR

- Build succeeds locally.
- Relevant tests pass.
- New behavior is covered by tests.
- Operationally important failures are logged clearly.
