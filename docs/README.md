# Documentation Index

This directory contains operational and contributor documentation for ProxyFeatures.

## Guides

- [Architecture](ARCHITECTURE.md)
- [Configuration](CONFIGURATION.md)
- [Development](DEVELOPMENT.md)
- [Testing](TESTING.md)

## Release Workflow

Tagged releases are published by GitHub Actions using `.github/workflows/release-package.yml`.

To trigger a release:

1. Ensure `main` is green (tests + lint).
2. Create and push a version tag (`vX.Y.Z`).
3. Wait for the `Release Package` workflow to publish Maven package + GitHub release artifact.
