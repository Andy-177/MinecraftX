package paper.plugin.minecraftX.module

import org.bukkit.plugin.java.JavaPlugin
import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.*
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.logging.Level

class ModuleLoader(private val plugin: JavaPlugin, private val moduleDir: File) {

    private val registry = mutableMapOf<String, ModuleInfo>()
    private val nameIndex = mutableMapOf<String, MutableList<ModuleInfo>>()
    private val providesMap = mutableMapOf<String, ModuleInfo>()
    private val disabledFile = File(moduleDir, "disabled.txt")
    private val yaml = Yaml()

    fun loadModules(
        configManager: ConfigManager
    ): List<ModuleInfo> {
        val scanned = scanModules()
        if (scanned.isEmpty()) return emptyList()

        val disabled = loadDisabledList()

        for (module in scanned) {
            if (module.packageName in disabled) {
                module.enabled = false
            }
        }

        checkDuplicates(scanned)

        if (hasFatalError) return emptyList()

        val duplicateRemediation = resolveDuplicates(scanned)
        val validated = duplicateRemediation.filter { it.enabled }

        val cycle = detectCircularDependency(validated)
        if (cycle != null) {
            plugin.logger.severe("检测到依赖循环，服务器拒绝启动")
            plugin.logger.severe("依赖循环链：")
            plugin.logger.severe(cycle.joinToString(" -> "))
            plugin.server.shutdown()
            return emptyList()
        }

        val resolvedProviders = mutableMapOf<String, ModuleInfo>()
        for (module in validated) {
            module.manifest.provides?.forEach { pkg ->
                resolvedProviders[pkg] = module
            }
        }

        val result = mutableListOf<ModuleInfo>()
        for (module in validated) {
            if (!validateModule(module, validated, resolvedProviders)) {
                module.enabled = false
                continue
            }
            result.add(module)
        }

        val finalModules = result.filter { it.enabled }

        for (module in finalModules) {
            try {
                loadModuleScript(module, configManager)
            } catch (e: Exception) {
                plugin.logger.warning("模块 ${module.packageName} 加载失败: ${e.message}")
                module.enabled = false
            }
        }

        val loaded = finalModules.filter { it.loaded }

        for (module in loaded) {
            try {
                callOnEnable(module)
            } catch (e: Exception) {
                plugin.logger.warning("模块 ${module.packageName} onEnable 执行失败: ${e.message}")
            }
        }

        return loaded
    }

    fun shutdown() {
        for (module in registry.values) {
            if (module.loaded) {
                try {
                    callOnDisable(module)
                } catch (e: Exception) {
                    plugin.logger.warning("模块 ${module.packageName} onDisable 执行失败: ${e.message}")
                }
            }
        }
    }

    fun getModuleList(): List<String> {
        val lines = mutableListOf<String>()
        lines.add("§6=== MinecraftX 模块列表 ===")
        val sorted = registry.values.toList().sortedBy { it.manifest.name.lowercase() }
        for (module in sorted) {
            val isDup = (nameIndex[module.manifest.name]?.size ?: 0) > 1
            val display = if (isDup) module.packageName else module.manifest.name
            if (module.enabled && module.loaded) {
                lines.add("§a$display (§aEnabled§a)")
            } else {
                lines.add("§c$display (§cDisabled§c)")
            }
        }
        return lines
    }

    fun getModuleListFilterStatus(enabled: Boolean): List<String> {
        val label = if (enabled) "启用" else "禁用"
        val lines = mutableListOf<String>()
        lines.add("§6=== $label 模块列表 ===")
        val sorted = registry.values.toList()
            .filter { (it.enabled && it.loaded) == enabled }
            .sortedBy { it.manifest.name.lowercase() }
        for (module in sorted) {
            val isDup = (nameIndex[module.manifest.name]?.size ?: 0) > 1
            val display = if (isDup) module.packageName else module.manifest.name
            if (module.enabled && module.loaded) {
                lines.add("§a$display (§aEnabled§a)")
            } else {
                lines.add("§c$display (§cDisabled§c)")
            }
        }
        if (lines.size == 1) {
            lines.add("§e暂无${label}的模块")
        }
        return lines
    }

