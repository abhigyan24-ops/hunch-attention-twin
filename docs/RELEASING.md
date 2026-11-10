# Releasing Hunch

Instructions for creating official releases:

1. **Verify All Checks Pass:**
   ```bash
   node tools/rename-guard.mjs
   npm test
   npm run check:contracts
   ```
2. **Version Bump:**
   ```bash
   # Laptop gets full pre-release version:
   cd laptop && npm version 1.0.0-beta.1 --no-git-tag-version
   # VS Code extension gets base version:
   cd ../vscode-ext && npm version 1.0.0 --no-git-tag-version --allow-same-version
   ```
3. **Dry Run on CI:** Trigger `workflow_dispatch` on the release workflow branch.
4. **Tagging:** Create tag `v1.0.0-beta.1` and push to GitHub.
5. **Draft Review:** Verify generated binaries on clean virtual machines before publishing the draft GitHub release.
