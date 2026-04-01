# ProxyFeatures

[![CI Lint](https://github.com/HauntedMC/ProxyFeatures/actions/workflows/ci-lint.yml/badge.svg?branch=main)](https://github.com/HauntedMC/ProxyFeatures/actions/workflows/ci-lint.yml)
[![CI Tests and Coverage](https://github.com/HauntedMC/ProxyFeatures/actions/workflows/ci-tests-and-coverage.yml/badge.svg?branch=main)](https://github.com/HauntedMC/ProxyFeatures/actions/workflows/ci-tests-and-coverage.yml)
[![Latest Release](https://img.shields.io/github/v/release/HauntedMC/ProxyFeatures?sort=semver)](https://github.com/HauntedMC/ProxyFeatures/releases/latest)
[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/HauntedMC/ProxyFeatures)](LICENSE)

One modular feature framework for your entire Velocity network.

## Quick Start

1. Place `ProxyFeatures.jar` in your Velocity `plugins/` directory.
2. Install dependencies: `dataregistry` and `dataprovider`.
3. Start the proxy once to generate defaults.
4. Enable the features you want in `config.yml`.
5. Restart and go live.

## Requirements

- Java 21
- Velocity 3.5.0
- [Recommended] `DataRegistry` plugin
- [Recommended] `DataProvider` plugin

## Build From Source

Add GitHub Packages credentials for Maven server id `github` in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_TOKEN</password>
    </server>
  </servers>
</settings>
```

Use a token with `read:packages` (and `repo` if the package source repositories are private), then run:

```bash
mvn -B package
```

Output jar: `target/ProxyFeatures.jar`

## Learn More

- [Configuration Guide](docs/CONFIGURATION.md)
- [Documentation Index](docs/README.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development Notes](docs/DEVELOPMENT.md)
- [Testing and Quality](docs/TESTING.md)
- [Contributing](CONTRIBUTING.md)

## Community

- [Support](SUPPORT.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