    fun getModuleListFilterAuthor(author: String): List<String> {
        val lines = mutableListOf<String>()
        lines.add("§6=== $author 的模块列表 ===")
        val sorted = registry.values.toList()
            .filter { it.manifest.author.equals(author, ignoreCase = true) }
            .sortedBy { it.manifest.name.lowercase() }
        for (module in sorted) {
            val isDup = (nameIndex[module.manifest.name]?.size ?: 0) > 1
            val display = if (isDup) module.packageName else module.manifest.name
            if (module.enabled && module.loaded) {
                lines.add("§a$display (§aEnabled§a)")
            } else {
                lines.add("§c$display (§cDisabled§c)")
            }
        }
        return lines
    }

    fun getModuleInfo(nameOrPkg: String): List<String>? {
        val module = findModule(nameOrPkg) ?: return null
        val lines = mutableListOf<String>()
        lines.add(module.packageName)
        lines.add("  §7└─name:§f ${module.manifest.name}")
        lines.add("  §7└─version:§f ${module.manifest.version}")
        lines.add("  §7└─main:§f ${module.manifest.main}")
        val desc = module.manifest.description
        if (desc != null) {
            val descLines = desc.lines()
            if (descLines.size <= 1) {
                lines.add("  §7└─description:§f $desc")
            } else {
                lines.add("  §7└─description:§f ")
                for (dl in descLines) {
                    lines.add("      $dl")
                }
            }
        }
        lines.add("  §7└─author:§f ${module.manifest.author}")
        return lines
    }

    fun disableModule(nameOrPkg: String): Boolean {
        val module = findModule(nameOrPkg) ?: return false
        val disabled = loadDisabledList().toMutableSet()
        disabled.add(module.packageName)
        saveDisabledList(disabled)
        module.enabled = false
        plugin.logger.info("模块 ${module.packageName} 已禁用")
        return true
    }

    fun enableModule(nameOrPkg: String): Boolean {
        val module = findModule(nameOrPkg) ?: return false
        val disabled = loadDisabledList().toMutableSet()
        if (disabled.remove(module.packageName)) {
            saveDisabledList(disabled)
        }
        module.enabled = true
        plugin.logger.info("模块 ${module.packageName} 已启用")
        return true
    }

    private fun findModule(nameOrPkg: String): ModuleInfo? {
        if (nameOrPkg in registry) return registry[nameOrPkg]
        val byName = nameIndex[nameOrPkg]
        if (byName != null) {
            if (byName.size == 1) return byName[0]
            return null
        }
        return null
    }

    fun getModuleNames(): List<String> {
        val result = mutableSetOf<String>()
        for (module in registry.values) {
            result.add(module.manifest.name)
            result.add(module.packageName)
        }
        return result.toList().sorted()
    }

    fun getModuleAuthors(): List<String> {
        return registry.values.map { it.manifest.author }.distinct().sorted()
    }

    private var hasFatalError = false

