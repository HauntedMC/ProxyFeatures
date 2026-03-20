# Tests Layout

This repository uses a top-level `tests/` source root to keep test code clearly separated from plugin runtime code.

Structure mirrors `src/main/java`:

- `tests/java/nl/hauntedmc/proxyfeatures/api`
- `tests/java/nl/hauntedmc/proxyfeatures/framework`
- `tests/java/nl/hauntedmc/proxyfeatures/features`

Framework and API tests are implemented with the same internal package structure as production code.

Coverage gates (JaCoCo) enforce 100% line coverage for both:

- `nl.hauntedmc.proxyfeatures.framework*`
- `nl.hauntedmc.proxyfeatures.api*`

## Coverage Visibility

- IntelliJ: run tests with coverage to get package/class percentages and project-tree coloring.
- CLI HTML report: `target/site/jacoco/index.html`.
