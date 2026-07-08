package paper.plugin.minecraftX.module

import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ModulePackager(
    private val plugin: JavaPlugin,
    private val moduleDir: File,
    private val packagesDir: File
) {
    private val yaml = Yaml()

    fun install(zipNames: List<String>): List<String> {
        val messages = mutableListOf<String>()
        for (zipName in zipNames) {
            val zipFile = File(packagesDir, zipName)
            if (!zipFile.exists()) {
                messages.add("§c${zipName} 不存在")
                continue
            }
            if (zipFile.isDirectory || !zipName.endsWith(".zip", ignoreCase = true)) {
                messages.add("§c${zipName} 不是有效的 ZIP 文件")
                continue
            }
            if (!validateModulePack(zipFile)) {
                messages.add("§c${zipName} 模块包格式错误")
                continue
            }
            try {
                val extracted = extractModulePack(zipFile)
                messages.add("§a已安装 ${extracted.size} 个模块: ${extracted.joinToString(", ")}，重启后生效")
            } catch (e: Exception) {
                messages.add("§c安装 ${zipName} 失败: ${e.message}")
            }
        }
        return messages
    }

    fun remove(moduleNames: List<String>): List<String> {
        val messages = mutableListOf<String>()
        for (name in moduleNames) {
            val moduleDir = findModuleDir(name)
            if (moduleDir == null) {
                messages.add("§c未找到模块: $name")
                continue
            }
            val pkg = getModulePackageName(moduleDir) ?: moduleDir.name
            deleteDirectory(moduleDir)
            messages.add("§a模块 $pkg 已移除，重启后生效")
        }
        return messages
    }

    fun uninstall(moduleNames: List<String>): List<String> {
        val messages = mutableListOf<String>()
        for (name in moduleNames) {
            val moduleDir = findModuleDir(name)
            if (moduleDir == null) {
                messages.add("§c未找到模块: $name")
                continue
            }
            val manifest = parseManifestFile(File(moduleDir, "manifest.yml"))
                val pkg = if (manifest != null) "${manifest.author}@${manifest.name}:${manifest.version}" else moduleDir.name
            val safeName = pkg.replace(":", "-")
            val packageFileName = "$safeName.zip"
            val targetZip = File(packagesDir, packageFileName)
            packagesDir.mkdirs()
            zipDirectory(moduleDir, targetZip)
            deleteDirectory(moduleDir)
            messages.add("§a模块 $pkg 已卸载，备份包: ${packageFileName}，重启后生效")
        }
        return messages
    }

    fun archive(zipName: String, moduleNames: List<String>): List<String> {
        val messages = mutableListOf<String>()
        val dirs = mutableListOf<Pair<File, String>>()
        for (name in moduleNames) {
            val dir = findModuleDir(name)
            if (dir == null) {
                messages.add("§c未找到模块: $name")
            } else {
                val manifest = parseManifestFile(File(dir, "manifest.yml"))
                val pkg = if (manifest != null) "${manifest.author}@${manifest.name}:${manifest.version}" else dir.name
                dirs.add(dir to pkg)
            }
        }
        if (dirs.isEmpty()) {
            messages.add("§c没有有效模块可打包")
            return messages
        }
        packagesDir.mkdirs()
        val targetZip = File(packagesDir, "$zipName.zip")
        ZipOutputStream(FileOutputStream(targetZip)).use { zos ->
            for ((dir, _) in dirs) {
                addDirectoryToZip(dir, zos, dir.parentFile.absolutePath)
            }
        }
        for ((dir, pkg) in dirs) {
            deleteDirectory(dir)
        }
        messages.add("§a已打包 ${dirs.size} 个模块到 ${zipName}.zip 并已卸载，重启后生效")
        return messages
    }

    fun pack(moduleNames: List<String>): List<String> {
        val messages = mutableListOf<String>()
        for (name in moduleNames) {
            val dir = findModuleDir(name)
            if (dir == null) {
                messages.add("§c未找到模块: $name")
                continue
            }
            val manifest = parseManifestFile(File(dir, "manifest.yml"))
            val pkg = if (manifest != null) "${manifest.author}@${manifest.name}:${manifest.version}" else dir.name
            val safeName = pkg.replace(":", "-")
            val packageFileName = "$safeName.zip"
            val targetZip = File(packagesDir, packageFileName)
            packagesDir.mkdirs()
            zipDirectory(dir, targetZip)
            messages.add("§a模块 $pkg 已打包到 ${packageFileName}")
        }
        return messages
    }

    fun packs(zipName: String, moduleNames: List<String>): List<String> {
        val messages = mutableListOf<String>()
        val dirs = mutableListOf<File>()
        for (name in moduleNames) {
            val dir = findModuleDir(name)
            if (dir == null) {
                messages.add("§c未找到模块: $name")
            } else {
                dirs.add(dir)
            }
        }
        if (dirs.isEmpty()) {
            messages.add("§c没有有效模块可打包")
            return messages
        }
        packagesDir.mkdirs()
        val targetZip = File(packagesDir, "$zipName.zip")
        ZipOutputStream(FileOutputStream(targetZip)).use { zos ->
            for (dir in dirs) {
                addDirectoryToZip(dir, zos, dir.parentFile.absolutePath)
            }
        }
        messages.add("§a已打包 ${dirs.size} 个模块到 ${zipName}.zip")
        return messages
    }

    private fun findModuleDir(nameOrPkg: String): File? {
        if (!moduleDir.exists()) return null
        val subDirs = moduleDir.listFiles { f -> f.isDirectory } ?: return null

        for (dir in subDirs) {
            if (dir.name == nameOrPkg) return dir
            val manifestFile = File(dir, "manifest.yml")
            if (!manifestFile.exists()) continue
            val manifest = parseManifestFile(manifestFile) ?: continue
            if (manifest.name == nameOrPkg) return dir
            val pkg = "${manifest.author}@${manifest.name}:${manifest.version}"
            if (pkg == nameOrPkg) return dir
        }
        return null
    }

    private fun getModulePackageName(moduleDir: File): String? {
        val manifestFile = File(moduleDir, "manifest.yml")
        if (!manifestFile.exists()) return null
        val manifest = parseManifestFile(manifestFile) ?: return null
        return "${manifest.author}@${manifest.name}:${manifest.version}"
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseManifestFile(file: File): Manifest? {
        return try {
            FileInputStream(file).use { input ->
                val raw = yaml.load<Map<String, Any>>(input) ?: return null
                val name = raw["name"] as? String ?: return null
                val version = raw["version"] as? String ?: return null
                val main = raw["main"] as? String ?: return null
                val author = raw["author"] as? String ?: return null
                val description = raw["description"] as? String
                val expose = raw["expose"] as? Boolean ?: false
                Manifest(name, version, main, description, author, null, null, null, expose)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun validateModulePack(zipFile: File): Boolean {
        try {
            val entries = mutableListOf<String>()
            ZipFile(zipFile).use { zip ->
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    entries.add(enumeration.nextElement().name)
                }
            }
            if (entries.isEmpty()) return false
            val topDirs = entries.map { it.substringBefore("/") }
                .filter { it.isNotEmpty() }
                .distinct()
            if (topDirs.isEmpty()) return false
            for (dir in topDirs) {
                val manifestEntry = "$dir/manifest.yml"
                if (manifestEntry !in entries) return false
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun extractModulePack(zipFile: File): List<String> {
        val extracted = mutableSetOf<String>()
        ZipFile(zipFile).use { zip ->
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                val name = entry.name
                if (name.isEmpty()) continue
                val topDir = name.substringBefore("/")
                if (topDir.isNotEmpty()) extracted.add(topDir)
                val targetFile = File(moduleDir, name)
                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        return extracted.toList()
    }

    private fun zipDirectory(dir: File, targetFile: File) {
        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            addDirectoryToZip(dir, zos, dir.parentFile.absolutePath)
        }
    }

    private fun addDirectoryToZip(dir: File, zos: ZipOutputStream, basePath: String) {
        val files = dir.listFiles() ?: return
        for (file in files.sortedBy { it.name }) {
            val entryName = file.absolutePath.substring(basePath.length + 1)
                .replace("\\", "/")
            if (file.isDirectory) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
                addDirectoryToZip(file, zos, basePath)
            } else {
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    private fun deleteDirectory(dir: File) {
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) deleteDirectory(file)
                else file.delete()
            }
        }
        dir.delete()
    }
}
