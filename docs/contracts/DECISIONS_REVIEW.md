# Contract review decisions

The wire schema and crypto vectors are unchanged by this review. These are executable mock fixes for already-specified behavior, not new production policy.

- Command handler runs only after durable identity reservation. A crash after reservation has an unknown outcome, so duplicates return duplicate/outcome_unknown and are never executed twice. There is no promise that a command was applied solely because it was reserved. Rejected/expired/unauthorized commands never call the handler. Production8L/8P1 must expose uncertainty appropriately.
- The command ledger's configurable default4096records and relay join-IP bucket cap4096 are bounded test-harness implementation limits, not wire constants or production sizing. Production limits need the owning stage's documented sizing decision. The24hour command identity window and10rendezvousjoins/minute/IP are existing contract requirements.
- Event-ledger recovery rejects malformed/contradictory cursor or identity records instead of guessing a cursor. No recovery path persists rejected payloads. Production migration and transactional recovery remain its owners' work.
- Human SAS confirmation, real TLS, Android/Kotlin vectors, live sync and device compatibility remain unverified. Mock pairing auto-confirm is only a test harness. This review changes no phone code and starts no Gradle build.
