package com.sujan.pipe.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sujan.pipe.model.ContactItem
import com.sujan.pipe.model.LocalFileItem
import com.sujan.pipe.model.PipeBookmark
import java.io.File

/**
 * Handles device-level integrations:
 * 1. Loop-safe Browser & Deep-Link dispatching when `pipe` is set as Android's Default Browser.
 * 2. Reading device Contacts (`READ_CONTACTS`) and launching Call, SMS, or WhatsApp (`wa.me`).
 * 3. Scanning device files/folders (`MANAGE_EXTERNAL_STORAGE`) and opening files or folders in
 *    installed file managers (e.g. MiXplorer, DocumentsUI) or media/document viewers.
 */
object DeviceIntegrationManager {

    private val KNOWN_FILE_MANAGER_PACKAGES = listOf(
        "com.mixplorer.silver",
        "com.mixplorer",
        "me.zhanghai.android.files",
        "pl.solidexplorer2",
        "com.lonelycatgames.Xplore",
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

    /**
     * Queries device contacts (name + phone number), deduplicating identical name+number pairs.
     */
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
     * Scans standard storage directories plus any bookmarked folders up to 2 levels deep
     * so fuzzy file/folder search works instantaneously as the user types.
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
            val dir = File(scopedFolderPath)
            collectDirectoryEntries(dir, maxDepth = 2, currentDepth = 0, seenPaths = seenPaths, out = results, maxItems = 400)
            return results
        }

        val root = Environment.getExternalStorageDirectory()
        val rootsToScan = linkedSetOf<File>()
        bookmarks.forEach { bm ->
            val f = File(bm.target)
            if (f.exists() && f.isDirectory) rootsToScan.add(f)
        }
        listOf("Download", "Documents", "Pictures", "DCIM", "Music", "Movies").forEach { sub ->
            val f = File(root, sub)
            if (f.exists() && f.isDirectory) rootsToScan.add(f)
        }
        if (root.exists()) rootsToScan.add(root)

        for (dir in rootsToScan) {
            val depth = if (dir == root) 1 else 2
            collectDirectoryEntries(dir, maxDepth = depth, currentDepth = 0, seenPaths = seenPaths, out = results, maxItems = 800)
        }
        return results
    }

    private fun collectDirectoryEntries(
        dir: File,
        maxDepth: Int,
        currentDepth: Int,
        seenPaths: HashSet<String>,
        out: MutableList<LocalFileItem>,
        maxItems: Int,
    ) {
        if (out.size >= maxItems || currentDepth > maxDepth || !dir.exists() || !dir.canRead()) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (out.size >= maxItems) break
            val name = child.name
            if (name.startsWith(".")) continue
            if (currentDepth == 0 && name.equals("Android", ignoreCase = true)) continue
            val abs = child.absolutePath
            if (seenPaths.add(abs)) {
                out.add(
                    LocalFileItem(
                        name = name,
                        absolutePath = abs,
                        isDirectory = child.isDirectory,
                        parentName = dir.name.ifEmpty { "storage" },
                    )
                )
            }
            if (child.isDirectory && currentDepth + 1 <= maxDepth) {
                collectDirectoryEntries(child, maxDepth, currentDepth + 1, seenPaths, out, maxItems)
            }
        }
    }

    /**
     * Opens [url] while avoiding self-looping when `com.sujan.pipe` is set as Default Browser:
     * 1. First checks if a native domain app (e.g. YouTube, GitHub, Reddit, Maps) handles [url].
     * 2. Otherwise routes to an installed browser package that is NOT `context.packageName`.
     */
    fun openUrlExcludingSelf(context: Context, url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val baseIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pm = context.packageManager
        val selfPkg = context.packageName

        // Query all activities capable of handling this URL
        val handlers = runCatching {
            pm.queryIntentActivities(baseIntent, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != selfPkg }
        }.getOrDefault(emptyList())

        if (handlers.isNotEmpty()) {
            // Query generic http://example.com browsers so we can distinguish domain-specific native apps
            val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val browserPkgs = runCatching {
                pm.queryIntentActivities(probeIntent, PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName }
                    .toSet()
            }.getOrDefault(emptySet())

            // Prefer a dedicated native app (e.g. YouTube, GitHub, Maps) if one matches the URL!
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
     * Opens a local file or folder on the device using `FileProvider` and installed file managers
     * (prioritizing MiXplorer Silver / Material Files / Solid Explorer / DocumentsUI for directories).
     */
    fun openLocalFileOrFolder(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        val authority = "${context.packageName}.fileprovider"
        val contentUri = runCatching {
            FileProvider.getUriForFile(context, authority, file)
        }.getOrElse { Uri.fromFile(file) }

        if (file.isDirectory) {
            val pm = context.packageManager
            val mimeCandidates = listOf(
                "resource/folder",
                "vnd.android.document/directory",
                "*/*",
            )
            // 1. Try known file managers (MiXplorer, Material Files, Solid Explorer, DocumentsUI)
            for (pkg in KNOWN_FILE_MANAGER_PACKAGES) {
                for (mime in mimeCandidates) {
                    val ok = runCatching {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, mime)
                            setPackage(pkg)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        if (intent.resolveActivity(pm) != null) {
                            context.startActivity(intent)
                            true
                        } else {
                            false
                        }
                    }.getOrDefault(false)
                    if (ok) return true
                }
            }
            // 2. Generic folder intent fallback
            for (mime in listOf("resource/folder", "vnd.android.document/directory")) {
                val ok = runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, mime)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                    true
                }.getOrDefault(false)
                if (ok) return true
            }
            return false
        } else {
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
    }
}
