# KK Expenses — transaction-level dashboard patch

Adds a read-only Compose monthly activity card below the existing account overview:

- Per-account current-calendar-month income and expenses using integer database minor units.
- Up to four spending categories per account as proportional bars.
- Five most recent expense/income transactions with account and date.
- Refresh on transaction/category provider change notifications.
- No schema migration, no signing changes, no changes to financial records.

## Accounting rules

Queries the existing `TransactionProvider.TRANSACTIONS_URI`, excludes transfer peers, voided and archived records, and excludes split parent records (category ID 0) while including their constituent split entries. Uses the device's local time zone for month boundaries and preserves account currency separation. It does not add opening balances to income.

## Important limitations

- This is a source patch, not a verified APK. Run GitHub Actions and inspect compilation logs before installing.
- Month summaries and category breakdown are newly added; verify splits, refunds, transfer pairs, archived entries and different currencies against the upstream Transactions screen.
- The category label query is best-effort. When labels cannot be resolved, an Uncategorized label appears.
- The transient red transaction-row highlight, old premium purchase prompts, and old transaction editor visuals are NOT fixed in this patch. Those require separate source changes and regression testing. Do not remove licensing checks blindly.
- The repository snapshot here was reconstructed from the source archive and the previously supplied dashboard patches. If your GitHub MainScreen.kt has diverged, inspect the diff before applying.

## Install from Termux

```sh
cd ~/KK-Expenses
git pull --ff-only origin main
unzip -o ~/storage/downloads/KK_Expenses_Monthly_Analytics_Patch.zip
git diff --check
git diff -- myExpenses/src/main/java/org/totschnig/myexpenses/compose/main/MainScreen.kt
git add myExpenses/src/main/java/org/totschnig/myexpenses/compose/main/MainScreen.kt myExpenses/src/main/java/org/totschnig/myexpenses/compose/main/KKMonthlyActivity.kt KK_MONTHLY_DASHBOARD_README.md
git commit -m "Add monthly activity and recent transactions to home"
git push origin main
```

Use the existing signed GitHub Actions workflow. Do not uninstall your current app. If Gradle fails, share the first Kotlin compilation error.
