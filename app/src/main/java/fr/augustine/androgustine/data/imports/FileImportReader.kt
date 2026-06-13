package fr.augustine.androgustine.data.imports

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

data class FileImportSummary(
    val name: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val characterCount: Int,
    val preview: String
)

fun readFileImportSummary(contentResolver: ContentResolver, uri: Uri): FileImportSummary {
    val displayName: String?
    val sizeBytes: Long?

    contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null
    ).use { cursor ->
        if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            displayName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                cursor.getString(nameIndex)
            } else {
                null
            }
            sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                null
            }
        } else {
            displayName = null
            sizeBytes = null
        }
    }

    val text = contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
        reader?.readText() ?: throw IllegalStateException("Impossible d'ouvrir le flux du fichier.")
    }

    return FileImportSummary(
        name = displayName,
        mimeType = contentResolver.getType(uri),
        sizeBytes = sizeBytes,
        characterCount = text.length,
        preview = text.take(200)
    )
}
