package com.prism.ot.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.prism.ot.ui.theme.PrismColors

/**
 * PRISM top app bar.
 *
 * App bars are passed to `Scaffold`, which has specific parameters to
 * receive them. The bar is transparent so the camera preview on
 * the scanning screens extends underneath it, and it handles its own
 * status-bar inset via [TopAppBarDefaults.topAppBarColors].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrismTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = PrismColors.TextPrimary,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrismColors.TextPrimary,
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PrismColors.Background.copy(alpha = 0.72f),
            scrolledContainerColor = PrismColors.Background.copy(alpha = 0.92f),
            navigationIconContentColor = PrismColors.TextPrimary,
            actionIconContentColor = PrismColors.TextPrimary,
        ),
    )
}
