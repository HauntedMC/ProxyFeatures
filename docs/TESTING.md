# Testing and Quality

## Test Layout

Tests live in a dedicated top-level source root:

- `tests/java/nl/hauntedmc/proxyfeatures/api`
- `tests/java/nl/hauntedmc/proxyfeatures/framework`
- `tests/java/nl/hauntedmc/proxyfeatures/features`

Framework and API tests mirror the same internal package structure as production code.

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

- `nl.hauntedmc.proxyfeatures.framework*`
- `nl.hauntedmc.proxyfeatures.api*`

This keeps core platform contracts and extension APIs fully regression-tested.

## Framework Breakage Detection

The framework suite is designed to fail on meaningful behavior regressions, not only on execution-path changes:

- `framework/command`: permission gates, command routing, localization key mapping, placeholder wiring, list/status rendering.
- `framework/config`: default injection, schema drift reconciliation, unknown key pruning, global config accessors.
- `framework/loader`: discovery/init ordering, dependency diagnostics, enable/disable/reload result contracts, rollback behavior.
- `framework/loader/dependency`: recursive dependency checks, plugin dependency checks, dependent-feature resolution safety.
- `framework/lifecycle`: command/listener/task/data/cache manager registration and cleanup behavior.
- `framework/localization`: language fallback rules, default-message registration semantics, placeholder rendering.
- `framework/log`: logger prefix/format methods and overload paths.

## API Breakage Detection

The API suite explicitly targets contract-level regressions and edge handling:

- `api/io/config`: typed conversion behavior, default fallbacks, scoped view pathing, batch/list mutation safety, YAML error-handling branches.
- `api/io/cache`: JSON/SQLite cache persistence, expiration cleanup behavior, filesystem and JDBC failure wrapping.
- `api/util/text/format`: MiniMessage/legacy conversion edge cases, sanitizer/autolinker behavior, serializer format options.
- `api/util/http`: URI scheme guards, response-size protection, non-2xx handling, webhook safety behavior.
- `api/util/text/placeholder` and `api/util/text/format/inspect`: placeholder resolution precedence and formatting detection correctness.

## Viewing Coverage In IDE

For IntelliJ IDEA:

1. Run tests with coverage (`Run 'All Tests' with Coverage`).
2. Open the `Coverage` tool window to see package/class percentages.
3. In the Project view, enable coverage coloring to see file-level status directly in the explorer.
4. Open a class to view covered/missed lines in the editor gutter.

CLI-generated report is also available at:

- `target/site/jacoco/index.html`

## CI Workflows

- `CI Tests and Coverage`: runs `mvn verify`
- `CI Lint`: runs `mvn -DskipTests checkstyle:check`

Both workflows trigger on:

- Pull requests (`opened`, `synchronize`, `reopened`)
- Pushes to `main`
