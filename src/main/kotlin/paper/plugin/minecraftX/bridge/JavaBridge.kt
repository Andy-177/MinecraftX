package paper.plugin.minecraftX.bridge

import org.bukkit.plugin.java.JavaPlugin
import org.luaj.vm2.LuaValue

class JavaBridge(private val plugin: JavaPlugin) {

    fun registerAll(globals: LuaValue) {
        LuaProxyBridge().register(globals)
        LambdaBridge().register(globals)
        AnnotationBridge().register(globals)
        AsyncBridge(plugin).register(globals)
        ReflectionBridge().register(globals)
        CollectionBridge().register(globals)
    }
}
