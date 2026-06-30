package paper.plugin.minecraftX

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import paper.plugin.minecraftX.module.ModuleLoader
import paper.plugin.minecraftX.module.ConfigManager
import java.io.File

class MinecraftX : JavaPlugin() {

    lateinit var luaEngine: LuaEngine
        private set

    lateinit var scriptDir: File
        private set

    lateinit var moduleLoader: ModuleLoader
        private set

    override fun onEnable() {
        val serverRoot = dataFolder.parentFile.parentFile
        val minecraftXDir = File(serverRoot, "MinecraftX")
        val subDirs = listOf("script", "module", "config")

        val existing = subDirs.map { File(minecraftXDir, it) }.filter { it.exists() }
        val missing = subDirs.map { File(minecraftXDir, it) }.filter { !it.exists() }

        when {
            missing.isEmpty() -> {}
            existing.isEmpty() -> {
                minecraftXDir.mkdirs()
                missing.forEach { it.mkdirs() }
                logger.info("MinecraftX初始化完成")
            }
            else -> {
                missing.forEach { it.mkdirs() }
                logger.info("MinecraftX已修复受损文件夹")
            }
        }

        scriptDir = File(minecraftXDir, "script")
        val scriptConfigManager = ScriptConfigManager(this)
        luaEngine = LuaEngine(this, scriptConfigManager)
        luaEngine.init()

        val moduleDir = File(minecraftXDir, "module")
        val moduleConfigManager = ConfigManager(this)
        moduleLoader = ModuleLoader(this, moduleDir)
        moduleLoader.loadModules(moduleConfigManager)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) return false

        return when (command.name.lowercase()) {
            "script" -> handleScriptCommand(sender, args)
            "modlist" -> handleModlistCommand(sender, args)
            "modmanager" -> handleModmanagerCommand(sender, args)
            else -> false
        }
    }

    private fun handleScriptCommand(sender: CommandSender, args: Array<String>): Boolean {
        when (args[0].lowercase()) {
            "run" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c用法: /script run <文件名>")
                    return true
                }
                val scriptFile = File(scriptDir, args[1])
                if (!scriptFile.exists() || !scriptFile.isFile) {
                    sender.sendMessage("§c脚本 ${args[1]} 不存在")
                    return true
                }
                try {
                    luaEngine.runScript(scriptFile)
                    sender.sendMessage("§a脚本 ${args[1]} 已执行")
                } catch (e: Exception) {
                    sender.sendMessage("§c脚本执行错误: ${e.message}")
                }
                return true
            }
            "list" -> {
                val files = scriptDir.listFiles()
                    ?.filter { it.isFile && it.extension == "lua" }
                    ?.sortedBy { it.name }
                if (files.isNullOrEmpty()) {
                    sender.sendMessage("§e暂无脚本文件")
                } else {
                    sender.sendMessage("§6=== MinecraftX 脚本列表 ===")
                    files.forEach { sender.sendMessage("§7- §f${it.name}") }
                }
                return true
            }
        }
        return false
    }

    private fun handleModlistCommand(sender: CommandSender, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            moduleLoader.getModuleList().forEach { sender.sendMessage(it) }
            return true
        }

        when (args[0].lowercase()) {
            "filter" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c用法: /modlist filter <status|author> [args...]")
                    return true
                }
                when (args[1].lowercase()) {
                    "status" -> {
                        if (args.size < 3) {
                            sender.sendMessage("§c用法: /modlist filter status <enabled|disable>")
                            return true
                        }
                        val enabled = when (args[2].lowercase()) {
                            "enabled" -> true
                            "disable" -> false
                            else -> {
                                sender.sendMessage("§c用法: /modlist filter status <enabled|disable>")
                                return true
                            }
                        }
                        moduleLoader.getModuleListFilterStatus(enabled).forEach { sender.sendMessage(it) }
                    }
                    "author" -> {
                        if (args.size < 3) {
                            sender.sendMessage("§c用法: /modlist filter author <开发者名>")
                            return true
                        }
                        moduleLoader.getModuleListFilterAuthor(args[2]).forEach { sender.sendMessage(it) }
                    }
                    else -> sender.sendMessage("§c用法: /modlist filter <status|author> [args...]")
                }
                return true
            }
            "info" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c用法: /modlist info <模块名|包名>")
                    return true
                }
                val info = moduleLoader.getModuleInfo(args[1])
                if (info == null) {
                    sender.sendMessage("§c未找到模块: ${args[1]}")
                } else {
                    info.forEach { sender.sendMessage(it) }
                }
                return true
            }
            else -> {
                moduleLoader.getModuleList().forEach { sender.sendMessage(it) }
                return true
            }
        }
    }

    private fun handleModmanagerCommand(sender: CommandSender, args: Array<String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("§c用法: /modmanager <disabled|enabled> <模块名|包名>")
            return true
        }

        when (args[0].lowercase()) {
            "disabled" -> {
                if (moduleLoader.disableModule(args[1])) {
                    sender.sendMessage("§a模块 ${args[1]} 已禁用")
                } else {
                    sender.sendMessage("§c未找到模块: ${args[1]}")
                }
            }
            "enabled" -> {
                if (moduleLoader.enableModule(args[1])) {
                    sender.sendMessage("§a模块 ${args[1]} 已启用")
                } else {
                    sender.sendMessage("§c未找到模块: ${args[1]}")
                }
            }
            else -> sender.sendMessage("§c用法: /modmanager <disabled|enabled> <模块名|包名>")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): MutableList<String> {
        return when (command.name.lowercase()) {
            "script" -> tabCompleteScript(args)
            "modlist" -> tabCompleteModlist(args)
            "modmanager" -> tabCompleteModmanager(args)
            else -> mutableListOf()
        }
    }

    private fun tabCompleteScript(args: Array<String>): MutableList<String> {
        return when (args.size) {
            1 -> mutableListOf("run", "list")
            2 -> {
                if (args[0].lowercase() == "run") {
                    scriptDir.listFiles()
                        ?.filter { it.isFile && it.extension == "lua" }
                        ?.map { it.name }
                        ?.toMutableList() ?: mutableListOf()
                } else mutableListOf()
            }
            else -> mutableListOf()
        }
    }

    private fun tabCompleteModlist(args: Array<String>): MutableList<String> {
        return when (args.size) {
            1 -> mutableListOf("filter", "info")
            2 -> {
                when (args[0].lowercase()) {
                    "filter" -> mutableListOf("status", "author")
                    "info" -> getModuleSuggestions()
                    else -> mutableListOf()
                }
            }
            3 -> {
                when {
                    args[0].lowercase() == "filter" && args[1].lowercase() == "status" ->
                        mutableListOf("enabled", "disable")
                    args[0].lowercase() == "filter" && args[1].lowercase() == "author" ->
                        getAuthorSuggestions(args[2])
                    else -> mutableListOf()
                }
            }
            else -> mutableListOf()
        }
    }

    private fun tabCompleteModmanager(args: Array<String>): MutableList<String> {
        return when (args.size) {
            1 -> mutableListOf("disabled", "enabled")
            2 -> getModuleSuggestions()
            else -> mutableListOf()
        }
    }

    private fun getModuleSuggestions(): MutableList<String> {
        return moduleLoader.getModuleNames().toMutableList()
    }

    private fun getAuthorSuggestions(prefix: String): MutableList<String> {
        return moduleLoader.getModuleAuthors()
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .toMutableList()
    }

    override fun onDisable() {
        moduleLoader.shutdown()
    }
}
