# Hunch Brand Kit

Source of truth for product naming across Android, Workstation, Extensions, and documentation. Governed by `docs/prompts/HUNCH_RENAME_PACK.md`.

## 1. Brand Identity Table

| Item | Value | Status / Rule |
|---|---|---|
| Product name | **Hunch** (capital H, never HUNCH) | Approved source of truth |
| Descriptor (always next to the name on first run, About, README, slides) | **your attention digital twin** | Required on first run, About, README, slides |
| Tagline | **Knows what that notification will cost you.** | Approved source of truth |
| Disclaimer (README, About screens) | **Unrelated to other products named Hunch.** A working name for a prototype. | Required on README and About screens |
| Mascot | **Pip** | Unchanged |
| Domain | `usehunch.dev` | `[UNVERIFIED / PENDING OWNER REGISTRATION]` — Do **NOT** hard-code any URL until the owner confirms registration |
| Repository | `hunch-attention-twin` | `[PENDING OWNER GITHUB RENAME]` |
| Social and store handle | `@gethunch` | `[UNVERIFIED / PENDING OWNER CHECK PER PLATFORM]` |
| Short store line | "Predicts what a notification will cost your focus." | Approved source of truth |
| Codename | `dhyan` | Kept inside internal package names, protected identifiers, storage names, and protocol constants for compatibility |

## 2. README Opening Block

`README.md` (and root project documentation) must start with this exact five-line block:

```markdown
# Hunch: your attention digital twin

> Knows what that notification will cost you.

Unrelated to other products named Hunch. Hunch is a working name for a prototype.

Codename note: internal package names, protocol constants and storage names still say `dhyan` so existing data and pairings keep working.
```

Credits remain unchanged: the ActivityWatch credit line and asset credits must not be altered or removed.

## 3. Owner Verification Checklist (Pending Owner Action)

Record verified values here after completing the manual owner checks (`docs/prompts/HUNCH_RENAME_PACK.md` Section 3):

- [ ] **Domain (`usehunch.dev`)**: Check via `nslookup usehunch.dev`, `lookup.icann.org`, and registrar availability search before purchase. Status: `UNVERIFIED`.
- [ ] **GitHub repo (`hunch-attention-twin`)**: Rename in GitHub repository settings and update worktree remotes via `git remote set-url origin <new url>`. Status: `PENDING`.
- [ ] **Handles (`@gethunch`)**: Check availability on GitHub, X, Instagram, LinkedIn, and store developer accounts. Status: `UNVERIFIED`.
- [ ] **Trademark**: Before any public launch, search IP India (classes 9, 35, 42) and WIPO. Status: `NOT DONE / NOT REQUIRED FOR DEMO`.
