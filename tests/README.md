# Tests Layout

This repository uses a top-level `tests/` source root to keep test code clearly separated from plugin runtime code.

Structure mirrors `src/main/java`:

- `tests/java/nl/hauntedmc/proxyfeatures/api`
- `tests/java/nl/hauntedmc/proxyfeatures/framework`
- `tests/java/nl/hauntedmc/proxyfeatures/features`

Framework tests are implemented first and organized by the same internal package structure as production code.
