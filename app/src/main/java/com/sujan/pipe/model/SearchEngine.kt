package com.sujan.pipe.model

enum class EngineColorRole(val label: String) {
    ACCENT("lime"),
    ACCENT2("peach"),
    ACCENT3("sky"),
    GOOD("mint"),
    WARN("amber"),
    FG("ink");

    companion object {
        fun fromString(raw: String?): EngineColorRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACCENT2
    }
}

object EngineGlyphs {
    val ALL = listOf("|", ">", "*", "#", "@", "?", "$", "~")
}

/**
 * Preferred WhatsApp app flavor when opening WhatsApp chats (`com.whatsapp` vs `com.whatsapp.w4b`).
 */
enum class WhatsAppFlavor(val label: String, val badge: String, val packageName: String?) {
    BOTH("Show both [wa] & [wa.b] buttons", "wa/b", null),
    PERSONAL("WhatsApp (Personal)", "wa", "com.whatsapp"),
    BUSINESS("WhatsApp Business", "wa.b", "com.whatsapp.w4b");

    companion object {
        fun fromString(raw: String?): WhatsAppFlavor =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BOTH
    }
}

/**
 * User-customizable command prefixes and row badge tags for `f`, `s`, `b`, `app`, `hist`, `c`, `def`, `clip`, and `set`.
 */
data class PrefixConfig(
    val files: String = "f",
    val pipes: String = "s",
    val bookmarks: String = "b",
    val apps: String = "app",
    val history: String = "hist",
    val contacts: String = "c",
    val dictionary: String = "def",
    val clipboard: String = "clip",
    val systemSettings: String = "set",
) {
    val filesClean: String get() = files.trim().lowercase().ifEmpty { "f" }
    val pipesClean: String get() = pipes.trim().lowercase().ifEmpty { "s" }
    val bookmarksClean: String get() = bookmarks.trim().lowercase().ifEmpty { "b" }
    val appsClean: String get() = apps.trim().lowercase().ifEmpty { "app" }
    val historyClean: String get() = history.trim().lowercase().ifEmpty { "hist" }
    val contactsClean: String get() = contacts.trim().lowercase().ifEmpty { "c" }
    val dictionaryClean: String get() = dictionary.trim().lowercase().ifEmpty { "def" }
    val clipboardClean: String get() = clipboard.trim().lowercase().ifEmpty { "clip" }
    val systemSettingsClean: String get() = systemSettings.trim().lowercase().ifEmpty { "set" }
}

/**
 * Universal Alias entry allowing ANY item that `pipe` searches (system settings, app settings,
 * apps, app shortcuts, files, folders, contacts, bookmarks, pipes) to be assigned a short alias.
 */
data class UniversalAlias(
    val targetKey: String,
    val alias: String,
    val label: String,
    val category: String,
    val subtitle: String = "",
) {
    val aliasLower: String = alias.trim().lowercase()
}

/**
 * Discoverable App Shortcut or App Settings activity (parsed from static `android.app.shortcuts` XML,
 * `LauncherApps`, `ACTION_APPLICATION_PREFERENCES`, or per-app Android App Info settings).
 */
enum class AppShortcutKind(val badge: String) {
    STATIC_SHORTCUT("cut"),
    DYNAMIC_SHORTCUT("cut"),
    APP_PREFERENCES("cfg"),
    APP_SYSTEM_INFO("inf"),
}

data class AppShortcutItem(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val shortcutLabel: String,
    val kind: AppShortcutKind = AppShortcutKind.STATIC_SHORTCUT,
    val targetClass: String? = null,
    val intentAction: String? = null,
    val dataUri: String? = null,
    val launcherShortcutId: String? = null,
) {
    val usageKey: String = "shortcut:$packageName:$id"
    val appLabelLower: String = appLabel.trim().lowercase()
    val shortcutLabelLower: String = shortcutLabel.trim().lowercase()
    val combinedLower: String = "$appLabelLower $shortcutLabelLower"
}

/**
 * Deep Android system settings shortcut (`set dev`, `set spot`, `set dns`, `set notif`, etc.)
 * as well as internal `pipe` settings sections (`pipe://settings/overlay`, etc.).
 */
