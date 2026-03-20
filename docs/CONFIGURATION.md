# Configuration Guide

## Main File

Primary configuration file:

- `config.yml`

Feature-local files may also be created under:

- `local/*.yml`

## Global Keys

Important global keys:

- `global.server_name`
- `global.dataprovider_token`

`dataprovider_token` can also come from environment variable:

- `PROXYFEATURES_DATAPROVIDER_TOKEN`

Token lookup precedence:

1. `PROXYFEATURES_DATAPROVIDER_TOKEN`
2. `global.dataprovider_token`

## Feature Keys

Each feature uses:

- `features.<FeatureName>.enabled`
- feature-specific keys under `features.<FeatureName>.*`

Example:

```yaml
features:
  Queue:
    enabled: true
    poll-interval-seconds: 2
```

## Localization

Localization files are under:

- `src/main/resources/lang/`

At runtime, localized outputs are resolved through the localization handler.

## Operational Advice

- Keep secrets in environment variables where possible.
- Do not commit production tokens to the repository.
- Validate config changes in staging before production rollout.
