package com.voxy.files.data

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

data class LargestFile(val name: String, val sizeBytes: Long, val uri: Uri)
data class StorageReport(
    val folders: Int,
    val files: Int,
    val knownBytes: Long,
    val largestFiles: List<LargestFile>
)

/** SAF-only operations: never treat content:// URIs as raw filesystem paths. */
object StorageRepository {
    fun list(folder: DocumentFile): List<DocumentFile> =
        folder.listFiles().sortedWith(
            compareBy<DocumentFile> { !it.isDirectory }
                .thenBy { it.name.orEmpty().lowercase() }
        )

    fun createFolder(parent: DocumentFile, name: String): DocumentFile {
        val safeName = name.trim()
        require(safeName.isNotEmpty()) { "Folder name cannot be empty." }
        require(parent.findFile(safeName) == null) { "An item with this name already exists." }
        return parent.createDirectory(safeName)
            ?: throw IOException("The storage provider could not create the folder.")
    }

    fun createTextFile(parent: DocumentFile, name: String, resolver: ContentResolver): DocumentFile {
        val safeName = name.trim()
        require(safeName.isNotEmpty()) { "File name cannot be empty." }
        require(parent.findFile(safeName) == null) { "An item with this name already exists." }
        val file = parent.createFile("text/plain", safeName)
            ?: throw IOException("The storage provider could not create the file.")
        try {
            resolver.openOutputStream(file.uri, "w")?.use { it.write(ByteArray(0)) }
                ?: throw IOException("Could not open the new file for writing.")
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return file
    }

    fun rename(file: DocumentFile, newName: String): Boolean {
        val safeName = newName.trim()
        require(safeName.isNotEmpty()) { "Name cannot be empty." }
        return file.renameTo(safeName)
    }

    fun delete(files: List<DocumentFile>): Int {
        var deleted = 0
        files.forEach { if (it.delete()) deleted++ }
        return deleted
    }

    fun copy(resolver: ContentResolver, source: DocumentFile, destination: DocumentFile): DocumentFile {
        val name = source.name ?: throw IOException("The source has no display name.")
        if (destination.findFile(name) != null) {
            throw IOException("An item named '$name' already exists in the destination.")
        }
        if (source.isDirectory) {
            val targetDirectory = destination.createDirectory(name)
                ?: throw IOException("Could not create destination folder '$name'.")
            source.listFiles().forEach { child -> copy(resolver, child, targetDirectory) }
            return targetDirectory
        }

        val target = destination.createFile(source.type ?: "application/octet-stream", name)
            ?: throw IOException("Could not create destination file '$name'.")
        try {
            val input = resolver.openInputStream(source.uri)
                ?: throw IOException("Could not read '${source.name}'.")
            val output = resolver.openOutputStream(target.uri, "w")
                ?: throw IOException("Could not write '${target.name}'.")
            input.use { sourceStream ->
                output.use { targetStream -> sourceStream.copyTo(targetStream) }
            }
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun analyze(root: DocumentFile): StorageReport {
        var folderCount = 0
        var fileCount = 0
        var knownBytes = 0L
        val largest = mutableListOf<LargestFile>()
        val visited = HashSet<String>()

        fun walk(folder: DocumentFile) {
            if (!visited.add(folder.uri.toString())) return
            folder.listFiles().forEach { item ->
                if (item.isDirectory) {
                    folderCount++
                    walk(item)
                } else if (item.isFile) {
                    fileCount++
                    val size = item.length().coerceAtLeast(0L)
                    knownBytes += size
                    largest += LargestFile(item.name ?: "Unnamed file", size, item.uri)
                }
            }
        }

        walk(root)
        return StorageReport(folderCount, fileCount, knownBytes,
            largest.sortedByDescending { it.sizeBytes }.take(10))
    }
}
