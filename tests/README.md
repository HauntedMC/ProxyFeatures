# Tests Layout

This repository uses a top-level `tests/` source root to keep test code clearly separated from plugin runtime code.

Structure mirrors `src/main/java`:

- `tests/java/nl/hauntedmc/proxyfeatures/api`
- `tests/java/nl/hauntedmc/proxyfeatures/framework`
- `tests/java/nl/hauntedmc/proxyfeatures/features`

Framework and API tests are implemented with the same internal package structure as production code.
Feature tests also mirror the same internal package structure for logic-heavy internals/utilities.

Coverage gates (JaCoCo) enforce 100% line coverage for both:

- `nl.hauntedmc.proxyfeatures.framework*`
- `nl.hauntedmc.proxyfeatures.api*`

Feature logic is also coverage-gated at 100% line coverage for selected classes:

- queue model/util (`ServerQueue`, `QueueEntry`, `EnqueueDecision`, `ServerStatus`, `PriorityResolver`)
- AntiVPN internals (`IPCheckResult`, `MetricsCollector`, `CountryService`, `ProviderChain`, `IpWhitelist`, `PersistentIpCache`)
- votifier/resourcepack utilities (`IpAccessList`, `RSAUtil`, `ResourceUtils`)

Feature tests intentionally skip repetitive framework-covered boilerplate such as feature main/meta wrappers and focus on feature-specific behavior that should detect breakage.

## Coverage Visibility

- IntelliJ: run tests with coverage to get package/class percentages and project-tree coloring.
- CLI HTML report: `target/site/jacoco/index.html`.
