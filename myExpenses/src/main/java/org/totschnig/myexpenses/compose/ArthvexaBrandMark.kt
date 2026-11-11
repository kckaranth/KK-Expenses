package org.totschnig.myexpenses.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.R

/** Shared brand mark for the A2 UI. Uses existing day/night Arthvexa artwork. */
@Composable
fun ArthvexaBrandMark(showName: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.arthvexa_header_symbol),
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        if (showName) {
            Text("Arthvexa", style = MaterialTheme.typography.titleLarge)
        }
    }
}
