# ProxyFeatures

`ProxyFeatures` is a modular Velocity proxy plugin for HauntedMC.
It provides a large set of optional features (queueing, sanctions, votifier, AntiVPN, messaging, friends, localization, and more) that are loaded dynamically from configuration.

## Requirements

- Java 21
- Velocity 3.x (current project API target: `3.5.0-SNAPSHOT`)
- HauntedMC `dataregistry` plugin
- HauntedMC `dataprovider` plugin

## Build

```bash
mvn -q -DskipTests compile
mvn -q test
mvn -B package
```

Build output:

- `target/ProxyFeatures.jar`

## Install

1. Build (or download) the plugin jar.
2. Place it in your Velocity `plugins/` directory.
3. Ensure dependency plugins (`dataregistry`, `dataprovider`) are installed.
4. Start the proxy once to generate configuration.
5. Configure `config.yml` and enable the features you need.
6. Restart the proxy.

## Configuration Model

- Global settings are in `global.*` (inside `config.yml`).
- Each feature has its own section in `features.<FeatureName>.*`.
- Feature defaults are injected automatically on startup.
- Features can be enabled/disabled individually by config.
- Some features also use dedicated local files under `local/*.yml`.

DataProvider token resolution order:

1. `PROXYFEATURES_DATAPROVIDER_TOKEN` environment variable
2. `global.dataprovider_token` in `config.yml`

## Development

See:

- [Contributing Guide](CONTRIBUTING.md)
- [Documentation Index](docs/README.md)
- [Development Notes](docs/DEVELOPMENT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Configuration](docs/CONFIGURATION.md)

## Community and Governance

- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security Policy](SECURITY.md)
- [Support](SUPPORT.md)

## Repository Structure

- `src/main/java`: plugin source code
- `src/main/resources`: config and localization defaults
- `.github/`: CI workflow and community templates
- `docs/`: operational and contributor documentation
