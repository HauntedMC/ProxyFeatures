# Architecture Overview

## Core Pattern

`ProxyFeatures` is a modular plugin system built around feature classes.
Each feature extends a shared base and is discovered/loaded dynamically.

Main modules:

- `ProxyFeatures`: plugin bootstrap and lifecycle integration
- `framework.loader`: feature discovery, dependency checks, enable/disable/reload
- `framework.lifecycle`: feature-scoped managers (tasks, listeners, commands, data, cache)
- `framework.config` + `api.io.config`: typed config access and persistence
- `features.*`: independent feature implementations

## Feature Lifecycle

1. Feature classes are discovered via classpath scanning.
2. Default config/messages are injected.
3. Dependency checks are evaluated.
4. Enabled features are initialized.
5. On shutdown/reload, cleanup is called and managed resources are released.

## Config Model

- Primary shared configuration: `config.yml`
- Global settings in `global.*`
- Feature settings in `features.<FeatureName>.*`
- Local feature files can be created under `local/`

## Data and Messaging

- DataProvider is used for DB and messaging integration.
- ORM contexts are created per feature when needed.
- Redis messaging is used in selected features (e.g., votifier, relay, vanish).

## Safety Expectations

- Avoid unbounded thread creation.
- Use typed config reads with safe defaults.
- Bound external IO and parse untrusted input defensively.