    private fun scanModules(): List<ModuleInfo> {
        if (!moduleDir.exists()) return emptyList()
        val result = mutableListOf<ModuleInfo>()
        val subDirs = moduleDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return emptyList()
        for (dir in subDirs.sortedBy { it.name }) {
            val manifestFile = File(dir, "manifest.yml")
            if (!manifestFile.exists()) continue
            val manifest = parseManifest(manifestFile)
            if (manifest == null) {
                plugin.logger.warning("模块 ${dir.name} 的 manifest.yml 解析失败，已跳过")
                continue
            }
            val module = ModuleInfo(manifest, dir)
            result.add(module)
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseManifest(file: File): Manifest? {
        return try {
            FileInputStream(file).use { input ->
                val raw = yaml.load<Map<String, Any>>(input) ?: return null
                val name = raw["name"] as? String ?: return null
                val version = raw["version"] as? String ?: return null
                val main = raw["main"] as? String ?: return null
                val author = raw["author"] as? String ?: return null
                val description = raw["description"] as? String
                val expose = raw["expose"] as? Boolean ?: false

                val dependsRaw = raw["depends"] as? Map<String, Any>
                val depends = if (dependsRaw != null) {
                    DependsSpec(
                        required = (dependsRaw["required"] as? List<String>)?.map { it.trim() },
                        optional = (dependsRaw["optional"] as? List<String>)?.map { it.trim() }
                    )
                } else null

                val conflictsRaw = raw["conflicts"] as? Map<String, Any>
                val conflicts = if (conflictsRaw != null) {
                    ConflictsSpec(
                        `break` = (conflictsRaw["break"] as? List<String>)?.map { it.trim() },
                        incompatible = (conflictsRaw["incompatible"] as? List<String>)?.map { it.trim() }
                    )
                } else null

                val provides = (raw["provides"] as? List<String>)?.map { it.trim() }

                Manifest(name, version, main, description, author, depends, conflicts, provides, expose)
            }
        } catch (e: Exception) {
            plugin.logger.warning("解析 manifest.yml 失败: ${file.absolutePath} - ${e.message}")
            null
        }
    }

    private fun loadDisabledList(): Set<String> {
        if (!disabledFile.exists()) return emptySet()
        return try {
            disabledFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        } catch (e: IOException) {
            plugin.logger.warning("读取 disabled.txt 失败: ${e.message}")
            emptySet()
        }
    }

    private fun saveDisabledList(list: Set<String>) {
        try {
            disabledFile.writeText(list.sorted().joinToString("\n"))
        } catch (e: IOException) {
            plugin.logger.warning("写入 disabled.txt 失败: ${e.message}")
        }
    }

    private fun checkDuplicates(modules: List<ModuleInfo>) {
        for (module in modules) {
            registry[module.packageName] = module
            nameIndex.computeIfAbsent(module.manifest.name) { mutableListOf() }.add(module)
        }

        val dupMap = mutableMapOf<String, MutableList<ModuleInfo>>()
        for (module in modules) {
            val key = "${module.manifest.author}@${module.manifest.name}"
            dupMap.computeIfAbsent(key) { mutableListOf() }.add(module)
        }

        val fatalDuplicates = dupMap.values.filter { it.size > 1 }
        if (fatalDuplicates.isNotEmpty()) {
            hasFatalError = true
            plugin.logger.severe("同一插件不同版本同时存在，拒绝启动服务器，请删除其中一个版本，然后重启服务器")
            plugin.logger.severe("重名的插件：")
            for (dups in fatalDuplicates) {
                for (d in dups) {
                    plugin.logger.severe("  §7└─§c${d.packageName}")
                }
            }
            plugin.server.shutdown()
        }
    }

    private fun resolveDuplicates(modules: List<ModuleInfo>): List<ModuleInfo> {
        val byName = mutableMapOf<String, ModuleInfo>()
        val result = mutableListOf<ModuleInfo>()
        for (module in modules) {
            val existing = byName[module.manifest.name]
            if (existing != null) {
                if (existing.enabled) {
                    module.enabled = false
                }
            } else {
                byName[module.manifest.name] = module
            }
            result.add(module)
        }
        return result
    }

    private fun validateModule(
        module: ModuleInfo,
        allModules: List<ModuleInfo>,
        providers: Map<String, ModuleInfo>
    ): Boolean {
        val deps = module.manifest.depends ?: return true
        var valid = true

        val resolvePkg = fun(target: String): ModuleInfo? {
            val direct = allModules.find { it.packageName == target && it.enabled }
            if (direct != null) return direct
            val provided = providers[target]
            if (provided != null && provided.enabled) return provided
            return null
        }

        deps.required?.forEach { entry ->
            val ref = parseDependencyEntry(entry)
            val resolved = resolvePkg(ref.packageName)
            if (resolved == null) {
                valid = false
                plugin.logger.severe("${module.packageName}缺失依赖：")
                val descLine = if (ref.description != null) " : ${ref.description}" else ""
                plugin.logger.severe("  §7└─§c${ref.packageName}$descLine")
            }
        }

        deps.optional?.forEach { entry ->
            val ref = parseDependencyEntry(entry)
            val resolved = resolvePkg(ref.packageName)
            if (resolved == null) {
                plugin.logger.warning("${module.packageName}缺失可选依赖（可能会导致部分功能缺失）：")
                val descLine = if (ref.description != null) " : ${ref.description}" else ""
                plugin.logger.warning("  §7└─§e${ref.packageName}$descLine")
            }
        }

        val conflicts = module.manifest.conflicts
        if (conflicts != null) {
            conflicts.`break`?.forEach { entry ->
                val ref = parseDependencyEntry(entry)
                val conflicted = allModules.find {
                    it.packageName == ref.packageName && it.enabled
                } ?: providers[ref.packageName]?.takeIf { it.enabled }
                if (conflicted != null) {
                    valid = false
                    conflicted.enabled = false
                    plugin.logger.severe("${module.packageName}与${ref.packageName}存在冲突，已取消加载")
                }
            }

            conflicts.incompatible?.forEach { entry ->
                val ref = parseDependencyEntry(entry)
                val conflicted = allModules.find {
                    it.packageName == ref.packageName && it.enabled
                } ?: providers[ref.packageName]?.takeIf { it.enabled }
                if (conflicted != null) {
                    plugin.logger.warning("${module.packageName}与${ref.packageName}不兼容，可能会发生未知bug")
                    val descLine = if (ref.description != null) " : ${ref.description}" else ""
                    plugin.logger.warning("  §7└─§e${ref.packageName}$descLine")
                }
            }
        }

        return valid
    }

    private fun detectCircularDependency(modules: List<ModuleInfo>): List<String>? {
        val graph = mutableMapOf<String, MutableList<String>>()
        val pkgToModule = modules.associateBy { it.packageName }

        for (module in modules) {
            val deps = module.manifest.depends?.required ?: continue
            for (entry in deps) {
                val ref = parseDependencyEntry(entry)
                if (ref.packageName in pkgToModule) {
                    graph.computeIfAbsent(module.packageName) { mutableListOf() }.add(ref.packageName)
                }
            }
        }

        val visited = mutableSetOf<String>()
        val recursionStack = mutableListOf<String>()

        fun dfs(pkg: String): List<String>? {
            if (pkg in recursionStack) {
                val cycle = recursionStack.subList(recursionStack.indexOf(pkg), recursionStack.size) + pkg
                return cycle
            }
            if (pkg in visited) return null
            visited.add(pkg)
            recursionStack.add(pkg)
            val deps = graph[pkg]
            if (deps != null) {
                for (dep in deps) {
                    val result = dfs(dep)
                    if (result != null) return result
                }
            }
            recursionStack.removeAt(recursionStack.size - 1)
            return null
        }

        for (pkg in graph.keys) {
            val cycle = dfs(pkg)
            if (cycle != null) return cycle
        }
        return null
    }

    private fun loadModuleScript(
        module: ModuleInfo,
        configManager: ConfigManager
    ) {
        if (module.enabled) {
            configManager.copyResources(module)
        }

        val globals = JsePlatform.standardGlobals()
        module.globals = globals

        globals.set("plugin", CoerceJavaToLua.coerce(plugin))
        globals.set("server", CoerceJavaToLua.coerce(plugin.server))

        val pluginClassLoader = plugin.javaClass.classLoader
        val luajava = globals.get("luajava").checktable()
        luajava.set("bindClass", object : OneArgFunction() {
            override fun call(className: LuaValue): LuaValue {
                val clazz = Class.forName(className.tojstring(), true, pluginClassLoader)
                return CoerceJavaToLua.coerce(clazz)
            }
        })

        registerModuleHelpers(globals, module)
        registerModuleConfigHelpers(globals, module, configManager)
        registerExportImport(globals, module)

        val mainScript = File(module.scriptsDir, module.manifest.main)
        if (!mainScript.exists()) {
            plugin.logger.warning("模块 ${module.packageName} 的主程序 ${module.manifest.main} 不存在")
            return
        }

        try {
            globals.set("SCRIPT_NAME", LuaValue.valueOf(module.manifest.name))
            val chunk = globals.loadfile(mainScript.absolutePath)
            chunk.call()

            module.onEnableFunc = globals.get("onEnable").let {
                if (it.isfunction()) it else null
            }
            module.onDisableFunc = globals.get("onDisable").let {
                if (it.isfunction()) it else null
            }

            module.loaded = true
            plugin.logger.info("模块 ${module.packageName} 已加载")
        } catch (e: LuaError) {
            plugin.logger.warning("模块 ${module.packageName} Lua 错误: ${e.message}")
            module.loaded = false
        } catch (e: Exception) {
            plugin.logger.warning("模块 ${module.packageName} 加载失败: ${e.message}")
            module.loaded = false
        }
    }

    private fun registerModuleHelpers(globals: LuaValue, module: ModuleInfo) {
        globals.set("registerEvent", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val eventClassName = args.checkjstring(1)
                val priority: org.bukkit.event.EventPriority
                val callback: LuaFunction
                when (args.narg()) {
                    2 -> {
                        priority = org.bukkit.event.EventPriority.NORMAL
                        callback = args.checkfunction(2)
                    }
                    3 -> {
                        priority = org.bukkit.event.EventPriority.valueOf(args.checkjstring(2).uppercase())
                        callback = args.checkfunction(3)
                    }
                    else -> throw LuaError("Usage: registerEvent(className, [priority], callback)")
                }
                @Suppress("UNCHECKED_CAST")
                val eventClass = Class.forName(eventClassName) as Class<out org.bukkit.event.Event>
                val dummyListener = object : org.bukkit.event.Listener {}
                org.bukkit.Bukkit.getPluginManager().registerEvent(
                    eventClass, dummyListener, priority,
                    { _, event -> callback.call(CoerceJavaToLua.coerce(event)) },
                    plugin
                )
                return NIL
            }
        })

        globals.set("broadcast", object : OneArgFunction() {
            override fun call(message: LuaValue): LuaValue {
                val legacy = message.tojstring().replace("&", "§")
                org.bukkit.Bukkit.getServer().sendMessage(
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacy)
                )
                return NIL
            }
        })

