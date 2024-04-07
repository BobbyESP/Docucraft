package com.bobbyesp.docucraft.presentation.pages

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bobbyesp.docucraft.presentation.theme.DocucraftTheme

@Composable
fun HomePage() {

}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HomePagePreview() {
    DocucraftTheme {
        HomePage()
    }
}