# ProxyFeatures

One modular plugin for your entire Velocity network.

`ProxyFeatures` helps you run a smoother, safer, and more engaging proxy without stitching together a dozen separate plugins. Enable only the modules you need and scale up as your network grows.

## Why Server Owners Choose ProxyFeatures

- **Do more with fewer plugins:** replace plugin sprawl with one consistent system.
- **Improve player experience:** cleaner onboarding, better communication, and stronger social tools.
- **Protect your network:** built-in security and moderation features for day-to-day operations.
- **Stay flexible:** feature-by-feature toggles let you roll out changes safely.

## Major Benefits

### Better Retention and Player Experience

- Queue management, MOTD, announcer, broadcast, player list, and proxy info tools.
- Hub and slash-server flows to make navigation easy.
- Resource pack and UX-focused features to improve first impressions.

### Stronger Safety and Moderation

- AntiVPN controls and sanctions tooling.
- Command logging and moderation-focused visibility.
- Vanish and staff-oriented operational features.

### Better Staff and Community Operations

- Staff chat and command relay for cross-network coordination.
- Messaging, friends, player info, and language tooling for community support.
- Votifier and engagement-focused modules for network activity.

## Quick Start

1. Place `ProxyFeatures.jar` in your Velocity `plugins/` directory.
2. Install dependencies: `dataregistry` and `dataprovider`.
3. Start the proxy once to generate defaults.
4. Enable the features you want in `config.yml`.
5. Restart and go live.

## Requirements

- Java 21
- Velocity 3.x
- HauntedMC `dataregistry` plugin
- HauntedMC `dataprovider` plugin

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