data class SystemSettingShortcut(
    val id: String,
    val alias: String,
    val title: String,
    val subtitle: String,
    val intentAction: String,
    val fallbackAction: String? = null,
    val keywords: String = "",
    val internalPipeSection: String? = null,
) {
    val aliasLower: String = alias.trim().lowercase()
    val titleLower: String = title.trim().lowercase()
    val keywordsLower: String = keywords.trim().lowercase()
}

object DefaultSystemShortcuts {
    val PIPE_INTERNAL_LIST: List<SystemSettingShortcut> = listOf(
        SystemSettingShortcut(
            id = "pipe_set_overlay",
            alias = "ovl",
            title = "Pipe Settings: Floating Overlay",
            subtitle = "Opacity, position, corner radius, placeholder text",
            intentAction = "pipe://settings/OVERLAY",
            keywords = "pipe settings overlay opacity corner radius placeholder",
            internalPipeSection = "OVERLAY",
        ),
        SystemSettingShortcut(
            id = "pipe_set_index",
            alias = "idx",
            title = "Pipe Settings: Storage & Offline Dictionary",
            subtitle = "Permissions, storage index, excluded folders, offline dictionary",
            intentAction = "pipe://settings/INDEX",
            keywords = "pipe settings index dictionary permissions storage",
            internalPipeSection = "INDEX",
        ),
        SystemSettingShortcut(
            id = "pipe_set_theme",
            alias = "thm",
            title = "Pipe Settings: Theme & Color Palette",
            subtitle = "System/Dark/Light mode, Dracula, Alucard, Moss, Lagoon palettes",
            intentAction = "pipe://settings/THEME",
            keywords = "pipe settings theme palette color dark light dracula",
            internalPipeSection = "THEME",
        ),
        SystemSettingShortcut(
            id = "pipe_set_routing",
            alias = "rte",
            title = "Pipe Settings: Routing, Prefixes & WhatsApp",
            subtitle = "Custom prefixes (f, s, b, app, set), default browser, WhatsApp flavor",
            intentAction = "pipe://settings/ROUTING",
            keywords = "pipe settings routing prefix browser whatsapp",
            internalPipeSection = "ROUTING",
        ),
        SystemSettingShortcut(
            id = "pipe_set_backup",
            alias = "bak",
            title = "Pipe Settings: JSON Backup & Restore",
            subtitle = "Export/import full v2 JSON backup (pipes, bookmarks, aliases)",
            intentAction = "pipe://settings/BACKUP",
            keywords = "pipe settings backup export import json restore",
            internalPipeSection = "BACKUP",
        ),
        SystemSettingShortcut(
            id = "pipe_app_info",
            alias = "pipeinfo",
            title = "Pipe System App Info & Permissions",
            subtitle = "Open Android App Info screen for com.sujan.pipe",
            intentAction = "pipe://appinfo",
            keywords = "pipe app info permissions system settings",
            internalPipeSection = "APP_INFO",
        ),
    )