        globals.set("log", object : TwoArgFunction() {
            override fun call(level: LuaValue, message: LuaValue): LuaValue {
                plugin.logger.log(
                    Level.parse(level.tojstring().uppercase()),
                    message.tojstring()
                )
                return NIL
            }
        })

        globals.set("component", object : OneArgFunction() {
            override fun call(text: LuaValue): LuaValue {
                val legacy = text.tojstring().replace("&", "§")
                val component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacy)
                return CoerceJavaToLua.coerce(component)
            }
        })

        globals.set("sendMessage", object : TwoArgFunction() {
            override fun call(player: LuaValue, text: LuaValue): LuaValue {
                val audience = player.checkuserdata(org.bukkit.entity.Player::class.java) as net.kyori.adventure.audience.Audience
                val legacy = text.tojstring().replace("&", "§")
                audience.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacy))
                return NIL
            }
        })

        globals.set("sendTitle", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val audience = args.checkuserdata(1, org.bukkit.entity.Player::class.java) as net.kyori.adventure.audience.Audience
                val title = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                    args.checkjstring(2).replace("&", "§")
                )
                val subtitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                    args.checkjstring(3).replace("&", "§")
                )
                val fadeIn = if (args.narg() >= 4) args.checkint(4) else 10
                val stay = if (args.narg() >= 5) args.checkint(5) else 70
                val fadeOut = if (args.narg() >= 6) args.checkint(6) else 20
                audience.showTitle(net.kyori.adventure.title.Title.title(
                    title, subtitle,
                    net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L)
                    )
                ))
                return NIL
            }
        })

        globals.set("playSound", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val audience = args.checkuserdata(1, org.bukkit.entity.Player::class.java) as net.kyori.adventure.audience.Audience
                val loc = args.checkuserdata(2, org.bukkit.Location::class.java) as org.bukkit.Location
                val name = args.checkjstring(3)
                val volume = args.checkdouble(4).toFloat()
                val pitch = args.checkdouble(5).toFloat()
                val x = loc.x
                val y = loc.y
                val z = loc.z
                val key = net.kyori.adventure.key.Key.key(name.lowercase())
                val sound = net.kyori.adventure.sound.Sound.sound(key, net.kyori.adventure.sound.Sound.Source.MASTER, volume, pitch)
                audience.playSound(sound, x, y, z)
                return NIL
            }
        })

        globals.set("runTask", object : OneArgFunction() {
            override fun call(callback: LuaValue): LuaValue {
                val task = callback.checkfunction()
                org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable { task.call() })
                return NIL
            }
        })

        globals.set("runTaskLater", object : TwoArgFunction() {
            override fun call(delay: LuaValue, callback: LuaValue): LuaValue {
                val task = callback.checkfunction()
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, Runnable { task.call() }, delay.tolong())
                return NIL
            }
        })

        globals.set("runTaskTimer", object : ThreeArgFunction() {
            override fun call(delay: LuaValue, period: LuaValue, callback: LuaValue): LuaValue {
                val task = callback.checkfunction()
                val bukkitTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(
                    plugin, Runnable { task.call() }, delay.tolong(), period.tolong()
                )
                return CoerceJavaToLua.coerce(bukkitTask)
            }
        })
    }

    private fun registerModuleConfigHelpers(globals: LuaValue, module: ModuleInfo, configManager: ConfigManager) {
        globals.set("loadModuleConfig", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val config = configManager.loadConfig(module.manifest.name)
                return CoerceJavaToLua.coerce(config)
            }
        })

        globals.set("saveModuleConfig", object : OneArgFunction() {
            override fun call(configValue: LuaValue): LuaValue {
                val config = configValue.checkuserdata(org.bukkit.configuration.file.YamlConfiguration::class.java) as org.bukkit.configuration.file.YamlConfiguration
                configManager.saveConfig(module.manifest.name, config)
                return NIL
            }
        })
    }

    private fun registerExportImport(globals: LuaValue, module: ModuleInfo) {
        val exportTable = LuaTable()
        module.exportedFunctions = exportTable

        globals.set("export", object : TwoArgFunction() {
            override fun call(name: LuaValue, value: LuaValue): LuaValue {
                if (!module.manifest.expose) {
                    plugin.logger.warning("模块 ${module.packageName} 未启用 expose，但调用了 export()")
                    return NIL
                }
                exportTable.set(name, value)
                return NIL
            }
        })

        globals.set("import", object : OneArgFunction() {
            override fun call(packageName: LuaValue): LuaValue {
                val target = packageName.tojstring()
                val targetModule = registry[target]
                if (targetModule == null) {
                    throw LuaError("未找到模块: $target")
                }
                if (!targetModule.manifest.expose) {
                    throw LuaError("模块 $target 未启用 expose，无法导入")
                }
                if (!targetModule.loaded) {
                    throw LuaError("模块 $target 未加载")
                }
                return targetModule.exportedFunctions ?: LuaTable()
            }
        })
    }

    private fun callOnEnable(module: ModuleInfo) {
        val func = module.onEnableFunc ?: return
        val globals = module.globals ?: return
        try {
            func.call()
        } catch (e: LuaError) {
            plugin.logger.warning("模块 ${module.packageName} onEnable 错误: ${e.message}")
        }
    }

    private fun callOnDisable(module: ModuleInfo) {
        val func = module.onDisableFunc ?: return
        try {
            func.call()
        } catch (e: LuaError) {
            plugin.logger.warning("模块 ${module.packageName} onDisable 错误: ${e.message}")
        }
    }

    companion object {
        fun parseDependencyEntry(entry: String): DependencyRef {
            val s = entry.trim()
            val atIdx = s.indexOf('@')
            require(atIdx != -1) { "Invalid dependency format, missing '@': $entry" }
            val afterAt = s.substring(atIdx + 1)
            val firstColon = afterAt.indexOf(':')
            require(firstColon != -1) { "Invalid dependency format, missing ':': $entry" }
            val name = afterAt.substring(0, firstColon)
            val rest = afterAt.substring(firstColon + 1)
            val secondColon = rest.indexOf(':')
            val version: String
            val description: String?
            if (secondColon == -1) {
                version = rest.trim()
                description = null
            } else {
                version = rest.substring(0, secondColon).trim()
                description = rest.substring(secondColon + 1).trim()
            }
            val author = s.substring(0, atIdx)
            val pkg = "$author@$name:$version"
            return DependencyRef(pkg, description)
        }
    }
}
