# KK Expenses v0.3 — first functional dashboard patch

This patch adds a **Home** navigation item to the existing Jetpack Compose main screen on single-pane devices. It displays live account balances using the application's existing `FullAccount` state and currency formatter, and provides buttons to navigate to existing Accounts and Transactions screens.

It does **not** change the SQLite schema, licensing checks, backup behavior, or premium feature availability. It is not the complete v0.3 release. No compilation has been performed in this environment; validate via GitHub Actions and report the build log if it fails.

## Apply on Android using Termux

From `~/KK-Expenses`, extract the patch ZIP with `unzip -o PATH_TO_ZIP`, then run:

```sh
git diff --check
git add myExpenses/src/main/java/org/totschnig/myexpenses/compose/main/MainScreen.kt KK_V0_3_README.md
git commit -m "Add KK Expenses Compose home dashboard"
git push origin main
```

Run your existing GitHub Actions workflow `Build KK Expenses APK` and test the Home tab. Do not uninstall the existing app or clear its data. Back up any important financial data before installing test builds.

## Follow-up

Add reliable transaction summary and charts after verifying repository data contracts. Commercial UI and premium dependencies require separate, license-compliant work.
