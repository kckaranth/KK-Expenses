package org.totschnig.myexpenses.fragment.preferences

import android.content.Intent
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
}
