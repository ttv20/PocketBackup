package com.ttv20.rsyncbackup.backup

import android.content.Context
import java.io.File

data class NativeInstallResult(
    val paths: BinaryPaths,
    val toolPaths: Map<String, String>,
    val missing: List<String>,
) {
    val isComplete: Boolean get() = missing.isEmpty()

    fun requireTool(name: String): String =
        requireNotNull(toolPaths[name]) { "Missing $name binary" }
}

class NativeBinaryManager(private val context: Context) {
    fun ensureInstalled(): NativeInstallResult {
        val binDir = File(context.filesDir, "bin").also { it.mkdirs() }
        val installVersion = currentApkInstallVersion()
        val marker = File(binDir, ".installed-apk-version")
        val installedVersion = if (marker.exists()) marker.readText() else null
        if (!looksInstalled(binDir) || installedVersion != installVersion) {
            binDir.deleteRecursively()
            binDir.mkdirs()
            copyAssetTree("native/arm64-v8a", binDir)
            marker.writeText(installVersion)
        }

        val rsync = executablePath("rsync", "librsync_exec.so", binDir)
        val ssh = executablePath("ssh", "libssh_exec.so", binDir)
        val sshKeygen = executablePath("ssh-keygen", "libssh_keygen_exec.so", binDir)
        val sshKeyscan = executablePath("ssh-keyscan", "libssh_keyscan_exec.so", binDir)
        val scp = executablePath("scp", "libscp_exec.so", binDir)
        val sftp = executablePath("sftp", "libsftp_exec.so", binDir)
        val tsnet = executablePath("tsnet-nc", "libtsnet_nc_exec.so", binDir)
        val missing = buildList {
            if (rsync == null) add("rsync")
            if (ssh == null) add("ssh")
            if (sshKeygen == null) add("ssh-keygen")
            if (sshKeyscan == null) add("ssh-keyscan")
            if (scp == null) add("scp")
            if (sftp == null) add("sftp")
            if (tsnet == null) add("tsnet-nc")
        }

        return NativeInstallResult(
            paths = BinaryPaths(
                rsync = rsync ?: File(binDir, "rsync").absolutePath,
                ssh = ssh ?: File(binDir, "ssh").absolutePath,
                tsnetHelper = tsnet ?: File(binDir, "tsnet-nc").absolutePath,
            ),
            toolPaths = mapOf(
                "rsync" to rsync,
                "ssh" to ssh,
                "ssh-keygen" to sshKeygen,
                "ssh-keyscan" to sshKeyscan,
                "scp" to scp,
                "sftp" to sftp,
                "tsnet-nc" to tsnet,
            ).mapNotNull { (name, path) -> path?.let { name to it } }.toMap(),
            missing = missing,
        )
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val entries = context.assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            runCatching {
                context.assets.open(assetPath).use { input ->
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return
        }

        destination.mkdirs()
        entries.forEach { entry ->
            copyAssetTree("$assetPath/$entry", File(destination, entry))
        }
    }

    private fun executablePath(name: String, nativeLibraryName: String, binDir: File): String? {
        val nativeExecutable = nativeExecutableCandidates(nativeLibraryName)
            .firstOrNull { it.exists() }
        if (nativeExecutable != null) {
            return nativeExecutable.absolutePath
        }
        val assetCopy = File(binDir, name)
        if (!assetCopy.exists()) return null
        assetCopy.setExecutable(true, true)
        binDir.listFiles()
            ?.filter { it.isFile && !it.name.contains('.') }
            ?.forEach { it.setExecutable(true, true) }
        return assetCopy.absolutePath
    }

    private fun nativeExecutableCandidates(nativeLibraryName: String): List<File> {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        return listOf(
            File(nativeDir, nativeLibraryName),
            File(nativeDir, "arm64/$nativeLibraryName"),
            File(nativeDir, "arm64-v8a/$nativeLibraryName"),
        )
    }

    private fun looksInstalled(binDir: File): Boolean =
        listOf(
            File(binDir, "rsync"),
            File(binDir, "ssh"),
            File(binDir, "scp"),
            File(binDir, "sftp"),
            File(binDir, "tsnet-nc"),
            File(binDir, "lib/libcrypto.so.3"),
            File(binDir, "lib/libz.so.1"),
            File(binDir, "lib/libzstd.so.1"),
        ).all { it.exists() }

    private fun currentApkInstallVersion(): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${packageInfo.versionCode}:${packageInfo.lastUpdateTime}"
    }

    companion object {
        fun configureProcessEnvironment(processBuilder: ProcessBuilder, filesDir: File) {
            val binDir = File(filesDir, "bin")
            val libDir = File(binDir, "lib")
            val nativeLibDir = filesDir.parentFile?.resolve("lib")
            val homeDir = File(filesDir, "home").also { it.mkdirs() }
            val env = processBuilder.environment()
            val existingPath = env["PATH"].orEmpty()
            val existingLibraryPath = env["LD_LIBRARY_PATH"].orEmpty()
            env["PATH"] = listOfNotNull(nativeLibDir?.absolutePath, binDir.absolutePath, existingPath)
                .filter { it.isNotBlank() }
                .joinToString(":")
            env["LD_LIBRARY_PATH"] = listOfNotNull(libDir.absolutePath, nativeLibDir?.absolutePath, existingLibraryPath)
                .filter { it.isNotBlank() }
                .joinToString(":")
            env["HOME"] = homeDir.absolutePath
            env["TERM"] = "dumb"
            File(filesDir, "tmp").also {
                it.mkdirs()
                env["TMPDIR"] = it.absolutePath
            }
        }
    }
}
