package org.totschnig.myexpenses.fragment.preferences

import android.content.Intent
import android.net.Uri
import android.content.ActivityNotFoundException
import androidx.annotation.Keep
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.preference.PrefKey


@Keep
class PreferencesFeedbackFragment : BasePreferenceFragment() {
    override val preferencesResId = R.xml.preferences_feedback


    override fun onPreferenceTreeClick(
        preference: Preference
    ): Boolean = when {
        super.onPreferenceTreeClick(preference) -> true

        preference.key == "arthvexa_help_faq" -> {
            showArthvexaFaq()
            true
        }

        preference.key == "arthvexa_report_problem" -> {
            confirmArthvexaIssue(
                "Bug report: "
            )
            true
        }

        preference.key == "arthvexa_request_feature" -> {
            confirmArthvexaIssue(
                "Feature request: "
            )
            true
        }

        matches(preference, PrefKey.TELL_A_FRIEND) -> {
            val shareText =
                "Arthvexa - Your Finances. In Focus."

            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        putExtra(
                            Intent.EXTRA_TEXT,
                            shareText
                        )
                        type = "text/plain"
                    },
                    getString(R.string.menu_share)
                )
            )
            true
        }

        else -> false
    }

    private fun showArthvexaFaq() {
        val titles = arrayOf(
            "Accounts",
            "Transactions",
            "Transfers",
            "Monthly activity",
            "Budgets",
            "Backup and restore"
        )

        val answers = arrayOf(
            "Open Accounts to manage your accounts. " +
                "Each account maintains its own balance and currency.",

            "Open Transactions to record income and expenses. " +
                "Select a transaction to review or edit its details.",

            "Use Transfer to move money between accounts. " +
                "Transfers are not treated as ordinary income or expenses.",

            "Expand Monthly activity on the Home screen " +
                "to view account trends and monthly comparisons.",

            "Use Budgets to plan spending and review " +
                "progress against your configured budgets.",

            "Open Settings and locate Backup and restore. " +
                "Create a backup before changing devices."
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Arthvexa Help & FAQs")
            .setItems(titles) { _, index ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(titles[index])
                    .setMessage(answers[index])
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Topics") { _, _ ->
                        showArthvexaFaq()
                    }
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmArthvexaIssue(title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Before continuing")
            .setMessage(
                "GitHub issues are public. Do not include " +
                    "account numbers, balances, transaction " +
                    "details, passwords, or other personal " +
                    "financial information.\n\n" +
                    "Arthvexa will not automatically attach " +
                    "your financial data."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue to GitHub") { _, _ ->
                openArthvexaIssue(title)
            }
            .show()
    }

    private fun openArthvexaIssue(title: String) {
        val url = Uri.parse(
            "https://github.com/kckaranth/KK-Expenses/issues/new"
        ).buildUpon()
            .appendQueryParameter("title", title)
            .build()

        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, url)
            )
        } catch (e: ActivityNotFoundException) {
            android.widget.Toast.makeText(
                requireContext(),
                "No browser available to open GitHub",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