    val ANDROID_SYSTEM_LIST: List<SystemSettingShortcut> = listOf(
        SystemSettingShortcut(
            id = "sys_dev",
            alias = "dev",
            title = "Developer Options & Wireless ADB",
            subtitle = "USB/Wireless debugging, stay awake, animation scale",
            intentAction = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
            keywords = "adb debug wireless developer options build",
        ),
        SystemSettingShortcut(
            id = "sys_spot",
            alias = "spot",
            title = "Wi-Fi Hotspot & Tethering",
            subtitle = "Portable Wi-Fi hotspot, USB & Bluetooth tethering",
            intentAction = "android.settings.TETHER_SETTINGS",
            fallbackAction = "android.settings.WIRELESS_SETTINGS",
            keywords = "hotspot tether tethering ap wifi share",
        ),
        SystemSettingShortcut(
            id = "sys_dns",
            alias = "dns",
            title = "Private DNS & Connectivity",
            subtitle = "Configure Private DNS (AdGuard / Cloudflare), NFC, airplane",
            intentAction = "android.settings.WIRELESS_SETTINGS",
            keywords = "private dns adguard cloudflare network wireless",
        ),
        SystemSettingShortcut(
            id = "sys_wifi",
            alias = "wifi",
            title = "Wi-Fi Networks",
            subtitle = "Connect to Wi-Fi, QR share, saved networks",
            intentAction = "android.settings.WIFI_SETTINGS",
            keywords = "wifi wlan internet wireless",
        ),
        SystemSettingShortcut(
            id = "sys_ip",
            alias = "ip",
            title = "Wi-Fi IP, MAC & Advanced",
            subtitle = "Static IP, gateway, subnet, MAC address",
            intentAction = "android.settings.WIFI_IP_SETTINGS",
            fallbackAction = "android.settings.WIFI_SETTINGS",
            keywords = "ip address mac static gateway wifi",
        ),
        SystemSettingShortcut(
            id = "sys_vpn",
            alias = "vpn",
            title = "VPN Settings",
            subtitle = "Always-on VPN profiles & WireGuard/Tailscale toggles",
            intentAction = "android.settings.VPN_SETTINGS",
            keywords = "vpn wireguard tailscale tunnel",
        ),
        SystemSettingShortcut(
            id = "sys_sim",
            alias = "sim",
            title = "SIM Cards & Mobile Network",
            subtitle = "Preferred SIM for data/calls, VoLTE, data roaming",
            intentAction = "android.settings.DATA_ROAMING_SETTINGS",
            fallbackAction = "android.settings.NETWORK_OPERATOR_SETTINGS",
            keywords = "sim ntc ncell mobile data roaming cellular lte 5g",
        ),
        SystemSettingShortcut(
            id = "sys_usage",
            alias = "usage",
            title = "Data Usage Monitor",
            subtitle = "Mobile & Wi-Fi data usage breakdown",
            intentAction = "android.settings.DATA_USAGE_SETTINGS",
            keywords = "data usage bandwidth limit",
        ),
        SystemSettingShortcut(
            id = "sys_bt",
            alias = "bt",
            title = "Bluetooth Devices",
            subtitle = "Pair earbuds, keyboards, and audio devices",
            intentAction = "android.settings.BLUETOOTH_SETTINGS",
            keywords = "bluetooth audio earbuds pair",
        ),
        SystemSettingShortcut(
            id = "sys_bat",
            alias = "bat",
            title = "Battery Usage & Health",
            subtitle = "Screen-on time, per-app battery drain, power saver",
            intentAction = "android.intent.action.POWER_USAGE_SUMMARY",
            fallbackAction = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
            keywords = "battery power charge drain saver",
        ),
        SystemSettingShortcut(
            id = "sys_opt",
            alias = "opt",
            title = "Battery Optimization Exceptions",
            subtitle = "Allow background apps (Termux, Shunt, Pipe) unrestricted battery",
            intentAction = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
            keywords = "battery optimization unrestricted background doze",
        ),
        SystemSettingShortcut(
            id = "sys_notif",
            alias = "notif",
            title = "Notification Listener Access",
            subtitle = "Grant notification read/intercept access to utility apps",
            intentAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
            keywords = "notification listener access shunt intercept",
        ),
        SystemSettingShortcut(
            id = "sys_draw",
            alias = "draw",
            title = "Display Over Other Apps",
            subtitle = "Manage floating overlay window permissions",
            intentAction = "android.settings.action.MANAGE_OVERLAY_PERMISSION",
            keywords = "overlay draw floating window permission",
        ),
        SystemSettingShortcut(
            id = "sys_store",
            alias = "store",
            title = "Storage Manager",
            subtitle = "Internal storage breakdown and cache cleanup",
            intentAction = "android.settings.INTERNAL_STORAGE_SETTINGS",
            keywords = "storage disk space free clean",
        ),
        SystemSettingShortcut(
            id = "sys_defaults",
            alias = "defaults",
            title = "Default Apps (Browser, Assist, Links)",
            subtitle = "Choose default browser, digital assistant, and link handlers",
            intentAction = "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
            keywords = "default apps browser assist open with hermit",
        ),
        SystemSettingShortcut(
            id = "sys_acc",
            alias = "acc",
            title = "Accessibility Services",
            subtitle = "UbikiTouch, gesture overlays, and automation services",
            intentAction = "android.settings.ACCESSIBILITY_SETTINGS",
            keywords = "accessibility ubikitouch gesture automation",
        ),
        SystemSettingShortcut(
            id = "sys_kbd",
            alias = "kbd",
            title = "Keyboards & Input Methods",
            subtitle = "Manage active keyboards, Devanagari input, and autofill",
            intentAction = "android.settings.INPUT_METHOD_SETTINGS",
            keywords = "keyboard ime input method language typing",
        ),
        SystemSettingShortcut(
            id = "sys_loc",
            alias = "loc",
            title = "Location & GPS Services",
            subtitle = "Toggle GPS and location permissions",
            intentAction = "android.settings.LOCATION_SOURCE_SETTINGS",
            keywords = "location gps maps",
        ),
        SystemSettingShortcut(
            id = "sys_snd",
            alias = "snd",
            title = "Sound, Vibration & DND",
            subtitle = "Ring/media volumes, Do Not Disturb, ringtones",
            intentAction = "android.settings.SOUND_SETTINGS",
            keywords = "sound volume mute dnd vibration",
        ),
        SystemSettingShortcut(
            id = "sys_disp",
            alias = "disp",
            title = "Display, Dark Mode & Refresh Rate",
            subtitle = "Brightness, screen timeout, font size, refresh rate",
            intentAction = "android.settings.DISPLAY_SETTINGS",
            keywords = "display screen brightness dark mode timeout",
        ),
        SystemSettingShortcut(
            id = "sys_sec",
            alias = "sec",
            title = "Security & Biometrics",
            subtitle = "Fingerprint, lock screen, device admin, encryption",
            intentAction = "android.settings.SECURITY_SETTINGS",
            keywords = "security fingerprint lock pin biometric",
        ),
        SystemSettingShortcut(
            id = "sys_date",
            alias = "date",
            title = "Date, Time & Time Zone",
            subtitle = "24-hour format, automatic time zone",
            intentAction = "android.settings.DATE_SETTINGS",
            keywords = "date time clock timezone ntp",
        ),
    )

