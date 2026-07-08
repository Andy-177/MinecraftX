package paper.plugin.minecraftX.module

import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException

class ConfigManager(private val plugin: JavaPlugin) {

    private val configRoot: File

    init {
        val serverRoot = plugin.dataFolder.parentFile.parentFile
        configRoot = File(File(serverRoot, "MinecraftX"), "config")
    }

    fun getConfigDir(moduleName: String): File {
        return File(configRoot, moduleName)
    }

    fun getConfigFile(moduleName: String): File {
        return File(File(configRoot, moduleName), "config.yml")
    }

    fun copyResources(module: ModuleInfo) {
        val resourcesDir = module.resourcesDir
        val files = resourcesDir.listFiles()
        if (files.isNullOrEmpty()) return
        val targetDir = getConfigDir(module.manifest.name)
        targetDir.mkdirs()
        copyDirectory(resourcesDir, targetDir)
    }

    private fun copyDirectory(source: File, target: File) {
        if (!source.exists()) return
        val files = source.listFiles() ?: return
        for (file in files) {
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                targetFile.mkdirs()
                copyDirectory(file, targetFile)
            } else if (!targetFile.exists()) {
                try {
                    file.copyTo(targetFile)
                } catch (_: IOException) {}
            }
        }
    }

    fun loadConfig(moduleName: String): YamlConfiguration {
        val dir = getConfigDir(moduleName)
        dir.mkdirs()
        val file = getConfigFile(moduleName)
        return if (file.exists()) {
            try {
                YamlConfiguration.loadConfiguration(file)
            } catch (e: InvalidConfigurationException) {
                YamlConfiguration()
            }
        } else {
            YamlConfiguration()
        }
    }

    fun saveConfig(moduleName: String, config: YamlConfiguration) {
        val dir = getConfigDir(moduleName)
        dir.mkdirs()
        try {
            config.save(getConfigFile(moduleName))
        } catch (_: IOException) {}
    }

    fun reloadConfig(moduleName: String): YamlConfiguration {
        return loadConfig(moduleName)
    }

    fun deleteConfig(moduleName: String): Boolean {
        val file = getConfigFile(moduleName)
        return file.delete()
    }
}
