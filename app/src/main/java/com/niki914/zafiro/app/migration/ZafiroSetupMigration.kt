package com.niki914.zafiro.app.migration

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SetupMigrationResult(
    val files: Int,
    val message: String,
)

object ZafiroSetupMigration {
    private const val MAX_TOTAL_BYTES = 25L * 1024L * 1024L

    suspend fun importBundle(context: Context, uri: Uri): SetupMigrationResult =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open migration bundle")
            val staged = File(context.cacheDir, "zafiro-migration-${System.nanoTime()}").apply {
                mkdirs()
            }
            var files = 0
            var totalBytes = 0L
            try {
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val relative = sanitize(entry.name) ?: continue
                        if (!isAllowed(relative)) continue

                        val target = File(staged, relative)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                totalBytes += read
                                check(totalBytes <= MAX_TOTAL_BYTES) {
                                    "Migration bundle is too large"
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                        if (relative.endsWith(".json", ignoreCase = true)) {
                            JSONObject(target.readText(Charsets.UTF_8))
                        }
                        files++
                    }
                }

                check(files > 0) { "No Zafiro setup files found in bundle" }

                staged.walkTopDown()
                    .filter { it.isFile }
                    .forEach { source ->
                        val relative = source.relativeTo(staged).invariantSeparatorsPath
                        val destination = File(context.filesDir, relative)
                        destination.parentFile?.mkdirs()
                        val temp = File(destination.parentFile, destination.name + ".migration")
                        source.copyTo(temp, overwrite = true)
                        if (destination.exists() && !destination.delete()) {
                            temp.delete()
                            error("Could not replace $relative")
                        }
                        if (!temp.renameTo(destination)) {
                            temp.copyTo(destination, overwrite = true)
                            temp.delete()
                        }
                    }

                SetupMigrationResult(
                    files = files,
                    message = "Imported $files setup files. Restart Zafiro Control to reload them.",
                )
            } finally {
                staged.deleteRecursively()
            }
        }

    suspend fun exportBundle(context: Context, uri: Uri): SetupMigrationResult =
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri, "w")
                ?: error("Unable to create migration bundle")
            val roots = listOf(
                File(context.filesDir, "local_settings.json"),
                File(context.filesDir, "settings"),
                File(context.filesDir, "skills"),
            )
            var files = 0
            ZipOutputStream(output.buffered()).use { zip ->
                roots.forEach { root ->
                    if (!root.exists()) return@forEach
                    if (root.isFile) {
                        addFile(zip, root, root.name)
                        files++
                    } else {
                        root.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relative = file.relativeTo(context.filesDir).invariantSeparatorsPath
                            if (isAllowed(relative)) {
                                addFile(zip, file, relative)
                                files++
                            }
                        }
                    }
                }
            }
            SetupMigrationResult(
                files = files,
                message = "Exported $files setup files.",
            )
        }

    private fun addFile(zip: ZipOutputStream, file: File, relative: String) {
        zip.putNextEntry(ZipEntry(relative))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun sanitize(name: String): String? {
        val normalized = name.replace('\\', '/').trimStart('/')
        if (normalized.isBlank()) return null
        if (normalized.split('/').any { it == ".." || it.isBlank() }) return null
        return normalized
    }

    private fun isAllowed(relative: String): Boolean {
        return relative == "local_settings.json" ||
                (relative.startsWith("settings/") && relative.endsWith(".json")) ||
                relative.startsWith("skills/")
    }
}