    val LIST: List<SystemSettingShortcut> = PIPE_INTERNAL_LIST + ANDROID_SYSTEM_LIST
    val ALL_LIST: List<SystemSettingShortcut> = LIST
}

/**
 * Offline dictionary entry retrieved on demand via `OfflineDictionaryManager.searchOnDemand()`.
 */
data class DictionaryEntry(
    val word: String,
    val partOfSpeech: String = "def",
    val definition: String,
    val synonyms: String = "",
    val example: String = "",
) {
    val wordLower: String = word.trim().lowercase()
}

/**
 * Full v2 JSON backup bundle containing search pipes, bookmarks, custom prefixes, excluded folders, usage counts, and universal aliases.
 */
data class FullBackupBundle(
    val version: Int = 2,
    val engines: List<SearchEngine>,
    val bookmarks: List<PipeBookmark> = emptyList(),
    val prefixes: PrefixConfig? = null,
    val excludedFolders: List<String> = emptyList(),
    val usageCounts: Map<String, Int> = emptyMap(),
    val universalAliases: List<UniversalAlias> = emptyList(),
)

data class SearchEngine(
    val id: String,
    val name: String,
    val keyword: String,
    val urlTemplate: String,
    val glyph: String = "|",
    val colorRole: EngineColorRole = EngineColorRole.ACCENT2,
    val isDefault: Boolean = false,
) {
    val homeUrl: String
        get() {
            val trimmed = urlTemplate.trim()
            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd <= 0) return trimmed
            val authorityStart = schemeEnd + 3
            val pathStart = trimmed.indexOf('/', authorityStart)
            if (pathStart < 0) return trimmed
            val origin = trimmed.substring(0, pathStart)
            val pathPart = trimmed.substring(pathStart)
            return if (pathPart.startsWith("/maps/")) "$origin/maps" else origin
        }
}

enum class BookmarkKind(val badge: String) {
    FOLDER("dir"),
    FILE("file"),
    LINK("url");

    companion object {
        fun fromString(raw: String?): BookmarkKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LINK
    }
}

enum class ContactQuickAction(val badge: String) {
    CALL("call"),
    SMS("sms"),
    WHATSAPP("wa"),
    WHATSAPP_BUSINESS("wa.b"),
}

data class PipeBookmark(
    val id: String,
    val alias: String,
    val title: String,
    val target: String,
    val kind: BookmarkKind,
    val tags: String = "",
) {
    val tagsLower: String = tags.trim().lowercase()
}

data class InstalledAppItem(
    val label: String,
    val packageName: String,
) {
    val labelLower: String = label.trim().lowercase()
    val pkgLower: String = packageName.trim().lowercase()
}

data class ContactItem(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String? = null,
) {
    val nameLower: String = displayName.trim().lowercase()
    val cleanPhone: String = phoneNumber.filter { it.isDigit() }
    val monogram: String = run {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        when {
            parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
            parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
            parts.size == 1 -> parts[0].uppercase()
            else -> "@"
        }
    }
}

