package org.totschnig.myexpenses.fragment.preferences

import android.content.Intent
import android.net.Uri
import android.content.ActivityNotFoundException
import androidx.annotation.Keep
import androidx.preference.Preference
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.preference.PrefKey


@Keep
class PreferencesFeedbackFragment : BasePreferenceFragment() {
    override val preferencesResId = R.xml.preferences_feedback


    override fun onPreferenceTreeClick(
        preference: Preference
    ): Boolean = when {
        super.onPreferenceTreeClick(preference) -> true

        preference.key == "arthvexa_report_problem" -> {
            openArthvexaIssue(
                "Bug report: "
            )
            true
        }

        preference.key == "arthvexa_request_feature" -> {
            openArthvexaIssue(
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
