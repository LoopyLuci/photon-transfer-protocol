package com.prism.ot.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.prism.ot.core.PrismLogger
import com.prism.ot.data.model.TransferResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a verified reconstructed file to the rest of the system.
 *
 * Two delivery modes:
 *  1. **APK**: fires `ACTION_VIEW` with `application/vnd.android.package-archive`
 *     and a content URI, so the system installer picks it up. Requires
 *     `REQUEST_INSTALL_PACKAGES` on API 26+; the manifest declares it and
 *     the UI must surface the user-facing settings prompt.
 *  2. **Everything else**: fires `ACTION_SEND` with a content URI and a
 *     chooser, which lets the user save to Drive, email it, or open it in
 *     a compatible app.
 *
 * In both cases the grant is temporary and read-only.
 */
@Singleton
class TransferResultDeliverer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: PrismLogger,
) {

    fun buildOpenIntent(result: TransferResult.Success): Intent? {
        val uri = result.outputUri
        return when {
            result.metadata.mimeType == APK_MIME -> {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            else -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = result.metadata.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, result.metadata.fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
    }

    fun launch(result: TransferResult.Success, chooserTitle: String = "Open with") {
        val intent = buildOpenIntent(result) ?: return
        runCatching {
            if (result.metadata.mimeType == APK_MIME) {
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }.onFailure { logger.e(TAG, "delivery failed", it) }
    }

    companion object {
        private const val TAG = "TransferResultDeliverer"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
