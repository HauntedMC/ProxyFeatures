# Development Notes

## Build and Test

```bash
mvn -q -DskipTests compile
mvn -q test
mvn -B package
```

## Local Workflow

1. Create a branch.
2. Implement focused changes.
3. Validate compile/tests.
4. Update docs/templates when behavior changes.
5. Open PR using the repository template.

## Coding Guidelines

- Prefer typed config API:
  - `get("key", Type.class, default)`
  - `getList(...)`
- Avoid broad unchecked casts from config values.
- Use timeout bounds on external calls.
- Keep async/scheduler lifecycles explicit and cleaned up on disable.
- Keep feature boundaries clean; avoid cross-feature coupling unless through API registry.

## Common Validation Checks

- Feature enable/disable/reload still works.
- No startup exceptions with default config.
- No blocked login/main paths from remote calls.
- No accidental token or credential logging.
