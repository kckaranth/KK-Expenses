# KK Expenses v0.2 — First source patch

This is an **incremental, build-unverified** patch for the already working KK Expenses source.

## Changes
- Defines a cohesive mint/teal Material 3 color scheme for the existing Compose UI.
- Preserves Android 12+ dynamic color preference.
- Does not change the database, package name, account logic, or transaction logic.

## Not yet delivered
- No new dashboard screen, free feature replacement, premium licensing removal, Google Drive backup, or 14 requested features have been implemented by this patch.
- The existing premium gating must not be treated as unlocked. Further source analysis and independently implemented replacements are required.

## Apply from Termux

1. Download this ZIP into your Downloads folder.
2. In Termux, run `cd ~/KK-Expenses`.
3. Unzip the archive into the repository root with `unzip -o ~/storage/downloads/KK_Expenses_v0_2_Theme_Patch.zip`.
4. Run `git diff --check` and `git diff -- myExpenses/src/main/java/org/totschnig/myexpenses/compose/Theme.kt`.
5. Commit and push: `git add myExpenses/src/main/java/org/totschnig/myexpenses/compose/Theme.kt && git commit -m "Add KK Expenses Material 3 palette" && git push origin main`.
6. Run your existing GitHub Actions APK workflow.

Note: Existing installations with Android 12+ dynamic color enabled may continue using system colors instead of the fallback palette.
