package paper.plugin.minecraftX

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import paper.plugin.minecraftX.module.ModuleLoader
import paper.plugin.minecraftX.module.ConfigManager
import paper.plugin.minecraftX.module.ModulePackager
import java.io.File

class MinecraftX : JavaPlugin() {

    lateinit var luaEngine: LuaEngine
        private set

    lateinit var scriptDir: File
        private set

    lateinit var moduleLoader: ModuleLoader
        private set

    lateinit var modulePackager: ModulePackager
        private set

    override fun onEnable() {
        val serverRoot = dataFolder.parentFile.parentFile
        val minecraftXDir = File(serverRoot, "MinecraftX")
        val subDirs = listOf("script", "module", "config", "packages")

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
        val packagesDir = File(minecraftXDir, "packages")
        val moduleConfigManager = ConfigManager(this)
        moduleLoader = ModuleLoader(this, moduleDir, moduleConfigManager)
        moduleLoader.loadModules()
        modulePackager = ModulePackager(this, moduleDir, packagesDir)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) return false

        return when (command.name.lowercase()) {
            "script" -> handleScriptCommand(sender, args)
            "mpm" -> handleMpmCommand(sender, args)
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

    private fun handleMpmCommand(sender: CommandSender, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§c用法: /mpm <install|remove|uninstall|archive|pack|packs|list|enabled|disabled> [args...]")
            return true
        }

        when (args[0].lowercase()) {
            "install" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c用法: /mpm install <zip名称> [zip名称2...]")
                    return true
                }
                val responses = modulePackager.install(args.drop(1))
                responses.forEach { sender.sendMessage(it) }
            }
            "remove" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c用法: /mpm remove <模块名|包名> [模块名2...]")
                    return true
                }
                val responses = modulePackager.remove(args.drop(1))
                responses.forEach { sender.sendMessage(it) }
            }
            "uninstall" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c用法: /mpm uninstall <模块名|包名> [模块名2...]")
                    return true
                }
                val responses = modulePackager.uninstall(args.drop(1))
                responses.forEach { sender.sendMessage(it) }
            }
            "archive" -> {
                if (args.size < 3) {
                    sender.sendMessage("§c用法: /mpm archive <zip名称> <模块名|包名> [模块名2...]")
                    return true
                }
                val zipName = args[1]
                val moduleNames = args.drop(2)
                val responses = modulePackager.archive(zipName, moduleNames)
                responses.forEach { sender.sendMessage(it) }
            }
            "pack" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c用法: /mpm pack <模块名|包名> [模块名2...]")
                    return true
                }
                val responses = modulePackager.pack(args.drop(1))
                responses.forEach { sender.sendMessage(it) }
            }
            "packs" -> {
                if (args.size < 3) {
                    sender.sendMessage("§c用法: /mpm packs <zip名称> <模块名|包名> [模块名2...]")
                    return true
                }
                val zipName = args[1]
                val moduleNames = args.drop(2)
                val responses = modulePackager.packs(zipName, moduleNames)
                responses.forEach { sender.sendMessage(it) }
            }
            "list" -> {
                if (args.size == 1) {
                    moduleLoader.getModuleList().forEach { sender.sendMessage(it) }
                    return true
                }
                when (args[1].lowercase()) {
                    "filter" -> {
                        if (args.size < 3) {
                            sender.sendMessage("§c用法: /mpm list filter <status|author> [args...]")
                            return true
                        }
                        when (args[2].lowercase()) {
                            "status" -> {
                                if (args.size < 4) {
                                    sender.sendMessage("§c用法: /mpm list filter status <enabled|disable>")
                                    return true
                                }
                                val enabled = when (args[3].lowercase()) {
                                    "enabled" -> true
                                    "disable" -> false
                                    else -> {
                                        sender.sendMessage("§c用法: /mpm list filter status <enabled|disable>")
                                        return true
                                    }
                                }
                                moduleLoader.getModuleListFilterStatus(enabled).forEach { sender.sendMessage(it) }
                            }
                            "author" -> {
                                if (args.size < 4) {
                                    sender.sendMessage("§c用法: /mpm list filter author <开发者名>")
                                    return true
                                }
                                moduleLoader.getModuleListFilterAuthor(args[3]).forEach { sender.sendMessage(it) }
                            }
                            else -> sender.sendMessage("§c用法: /mpm list filter <status|author> [args...]")
                        }
                    }
                    "info" -> {
                        if (args.size < 3) {
                            sender.sendMessage("§c用法: /mpm list info <模块名|包名>")
                            return true
                        }
                        val info = moduleLoader.getModuleInfo(args[2])
                        if (info == null) {
                            sender.sendMessage("§c未找到模块: ${args[2]}")
                        } else {
                            info.forEach { sender.sendMessage(it) }
                        }
                    }
                    else -> {
                        moduleLoader.getModuleList().forEach { sender.sendMessage(it) }
                    }
                }
                return true
            }
            "disabled" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c用法: /mpm disabled <模块名|包名>")
                    return true
                }
                if (moduleLoader.disableModule(args[1])) {
                    sender.sendMessage("§a模块 ${args[1]} 已禁用，重启后生效")
                } else {
                    sender.sendMessage("§c未找到模块: ${args[1]}")
                }
            }
            "enabled" -> {
                if (args.size < 2) {
                    sender.sendMessage("§c用法: /mpm enabled <模块名|包名>")
                    return true
                }
                if (moduleLoader.enableModule(args[1])) {
                    sender.sendMessage("§a模块 ${args[1]} 已启用，重启后生效")
                } else {
                    sender.sendMessage("§c未找到模块: ${args[1]}")
                }
            }
            else -> sender.sendMessage("§c用法: /mpm <install|remove|uninstall|archive|pack|packs|list|enabled|disabled> [args...]")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): MutableList<String> {
        return when (command.name.lowercase()) {
            "script" -> tabCompleteScript(args)
            "mpm" -> tabCompleteMpm(args)
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

    private fun tabCompleteMpm(args: Array<String>): MutableList<String> {
        val subcommands = listOf("install", "remove", "uninstall", "archive", "pack", "packs", "list", "disabled", "enabled")
        return when (args.size) {
            1 -> subcommands.toMutableList()
            2 -> {
                when (args[0].lowercase()) {
                    "install" -> mutableListOf()
                    "list" -> mutableListOf("filter", "info")
                    "remove", "uninstall", "pack", "disabled", "enabled" -> getModuleSuggestions()
                    "archive", "packs" -> mutableListOf()
                    else -> mutableListOf()
                }
            }
            3 -> {
                when (args[0].lowercase()) {
                    "list" -> {
                        when (args[1].lowercase()) {
                            "filter" -> mutableListOf("status", "author")
                            "info" -> getModuleSuggestions()
                            else -> mutableListOf()
                        }
                    }
                    "remove", "uninstall", "pack", "disabled", "enabled" -> getModuleSuggestions()
                    "archive", "packs" -> getModuleSuggestions()
                    else -> mutableListOf()
                }
            }
            4 -> {
                when (args[0].lowercase()) {
                    "list" -> {
                        when {
                            args[1].lowercase() == "filter" && args[2].lowercase() == "status" ->
                                mutableListOf("enabled", "disable")
                            args[1].lowercase() == "filter" && args[2].lowercase() == "author" ->
                                getAuthorSuggestions(args[3])
                            else -> mutableListOf()
                        }
                    }
                    else -> mutableListOf()
                }
            }
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