data class LocalFileItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val parentName: String,
    val sizeBytes: Long = 0L,
) {
    val nameLower: String = name.trim().lowercase()
    val parentLower: String = parentName.trim().lowercase()
    val pathLower: String = absolutePath.trim().lowercase()

    val isImageFile: Boolean = !isDirectory && (
        nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") ||
            nameLower.endsWith(".png") || nameLower.endsWith(".webp") ||
            nameLower.endsWith(".bmp") || nameLower.endsWith(".gif")
        )

    val formattedSize: String
        get() {
            if (isDirectory) return "dir"
            if (sizeBytes <= 0L) return "file"
            val kb = sizeBytes / 1024.0
            if (kb < 1024.0) return "${kb.toInt().coerceAtLeast(1)} KB"
            val mb = kb / 1024.0
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }
}

data class SearchHistoryEntry(
    val id: Long,
    val rawInput: String,
    val engineName: String,
    val engineKeyword: String,
    val queryText: String,
    val resolvedUrl: String,
    val timestampEpochMs: Long,
)

object DefaultEngines {
    val YT_TIME_PIPES: List<SearchEngine> = listOf(
        SearchEngine(
            id = "eng_yt_today",
            name = "YouTube Today",
            keyword = "ytt",
            urlTemplate = "https://www.youtube.com/results?search_query={query}&sp=EgIIAg%3D%3D",
            glyph = "|",
            colorRole = EngineColorRole.ACCENT2,
        ),
        SearchEngine(
            id = "eng_yt_week",
            name = "YouTube Last Week",
            keyword = "ytw",
            urlTemplate = "https://www.youtube.com/results?search_query={query}&sp=EgIIAw%3D%3D",
            glyph = "|",
            colorRole = EngineColorRole.ACCENT2,
        ),
        SearchEngine(
            id = "eng_yt_month",
            name = "YouTube This Month",
            keyword = "ytm",
            urlTemplate = "https://www.youtube.com/results?search_query={query}&sp=EgIIBA%3D%3D",
            glyph = "|",
            colorRole = EngineColorRole.ACCENT2,
        ),
    )

    val LIST: List<SearchEngine> = listOf(
        SearchEngine("eng_google", "Google", "g", "https://www.google.com/search?q={query}", "|", EngineColorRole.ACCENT, isDefault = true),
        SearchEngine("eng_youtube", "YouTube", "yt", "https://www.youtube.com/results?search_query={query}", "|", EngineColorRole.ACCENT2),
    ) + YT_TIME_PIPES + listOf(
        SearchEngine("eng_wikipedia", "Wikipedia", "w", "https://en.wikipedia.org/w/index.php?search={query}", "|", EngineColorRole.FG),
        SearchEngine("eng_maps", "Maps", "maps", "https://www.google.com/maps/search/{query}", "|", EngineColorRole.GOOD),
        SearchEngine("eng_github", "GitHub", "gh", "https://github.com/search?q={query}", "|", EngineColorRole.ACCENT3),
        SearchEngine("eng_amazon", "Amazon", "a", "https://www.amazon.com/s?k={query}", "|", EngineColorRole.WARN),
        SearchEngine("eng_reddit", "Reddit", "r", "https://www.reddit.com/search/?q={query}", "|", EngineColorRole.ACCENT2),
    )
}

object DefaultBookmarks {
    val LIST: List<PipeBookmark> = listOf(
        PipeBookmark("bm_dl", "dl", "Downloads", "/storage/emulated/0/Download", BookmarkKind.FOLDER),
        PipeBookmark("bm_docs", "docs", "Documents", "/storage/emulated/0/Documents", BookmarkKind.FOLDER),
        PipeBookmark("bm_dcim", "dcim", "DCIM / Camera", "/storage/emulated/0/DCIM", BookmarkKind.FOLDER),
        PipeBookmark("bm_pic", "pic", "Pictures", "/storage/emulated/0/Pictures", BookmarkKind.FOLDER),
        PipeBookmark("bm_music", "music", "Music", "/storage/emulated/0/Music", BookmarkKind.FOLDER),
        PipeBookmark("bm_sd", "sd", "Internal Storage", "/storage/emulated/0", BookmarkKind.FOLDER),
    )
}
