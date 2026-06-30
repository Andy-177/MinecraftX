package paper.plugin.minecraftX

import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException

class ScriptConfigManager(private val plugin: JavaPlugin) {

    private val configRoot: File

    init {
        val serverRoot = plugin.dataFolder.parentFile.parentFile
        configRoot = File(File(serverRoot, "MinecraftX"), "script")
    }

    fun getConfigDir(scriptName: String): File {
        return File(configRoot, scriptName)
    }

    fun getConfigFile(scriptName: String): File {
        return File(File(configRoot, scriptName), "config.yml")
    }

    fun initConfig(scriptName: String): File {
        val dir = getConfigDir(scriptName)
        dir.mkdirs()
        val file = getConfigFile(scriptName)
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (_: IOException) {}
        }
        return file
    }

    fun initConfig(scriptName: String, defaults: Map<String, Any>): YamlConfiguration {
        val file = getConfigFile(scriptName)
        val config: YamlConfiguration

        if (file.exists()) {
            config = try {
                YamlConfiguration.loadConfiguration(file)
            } catch (e: InvalidConfigurationException) {
                YamlConfiguration()
            }
        } else {
            config = YamlConfiguration()
            getConfigDir(scriptName).mkdirs()
        }

        applyDefaults(config, defaults)
        saveConfig(scriptName, config)
        return config
    }

    fun loadConfig(scriptName: String): YamlConfiguration {
        initConfig(scriptName)
        return try {
            YamlConfiguration.loadConfiguration(getConfigFile(scriptName))
        } catch (e: InvalidConfigurationException) {
            YamlConfiguration()
        }
    }

    fun saveConfig(scriptName: String, config: YamlConfiguration) {
        try {
            config.save(getConfigFile(scriptName))
        } catch (_: IOException) {}
    }

    fun reloadConfig(scriptName: String): YamlConfiguration {
        return loadConfig(scriptName)
    }

    fun deleteConfig(scriptName: String): Boolean {
        return getConfigFile(scriptName).delete()
    }

    private fun applyDefaults(config: YamlConfiguration, defaults: Map<String, Any>) {
        for ((key, value) in defaults) {
            if (config.contains(key)) continue
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    applyDefaults(config, key, value as Map<String, Any>)
                }
                else -> config.set(key, value)
            }
        }
    }

    private fun applyDefaults(config: YamlConfiguration, path: String, defaults: Map<String, Any>) {
        for ((key, value) in defaults) {
            val fullPath = "$path.$key"
            if (config.contains(fullPath)) continue
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    applyDefaults(config, fullPath, value as Map<String, Any>)
                }
                else -> config.set(fullPath, value)
            }
        }
    }
}
