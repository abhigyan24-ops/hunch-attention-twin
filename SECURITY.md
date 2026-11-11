# Security Policy

## Supported Versions
Hunch is in **public beta**. Only the latest beta release receives security fixes.

| Version | Supported |
|---|---|
| Latest `1.0.0-beta.x` | Yes |
| Older betas | No |

## Reporting a Vulnerability
**Please do not open a public issue for security problems.**

Use GitHub's **Report a vulnerability** button on the repository's *Security* tab, or email **hunch_support@googlegroups.com**.

Please include:
- A clear description of what you found
- Exact steps to reproduce the issue
- Affected component(s) and version
- Potential impact and threat model

*Never include real personal data, private keys, authentication tokens, or live credentials in any report.*

## What to Expect
- Acknowledgement within **3 business days**
- Initial severity and impact assessment within **10 business days**
- A fix or mitigation plan for confirmed issues, typically within **90 days** (faster for high/critical severity)
- Credit in release advisories upon request

## Scope
**In scope:**
- The pairing and sync protocol (`contracts/sync/`), handshake, and cryptographic integrity
- Local IPC, HTTP endpoint authentication, and local credential storage
- User API key handling and safe storage
- The CI/CD release pipeline, integrity checking, and auto-update verification
- Any vulnerability that could lead to unauthorized local or network data egress

**Out of scope:**
- Physical attacks requiring access to an unlocked host or device
- Social engineering attacks
- Denial-of-service attacks against `localhost`
- Vulnerabilities solely in upstream third-party dependencies not packaged or bundled with Hunch

## Honest Security Posture
The custom synchronization protocol and cryptographic implementation have **not yet received an independent third-party audit**. Treat all pre-releases as beta software.

## Safe Harbor
We will not initiate legal action against researchers who make a good-faith effort to adhere to this policy, avoid data destruction or privacy invasion, and provide reasonable disclosure lead time.
