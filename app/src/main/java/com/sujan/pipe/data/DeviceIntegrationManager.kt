package com.sujan.pipe.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StrictMode
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import java.io.File
import java.util.ArrayDeque

/**
 * Handles device-level integrations:
 * 1. Loop-safe Browser & Deep-Link dispatching when `pipe` is set as Android's Default Browser.
 * 2. Reading device Contacts (`READ_CONTACTS`) and launching Call, SMS, or WhatsApp (`wa.me`).
 * 3. Two-Phase Storage Indexer:
 *    - Phase 1 indexes ALL directories across `/storage/emulated/0` (breadth-first up to depth 5)
 *      so folders are never starved by large file counts (e.g. 2,300+ files in `Download`).
 *    - Phase 2 indexes files with a balanced per-root quota.
 * 4. Directory Opener:
 *    - Never passes a `FileProvider` `content://` URI for directories (which causes `EISDIR` in MiXplorer).
 *    - Uses `file://` (`Uri.fromFile`) with relaxed `StrictMode.VmPolicy` for MiXplorer / third-party
 *      file managers, falling back to `DocumentsContract` (`com.android.externalstorage.documents`) for DocumentsUI.
 */
object DeviceIntegrationManager {

    private val DIRECT_FILE_URI_MANAGERS = listOf(
        "com.mixplorer.silver",
        "com.mixplorer",
        "com.mixplorer.beta",
        "pl.solidexplorer2",
        "com.lonelycatgames.Xplore",
        "me.zhanghai.android.files",
        "bin.mt.plus",
    )

    private val DOCUMENTS_UI_PACKAGES = listOf(
        "com.google.android.documentsui",
        "com.android.documentsui",
    )

    fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAllFilesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun loadDeviceContacts(context: Context): List<ContactItem> {
        if (!hasContactsPermission(context)) return emptyList()
        val list = mutableListOf<ContactItem>()
        val seen = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID)
                val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol).orEmpty()
                    val name = cursor.getString(nameCol)?.trim().orEmpty()
                    val num = cursor.getString(numCol)?.trim().orEmpty()
                    if (name.isNotEmpty() && num.isNotEmpty()) {
                        val dedupKey = "${name.lowercase()}_${num.filter { it.isDigit() }}"
                        if (seen.add(dedupKey)) {
                            list.add(ContactItem(id = id, displayName = name, phoneNumber = num))
                        }
                    }
                }
            }
        }
        return list
    }

    /**
     * Scans device storage using a Two-Phase Breadth-First strategy:
     * - Phase 1 collects ALL folders up to depth 5 across internal storage first, guaranteeing
     *   that every folder on the phone is searchable via `f <query>` even when `Download` has thousands of files.
     * - Phase 2 collects files with a per-directory cap so no single folder monopolizes the index.
     */
    /**
     * Comprehensive Full-Storage Indexer:
     * 1. Queries Android's `MediaStore.Files.getContentUri("external")` for all indexed files/folders.
     * 2. Walks `/storage/emulated/0` recursively with unlimited depth (up to depth 14, max 25,000 items),
     *    skipping only `/storage/emulated/0/Android/data` and `/storage/emulated/0/Android/obb` cache blobs.
     * 3. Stores relative parent paths (e.g. `Documents/Legal/Nagarikata`) so searching a folder name
     *    surfaces both the folder and every file inside it, and searching `sasu ama.jpg` finds it anywhere on the phone.
     */
    fun scanStorageFiles(
        context: Context,
        bookmarks: List<PipeBookmark>,
        scopedFolderPath: String? = null,
    ): List<LocalFileItem> {
        if (!hasAllFilesPermission(context)) return emptyList()
        val results = mutableListOf<LocalFileItem>()
        val seenPaths = HashSet<String>()
        val rootDir = Environment.getExternalStorageDirectory()
        val rootPrefix = rootDir.absolutePath

        fun computeRelativeParent(file: File): String {
            val parentPath = file.parentFile?.absolutePath ?: return "storage"
            if (parentPath == rootPrefix) return "storage"
            return if (parentPath.startsWith("$rootPrefix/")) {
                parentPath.removePrefix("$rootPrefix/")
            } else {
                file.parentFile?.name ?: "storage"
            }
        }

        if (scopedFolderPath != null) {
            val baseDir = File(scopedFolderPath)
            if (baseDir.exists() && baseDir.isDirectory) {
                crawlStorageBreadthFirst(
                    roots = listOf(baseDir),
                    maxDepth = 8,
                    maxItems = 5000,
                    seenPaths = seenPaths,
                    out = results,
                    relativeParentFn = ::computeRelativeParent,
                )
            }
            return results
        }

        // 1. Breadth-first full filesystem walk across `/storage/emulated/0` + bookmarked paths
        val roots = linkedSetOf<File>()
        if (rootDir.exists() && rootDir.isDirectory) roots.add(rootDir)
        bookmarks.forEach { bm ->
            val f = File(bm.target)
            if (f.exists() && f.isDirectory) roots.add(f)
        }

        crawlStorageBreadthFirst(
            roots = roots.toList(),
            maxDepth = 14,
            maxItems = 25_000,
            seenPaths = seenPaths,
            out = results,
            relativeParentFn = ::computeRelativeParent,
        )

        // 2. Supplement with MediaStore.Files in case any external volume or media entry was outside standard roots
        runCatching {
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                android.provider.MediaStore.Files.FileColumns.DATA,
                android.provider.MediaStore.Files.FileColumns.SIZE,
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataCol = cursor.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndex(android.provider.MediaStore.Files.FileColumns.SIZE)
                if (dataCol >= 0) {
                    while (cursor.moveToNext() && results.size < 30_000) {
                        val path = cursor.getString(dataCol) ?: continue
                        if (path.contains("/Android/data/") || path.contains("/Android/obb/") || path.contains("/.")) continue
                        if (seenPaths.add(path)) {
                            val f = File(path)
                            if (f.exists()) {
                                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else f.length()
                                results.add(
                                    LocalFileItem(
                                        name = f.name,
                                        absolutePath = path,
                                        isDirectory = f.isDirectory,
                                        parentName = computeRelativeParent(f),
                                        sizeBytes = size,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        return results
    }

    /**
     * Lists direct child files and subfolders of [folderPath] for inline folder peek (`[+]`) in the overlay.
     */
    fun listDirectChildren(folderPath: String): List<LocalFileItem> {
        val dir = File(folderPath)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .take(50)
            .map { child ->
                LocalFileItem(
                    name = child.name,
                    absolutePath = child.absolutePath,
                    isDirectory = child.isDirectory,
                    parentName = dir.name.ifEmpty { "storage" },
                    sizeBytes = if (child.isDirectory) 0L else child.length(),
                )
            }
    }

    private fun crawlStorageBreadthFirst(
        roots: List<File>,
        maxDepth: Int,
        maxItems: Int,
        seenPaths: HashSet<String>,
        out: MutableList<LocalFileItem>,
        relativeParentFn: (File) -> String,
    ) {
        val queue = ArrayDeque<Pair<File, Int>>()
        for (r in roots) {
            if (r.exists() && r.isDirectory) {
                queue.add(r to 0)
            }
        }

        while (queue.isNotEmpty() && out.size < maxItems) {
            val (dir, depth) = queue.removeFirst()
            if (depth > maxDepth || !dir.canRead()) continue
            val children = dir.listFiles() ?: continue

            // Sort directories and files so user folders are visited cleanly
            for (child in children) {
                if (out.size >= maxItems) break
                val name = child.name
                if (name.startsWith(".")) continue
                val abs = child.absolutePath
                if (abs.contains("/Android/data") || abs.contains("/Android/obb")) continue

                val isDir = child.isDirectory
                if (seenPaths.add(abs)) {
                    out.add(
                        LocalFileItem(
                            name = name,
                            absolutePath = abs,
                            isDirectory = isDir,
                            parentName = relativeParentFn(child),
                            sizeBytes = if (isDir) 0L else child.length(),
                        )
                    )
                }
                if (isDir && depth + 1 <= maxDepth) {
                    queue.add(child to (depth + 1))
                }
            }
        }
    }

    fun openUrlExcludingSelf(context: Context, url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val baseIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pm = context.packageManager
        val selfPkg = context.packageName

        val handlers = runCatching {
            pm.queryIntentActivities(baseIntent, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != selfPkg }
        }.getOrDefault(emptyList())

        if (handlers.isNotEmpty()) {
            val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val browserPkgs = runCatching {
                pm.queryIntentActivities(probeIntent, PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName }
                    .toSet()
            }.getOrDefault(emptySet())

            val nativeAppPkg = handlers.firstOrNull { it !in browserPkgs }
            val targetPkg = nativeAppPkg ?: handlers.first()

            val targetedIntent = Intent(baseIntent).apply {
                setPackage(targetPkg)
            }
            val launched = runCatching {
                context.startActivity(targetedIntent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }

        return try {
            context.startActivity(baseIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun dialContact(context: Context, phoneNumber: String): Boolean = try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    fun smsContact(context: Context, phoneNumber: String): Boolean = try {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phoneNumber)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    fun whatsAppContact(context: Context, normalizedDigits: String): Boolean {
        val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$normalizedDigits")
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            val ok = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, waUri).apply {
                        setPackage(pkg)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        return openUrlExcludingSelf(context, "https://wa.me/$normalizedDigits")
    }

    /**
     * Opens a local file or directory on the device:
     * - For DIRECTORIES: NEVER uses `FileProvider` (which causes `read failed: EISDIR` in MiXplorer).
     *   Instead, relaxes `StrictMode.VmPolicy` and passes a real `file://` URI (`Uri.fromFile(file)`)
     *   with `"resource/folder"` to MiXplorer / third-party file managers, or a `DocumentsContract`
     *   `com.android.externalstorage.documents` directory URI to Android's DocumentsUI.
     * - For FILES: Uses `FileProvider` `content://` URI with the file's MIME type.
     */
    fun openLocalFileOrFolder(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false

        if (file.isDirectory) {
            return openDirectory(context, file)
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri = runCatching {
            FileProvider.getUriForFile(context, authority, file)
        }.getOrElse { Uri.fromFile(file) }

        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mime)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            true
        }.getOrDefault(false)
    }

    private fun openDirectory(context: Context, dir: File): Boolean {
        // Relax StrictMode VmPolicy so passing `file:///storage/emulated/0/...` to MiXplorer works directly
        runCatching {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        }

        val directFileUri = Uri.fromFile(dir)
        val folderMimes = listOf(
            "resource/folder",
            "vnd.android.cursor.dir/file",
            "vnd.android.document/directory",
        )

        // 1. Try known file managers (MiXplorer Silver, MiXplorer, Solid Explorer, X-plore, Material Files) with real `file://` URI
        for (pkg in DIRECT_FILE_URI_MANAGERS) {
            for (mime in folderMimes) {
                val launched = runCatching {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(directFileUri, mime)
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
                if (launched) return true
            }
        }

        // 2. Try any installed app that resolves `file://` + `"resource/folder"`
        for (mime in folderMimes) {
            val launched = runCatching {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(directFileUri, mime)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }

        // 3. Build official Android ExternalStorageProvider DocumentsContract URI (`primary:Download`, etc.)
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath
        val relPath = if (dir.absolutePath.startsWith(externalRoot)) {
            dir.absolutePath.removePrefix(externalRoot).trimStart('/')
        } else {
            ""
        }
        val docId = if (relPath.isEmpty()) "primary:" else "primary:$relPath"
        val docUri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)

        for (pkg in DOCUMENTS_UI_PACKAGES) {
            val launched = runCatching {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }

        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
