# Releasing Dhyan

Use `npm version <version> --no-git-tag-version` in laptop and vscode-ext. Commit the manifests and lockfiles together.

Push the reviewed CI branch and dispatch release.yml on that branch. Record the run SHA and inspect its logs and artifacts. Manual runs build artifacts and skip release creation. Stop after five failed dry runs and report the failures.

Android builds without signing secrets produce a debug-signed APK for testing. Each ephemeral runner may use a different debug key. Use a stable release keystore for updatable distribution.

After a green run and owner approval, merge the branch and create a new version tag once. v1.0.0 already exists locally, so resolve its remote status with the owner before choosing a tag. Tag runs create draft releases. Install and test the artifacts on clean target machines before publishing the draft manually.
