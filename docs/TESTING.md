# Testing and Quality

## Test Layout

Tests live in a dedicated top-level source root:

- `tests/java/nl/hauntedmc/proxyfeatures/api`
- `tests/java/nl/hauntedmc/proxyfeatures/framework`
- `tests/java/nl/hauntedmc/proxyfeatures/features`

Framework and API tests mirror the same internal package structure as production code.
Feature tests also mirror internal package structure for targeted logic classes (for example `features/antivpn/internal`, `features/queue/model`, `features/votifier/util`).

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
- selected feature logic classes across queue, AntiVPN, votifier/resourcepack, commandhider, clientinfo, friends, staffchat, connectioninfo, messager, and sanctions

This keeps core platform contracts and extension APIs fully regression-tested.

## Feature Test Strategy

Feature tests focus on **feature-specific logic and edge handling**, not repeated scaffolding that is already covered by framework tests.

Intentionally out of scope for feature package tests:

- repetitive feature boilerplate (`Feature` main classes, `meta` classes, framework wiring patterns)
- behavior that is already verified by framework contract tests

Feature logic currently gated at 100% line coverage:

- queue domain model/util: `ServerQueue`, `QueueEntry`, `EnqueueDecision`, `ServerStatus`, `PriorityResolver`
- AntiVPN internals: `IPCheckResult`, `MetricsCollector`, `CountryService`, `ProviderChain`, `IpWhitelist`, `PersistentIpCache`
- commandhider/clientinfo internals: `HiderHandler`, `ClientInfoConfig`
- friends/staffchat internals: `FriendsCache`, `ChatChannel`, `ChatChannelHandler`
- connectioninfo/messager/sanctions entities: `SessionHandler`, `PlayerMessageSettingsEntity`, `SanctionEntity`
- votifier/resourcepack internals/entities/utilities: `VotifierConfig`, `PlayerVoteStatsEntity`, `PlayerVoteMonthlyEntity`, `PlayerVoteMonthlyKey`, `VotifierRolloverStateEntity`, `IpAccessList`, `RSAUtil`, `ResourceUtils`
- feature runtime logic: `AntiVPNService`, `VanishRegistry`, `ServiceLookup`, `FeatureFactory`, `ServerLinksHandler`
- feature event-bus handlers: `commandrelay.internal.EventBusHandler`, `staffchat.internal.messaging.EventBusHandler`, `vanish.internal.messaging.EventBusHandler`, `votifier.messaging.EventBusHandler`

The feature suite targets regression-sensitive paths such as:

- queue ordering/requeue/grace semantics (including defensive stale-state handling)
- AntiVPN provider aggregation, whitelist CIDR parsing/matching, cache persistence/inflight dedupe/failure paths
- command hiding normalization/deduplication snapshot behavior
- profile-based clientinfo config overrides and fallback behavior
- friends caching and invalidation semantics
- staffchat channel prefix normalization, channel membership, and permission-gated broadcast delivery
- session/message/sanction entity state transitions and invariants
- votifier config clamps/sanitization + vote stats/monthly entity invariants + IP ACL parsing and RSA handling
- resourcepack utility conversions
- AntiVPN policy decisions and cache/provider interaction paths
- cross-proxy event-bus subscription/disable/error handling for command relay, staffchat, vanish, and votifier
- vanish online-registry correctness and serverlinks payload validity

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
