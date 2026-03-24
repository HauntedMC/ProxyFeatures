# Configuration Guide

This guide focuses on practical setup and safe operations. Keep changes small, test often, and roll out in steps.

## Configuration Layout

- Main settings: `config.yml`
- Optional feature-local files: `local/*.yml`
- Language overrides: `lang/*.yml` in the runtime plugin directory

Most features follow the same pattern:

- `enabled` toggle
- feature-specific settings under that feature section

## Recommended Workflow

1. Start with only the features you actively need.
2. Enable one feature (or one group) at a time.
3. Restart or reload based on your normal operations process.
4. Confirm behavior in logs and in-game.
5. Move to the next feature only after validation.

This keeps incidents small and rollback simple.

## Environment-Specific Values

Treat production endpoints, tokens, and credentials as environment-specific values:

- keep secrets out of committed files;
- prefer environment variables or your secret-management workflow;
- document expected variables for your team.

## Localization

You can override messages without copying every key.

Use partial language files with only the entries you want to customize. Missing entries automatically fall back to the default message set.

## Troubleshooting Tips

- If a feature is not active, verify it is enabled and dependencies are available.
- If a setting seems ignored, check key names/indentation first.
- Apply one change at a time when debugging configuration issues.
