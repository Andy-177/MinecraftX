package paper.plugin.minecraftX

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound as AdventureSound
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.*
import java.io.File
import java.time.Duration

class LuaEngine(private val plugin: JavaPlugin, private val configManager: ScriptConfigManager) {

    private val globals = JsePlatform.standardGlobals()
    private val dummyListener = object : Listener {}

    fun init() {
        globals.set("plugin", CoerceJavaToLua.coerce(plugin))
        globals.set("server", CoerceJavaToLua.coerce(Bukkit.getServer()))
        globals.set("configManager", CoerceJavaToLua.coerce(configManager))

        val pluginClassLoader = plugin.javaClass.classLoader
        val luajava = globals.get("luajava").checktable()
        luajava.set("bindClass", object : OneArgFunction() {
            override fun call(className: LuaValue): LuaValue {
                val clazz = Class.forName(className.tojstring(), true, pluginClassLoader)
                return CoerceJavaToLua.coerce(clazz)
            }
        })

        registerEventHelper()
        registerBroadcastHelper()
        registerLogHelper()
        registerTaskHelpers()
        registerInitConfigHelper()
        registerAdventureHelpers()
    }

    fun runScript(file: File): Any? {
        val scriptName = file.nameWithoutExtension
        globals.set("SCRIPT_NAME", LuaValue.valueOf(scriptName))
        val chunk = globals.loadfile(file.absolutePath)
        return chunk.call()
    }

    fun loadScripts(scriptDir: File) {
        if (!scriptDir.exists()) return
        scriptDir.listFiles()
            ?.filter { it.isFile && it.extension == "lua" }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                try {
                    runScript(file)
                    plugin.logger.info("Loaded script: ${file.name}")
                } catch (e: LuaError) {
                    plugin.logger.warning("Lua error in ${file.name}: ${e.message}")
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load ${file.name}: ${e.message}")
                }
            }
    }

    private fun registerEventHelper() {
        globals.set("registerEvent", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val eventClassName = args.checkjstring(1)
                val priority: EventPriority
                val callback: LuaFunction

                when (args.narg()) {
                    2 -> {
                        priority = EventPriority.NORMAL
                        callback = args.checkfunction(2)
                    }
                    3 -> {
                        priority = EventPriority.valueOf(args.checkjstring(2).uppercase())
                        callback = args.checkfunction(3)
                    }
                    else -> throw LuaError("Usage: registerEvent(className, [priority], callback)")
                }

                @Suppress("UNCHECKED_CAST")
                val eventClass = Class.forName(eventClassName) as Class<out Event>
                Bukkit.getPluginManager().registerEvent(
                    eventClass, dummyListener, priority,
                    { _, event -> callback.call(CoerceJavaToLua.coerce(event)) },
                    plugin
                )
                return NIL
            }
        })
    }

    private fun registerBroadcastHelper() {
        globals.set("broadcast", object : OneArgFunction() {
            override fun call(message: LuaValue): LuaValue {
                val legacy = message.tojstring().replace("&", "§")
                Bukkit.getServer().sendMessage(
                    LegacyComponentSerializer.legacySection().deserialize(legacy)
                )
                return NIL
            }
        })
    }

    private fun registerLogHelper() {
        globals.set("log", object : TwoArgFunction() {
            override fun call(level: LuaValue, message: LuaValue): LuaValue {
                plugin.logger.log(
                    java.util.logging.Level.parse(level.tojstring().uppercase()),
                    message.tojstring()
                )
                return NIL
            }
        })
    }

    private fun registerInitConfigHelper() {
        globals.set("initConfig", object : TwoArgFunction() {
            override fun call(scriptName: LuaValue, defaultsTable: LuaValue): LuaValue {
                val name = scriptName.tojstring()
                val defaults = luaTableToMap(defaultsTable.checktable())
                val config = configManager.initConfig(name, defaults)
                return CoerceJavaToLua.coerce(config)
            }
        })
    }

    private fun registerAdventureHelpers() {
        globals.set("component", object : OneArgFunction() {
            override fun call(text: LuaValue): LuaValue {
                val legacy = text.tojstring().replace("&", "§")
                val component = LegacyComponentSerializer.legacySection().deserialize(legacy)
                return CoerceJavaToLua.coerce(component)
            }
        })

        globals.set("sendMessage", object : TwoArgFunction() {
            override fun call(player: LuaValue, text: LuaValue): LuaValue {
                val audience = player.checkuserdata(Player::class.java) as Audience
                val legacy = text.tojstring().replace("&", "§")
                audience.sendMessage(LegacyComponentSerializer.legacySection().deserialize(legacy))
                return NIL
            }
        })

        globals.set("sendTitle", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val audience = args.checkuserdata(1, Player::class.java) as Audience
                val title = LegacyComponentSerializer.legacySection().deserialize(
                    args.checkjstring(2).replace("&", "§")
                )
                val subtitle = LegacyComponentSerializer.legacySection().deserialize(
                    args.checkjstring(3).replace("&", "§")
                )
                val fadeIn = if (args.narg() >= 4) args.checkint(4) else 10
                val stay = if (args.narg() >= 5) args.checkint(5) else 70
                val fadeOut = if (args.narg() >= 6) args.checkint(6) else 20
                audience.showTitle(Title.title(
                    title, subtitle,
                    Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)
                    )
                ))
                return NIL
            }
        })

        globals.set("playSound", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val audience = args.checkuserdata(1, Player::class.java) as Audience
                val loc = args.checkuserdata(2, Location::class.java) as Location
                val name = args.checkjstring(3)
                val volume = args.checkdouble(4).toFloat()
                val pitch = args.checkdouble(5).toFloat()
                val x = loc.x
                val y = loc.y
                val z = loc.z
                val key = Key.key(name.lowercase())
                val sound = AdventureSound.sound(key, AdventureSound.Source.MASTER, volume, pitch)
                audience.playSound(sound, x, y, z)
                return NIL
            }
        })
    }

    private fun luaTableToMap(table: LuaTable): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        var k = LuaValue.NIL
        while (true) {
            val n = table.next(k)
            k = n.arg1()
            if (k.isnil()) break
            result[k.tojstring()] = luaValueToJava(n.arg(2))
        }
        return result
    }

    private fun luaValueToJava(value: LuaValue): Any {
        return when {
            value.isnil() -> "null"
            value.isboolean() -> value.toboolean()
            value.isint() -> value.toint()
            value.isnumber() -> value.todouble()
            value.isstring() -> value.tojstring()
            value.istable() -> luaTableToMap(value.checktable())
            else -> value.tojstring()
        }
    }

    private fun registerTaskHelpers() {
        globals.set("runTask", object : OneArgFunction() {
            override fun call(callback: LuaValue): LuaValue {
                val task = callback.checkfunction()
                Bukkit.getScheduler().runTask(plugin, Runnable { task.call() })
                return NIL
            }
        })

        globals.set("runTaskLater", object : TwoArgFunction() {
            override fun call(delay: LuaValue, callback: LuaValue): LuaValue {
                val task = callback.checkfunction()
                Bukkit.getScheduler().runTaskLater(plugin, Runnable { task.call() }, delay.tolong())
                return NIL
            }
        })

        globals.set("runTaskTimer", object : ThreeArgFunction() {
            override fun call(delay: LuaValue, period: LuaValue, callback: LuaValue): LuaValue {
                val task = callback.checkfunction()
                val bukkitTask = Bukkit.getScheduler().runTaskTimer(
                    plugin, Runnable { task.call() }, delay.tolong(), period.tolong()
                )
                return CoerceJavaToLua.coerce(bukkitTask)
            }
        })
    }
}
