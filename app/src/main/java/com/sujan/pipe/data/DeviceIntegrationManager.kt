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
    fun scanStorageFiles(
        context: Context,
        bookmarks: List<PipeBookmark>,
        scopedFolderPath: String? = null,
    ): List<LocalFileItem> {
        if (!hasAllFilesPermission(context)) return emptyList()
        val results = mutableListOf<LocalFileItem>()
        val seenPaths = HashSet<String>()

        if (scopedFolderPath != null) {
            val baseDir = File(scopedFolderPath)
            if (baseDir.exists() && baseDir.isDirectory) {
                indexDirectoriesBreadthFirst(listOf(baseDir), maxDepth = 3, maxDirs = 500, seenPaths = seenPaths, out = results)
                indexFilesBalanced(listOf(baseDir), maxDepth = 2, maxFilesPerRoot = 600, maxTotalItems = 1200, seenPaths = seenPaths, out = results)
            }
            return results
        }

        val root = Environment.getExternalStorageDirectory()
        val priorityRoots = linkedSetOf<File>()
        if (root.exists() && root.isDirectory) priorityRoots.add(root)
        bookmarks.forEach { bm ->
            val f = File(bm.target)
            if (f.exists() && f.isDirectory) priorityRoots.add(f)
        }
        listOf("Download", "Documents", "Pictures", "DCIM", "Music", "Movies").forEach { sub ->
            val f = File(root, sub)
            if (f.exists() && f.isDirectory) priorityRoots.add(f)
        }

        // Phase 1: Index ALL folders across storage (breadth-first up to depth 5)
        indexDirectoriesBreadthFirst(
            roots = priorityRoots.toList(),
            maxDepth = 5,
            maxDirs = 1500,
            seenPaths = seenPaths,
            out = results,
        )

        // Phase 2: Index files evenly across top-level folders
        val fileRoots = priorityRoots.filter { it != root } + listOfNotNull(root.takeIf { it.exists() })
        indexFilesBalanced(
            roots = fileRoots,
            maxDepth = 2,
            maxFilesPerRoot = 350,
            maxTotalItems = 3500,
            seenPaths = seenPaths,
            out = results,
        )

        return results
    }

    private fun indexDirectoriesBreadthFirst(
        roots: List<File>,
        maxDepth: Int,
        maxDirs: Int,
        seenPaths: HashSet<String>,
        out: MutableList<LocalFileItem>,
    ) {
        val queue = ArrayDeque<Pair<File, Int>>()
        for (r in roots) {
            if (r.exists() && r.isDirectory) {
                // Also add the root folder itself (e.g. Download, Documents, DCIM) if it has a parent
                val abs = r.absolutePath
                if (r.parentFile != null && r.name.isNotEmpty() && r.name != "0" && seenPaths.add(abs)) {
                    out.add(
                        LocalFileItem(
                            name = r.name,
                            absolutePath = abs,
                            isDirectory = true,
                            parentName = r.parentFile?.name?.takeIf { it != "0" } ?: "storage",
                        )
                    )
                }
                queue.add(r to 0)
            }
        }

        var dirCount = 0
        while (queue.isNotEmpty() && dirCount < maxDirs) {
            val (dir, depth) = queue.removeFirst()
            if (depth >= maxDepth || !dir.canRead()) continue
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (!child.isDirectory) continue
                val name = child.name
                if (name.startsWith(".")) continue
                // Skip noisy app-private cache trees under /storage/emulated/0/Android/data & obb
                if (depth <= 1 && (name.equals("data", ignoreCase = true) || name.equals("obb", ignoreCase = true)) &&
                    dir.name.equals("Android", ignoreCase = true)
                ) {
                    continue
                }
                val abs = child.absolutePath
                if (seenPaths.add(abs)) {
                    out.add(
                        LocalFileItem(
                            name = name,
                            absolutePath = abs,
                            isDirectory = true,
                            parentName = dir.name.takeIf { it.isNotEmpty() && it != "0" } ?: "storage",
                        )
                    )
                    dirCount++
                    if (dirCount >= maxDirs) break
                }
                queue.add(child to (depth + 1))
            }
        }
    }

    private fun indexFilesBalanced(
        roots: List<File>,
        maxDepth: Int,
        maxFilesPerRoot: Int,
        maxTotalItems: Int,
        seenPaths: HashSet<String>,
        out: MutableList<LocalFileItem>,
    ) {
        for (root in roots) {
            if (out.size >= maxTotalItems) break
            var countForThisRoot = 0
            val queue = ArrayDeque<Pair<File, Int>>()
            queue.add(root to 0)

            while (queue.isNotEmpty() && countForThisRoot < maxFilesPerRoot && out.size < maxTotalItems) {
                val (dir, depth) = queue.removeFirst()
                if (!dir.canRead()) continue
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    val name = child.name
                    if (name.startsWith(".")) continue
                    if (child.isDirectory) {
                        if (depth == 0 && root.name == "0" && name in setOf("Download", "Documents", "Pictures", "DCIM", "Music", "Movies", "Android")) {
                            // Handled by dedicated roots
                            continue
                        }
                        if (depth + 1 <= maxDepth) {
                            queue.add(child to (depth + 1))
                        }
                    } else {
                        val abs = child.absolutePath
                        if (seenPaths.add(abs)) {
                            out.add(
                                LocalFileItem(
                                    name = name,
                                    absolutePath = abs,
                                    isDirectory = false,
                                    parentName = dir.name.takeIf { it.isNotEmpty() && it != "0" } ?: "storage",
                                )
                            )
                            countForThisRoot++
                            if (countForThisRoot >= maxFilesPerRoot || out.size >= maxTotalItems) break
                        }
                    }
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
