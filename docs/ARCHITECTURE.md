# Architecture Overview

ProxyFeatures is built as a modular system for Velocity. Instead of one large block of logic, functionality is split into independent feature modules that can be turned on or off through configuration.

## Design Goals

- Keep features isolated so one module can be changed without destabilizing others.
- Centralize common lifecycle concerns such as command/listener/task registration.
- Let operators adopt features gradually, not all at once.

## Runtime Model

At startup, the plugin loads shared configuration, discovers available features, validates dependencies, and starts only the features that are enabled.

During runtime, each feature owns its own behavior while using shared framework services for common tasks (config access, lifecycle management, logging, and integration points).

On reload/shutdown, features are asked to clean up resources so stale listeners, tasks, and cached state do not leak into the next run.

## Configuration and Data

- `config.yml` is the primary control surface.
- Feature-specific settings live under each feature section.
- Some features may use additional local files for structured data.
- Data and cross-server communication are handled through project dependencies where needed.

## Why This Matters

For operators, this architecture means safer rollout and easier troubleshooting.

For contributors, it means clearer boundaries: implement behavior inside a feature, keep shared behavior in the framework, and avoid tight coupling between unrelated modules.
