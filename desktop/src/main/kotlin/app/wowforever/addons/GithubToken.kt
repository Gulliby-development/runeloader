package app.wowforever.addons

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * The user's own GitHub token, in its own file in the app folder (on Windows under the user's
 * AppData, which only that user can read; elsewhere the file is made owner-only). Core sends it to
 * api.github.com only (Http.githubToken).
 */
object GithubToken {
    private fun file() = File(DesktopPlatform.appHome(), "github-token")

    fun load(): String? = try { file().takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() } } catch (e: Exception) { null }

    fun save(token: String?) {
        val f = file()
        if (token.isNullOrBlank()) { f.delete(); return }
        f.parentFile.mkdirs()
        f.writeText(token.trim())
        try {
            Files.setPosixFilePermissions(f.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        } catch (e: UnsupportedOperationException) {
            f.setReadable(false, false); f.setReadable(true, true)
        } catch (e: Exception) {
        }
    }

    /** Looks a token like a GitHub one (ghp_, github_pat_, gho_ or a classic 40-hex)? */
    fun plausible(t: String): Boolean = t.trim().matches(Regex("(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|[0-9a-f]{40})"))
}
