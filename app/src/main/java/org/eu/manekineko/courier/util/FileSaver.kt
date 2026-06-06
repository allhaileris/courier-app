package org.eu.manekineko.courier.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

object FileSaver {

    private const val DIRECTORY_NAME = "Courier"
    private const val MIME_TYPE_JSON = "application/json"

    fun saveJsonFile(context: Context, fileName: String, jsonContent: String): Result<Uri> {
        return try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Files.FileColumns.MIME_TYPE, MIME_TYPE_JSON)
                put(
                    android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY_NAME"
                )
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.getContentUri("external"),
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray())
                    outputStream.flush()
                }
                Result.success(uri)
            } ?: run {
                android.util.Log.e("FileSaver", "Не удалось создать файл: uri is null")
                Result.failure(Exception("Не удалось создать файл"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FileSaver", "Ошибка при сохранении файла: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getSaveDirectoryPath(): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY_NAME"
    }
}
