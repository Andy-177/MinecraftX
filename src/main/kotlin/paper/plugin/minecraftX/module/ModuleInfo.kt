package paper.plugin.minecraftX.module

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.io.File

class ModuleInfo(
    val manifest: Manifest,
    val moduleDir: File
) {
    val packageName: String get() = "${manifest.author}@${manifest.name}:${manifest.version}"
    val scriptsDir: File get() = File(moduleDir, "scripts")
    val resourcesDir: File get() = File(moduleDir, "resources")

    var enabled: Boolean = true
    var loaded: Boolean = false
    var globals: LuaValue? = null
    var exportedFunctions: LuaTable? = null
    var onEnableFunc: LuaValue? = null
    var onDisableFunc: LuaValue? = null

    override fun toString(): String = packageName
}
