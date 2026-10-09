# Contributing

Nothing reaches `main` unless every quality gate is green. The gates run in three places, with the
same scripts, so a failure is always reproducible on your machine.

| Where | How it is enforced | Can it be bypassed? |
| --- | --- | --- |
| Your machine | `.githooks/pre-push` runs `scripts/preflight.sh` | Yes (`--no-verify`) |
| GitHub Actions | `.github/workflows/ci.yml` on every push and pull request | No |
| `main` branch | Branch protection requires a pull request and the **CI passed** check | No |

## One-time setup

```sh
git config core.hooksPath .githooks            # enable the pre-push hook
brew install actionlint shellcheck gitleaks    # local copies of the CI linters
```

`main` is protected on GitHub: changes arrive through a pull request, the **CI passed** check must be
green, history stays linear, force-pushes are blocked and `v*` tags are immutable.

## The gates

`scripts/preflight.sh` runs them all; pass names to run a subset (`scripts/preflight.sh docs gradle`).

1. **secrets** – no keystores or credential files tracked; gitleaks over the full history.
2. **workflows** – actionlint on workflows, shellcheck on every script and hook.
3. **docs** – html-validate on `docs/`, internal links and anchors resolve.
4. **gradle** – `./gradlew qualityCheck`:
   - Spotless (google-java-format AOSP, ktlint, whitespace)
   - javac `-Xlint:all -Werror` plus **Error Prone** (every warning is an error)
   - Checkstyle, PMD, SpotBugs (max effort, lowest threshold)
   - Android Lint with warnings as errors, including test sources
   - unit tests (Robolectric, MockWebServer) with a JaCoCo gate: 80 % lines, 75 % branches
   - debug and release (R8) builds

CI adds CodeQL (`security-extended`), Gradle wrapper validation, dependency review on pull
requests, and an informational emulator run of the instrumented tests.

Fix formatting automatically with `./gradlew spotlessApply`.

## Tests that need a device

PDF rendering needs the real Android graphics stack, so it is covered by an instrumented test:

```sh
./gradlew connectedDebugAndroidTest
```

## Releasing

Merge to `main`, then push a semantic-version tag:

```sh
git tag v1.2.3 && git push origin v1.2.3
```

`.github/workflows/release.yml` re-runs the whole gate, builds the signed release APK, verifies the
signature and version, attests build provenance and publishes the APK, its SHA-256 and the signing
certificate fingerprint as a GitHub release. Tags such as `v1.2.3-beta.1` become pre-releases. Add
the signing secrets listed at the top of that workflow first.
