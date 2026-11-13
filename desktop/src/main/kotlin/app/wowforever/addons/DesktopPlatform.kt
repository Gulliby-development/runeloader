package app.wowforever.addons

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Where the desktop app keeps its files, and where it looks for a game. */
class DesktopPlatform(private val home: File = appHome()) : Platform {
    private val prefs = FilePrefs(File(home, "settings.json"))

    override fun filesDir(): File = home.also { it.mkdirs() }
    override fun cacheDir(): File = File(home, "cache").also { it.mkdirs() }
    override fun prefs(): Platform.Prefs = prefs

    override fun searchRoots(): List<File> {
        val out = ArrayList<File>()
        val userHome = File(System.getProperty("user.home"))
        if (OS.windows) {
            for (drive in File.listRoots()) {
                out += drive
                out += File(drive, "Program Files")
                out += File(drive, "Program Files (x86)")
            }
        } else {
            out += userHome
            out += File(userHome, "Games")
            if (OS.mac) out += File("/Applications")
            // Wine and Proton prefixes keep Windows games under drive_c.
            out += File(userHome, ".wine/drive_c/Program Files")
            out += File(userHome, ".wine/drive_c/Program Files (x86)")
        }
        return out.filter { it.isDirectory }
    }

    companion object {
        fun appHome(): File {
            val userHome = System.getProperty("user.home")
            val dir = when {
                OS.windows -> File(System.getenv("APPDATA") ?: "$userHome/AppData/Roaming", "Runeloader")
                OS.mac -> File(userHome, "Library/Application Support/Runeloader")
                else -> File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "$userHome/.config", "runeloader")
            }
            dir.mkdirs()
            return dir
        }
    }
}

object OS {
    private val name = System.getProperty("os.name").lowercase()
    val windows = name.startsWith("windows")
    val mac = name.startsWith("mac")
}

/** Settings as one JSON file, written atomically on each change. */
private class FilePrefs(private val file: File) : Platform.Prefs {
    private val values: Json.Obj = try {
        if (file.isFile) Json.Obj(String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)) else Json.Obj()
    } catch (e: Exception) {
        Json.Obj()
    }

    @Synchronized override fun getString(k: String, def: String?): String? =
        if (values.isNull(k)) def else values.optString(k, def)
    @Synchronized override fun getLong(k: String, def: Long): Long = values.optLong(k, def)
    @Synchronized override fun getInt(k: String, def: Int): Int = values.optInt(k, def)
    @Synchronized override fun getBoolean(k: String, def: Boolean): Boolean = values.optBoolean(k, def)

    @Synchronized override fun putString(k: String, v: String?) { values.put(k, v); save() }
    @Synchronized override fun putLong(k: String, v: Long) { values.put(k, v); save() }
    @Synchronized override fun putInt(k: String, v: Int) { values.put(k, v); save() }
    @Synchronized override fun putBoolean(k: String, v: Boolean) { values.put(k, v); save() }
    @Synchronized override fun remove(k: String) { values.remove(k); save() }

    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        Files.write(tmp.toPath(), values.toString(2).toByteArray(StandardCharsets.UTF_8))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
