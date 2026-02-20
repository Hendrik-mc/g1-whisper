# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Email: security@example.com (replace with your address before publishing)

We will acknowledge within 48 hours and aim to release a patch within 14 days of a confirmed issue.

## Key Security Properties

- The app never stores your OpenAI API key on disk in plain text; it is read from `local.properties` at build time and embedded as a BuildConfig field
- No keystore file is ever committed to the repository
- All BLE communication is local-only (no relay server)
- Microphone access requires explicit user consent and defaults to off
- No analytics or crash reporting SDKs are included
