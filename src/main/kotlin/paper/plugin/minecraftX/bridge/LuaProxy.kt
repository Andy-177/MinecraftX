package paper.plugin.minecraftX.bridge

import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.*
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class LuaProxyBridge {

    fun register(globals: LuaValue) {
        globals.set("createProxy", object : TwoArgFunction() {
            override fun call(className: LuaValue, methods: LuaValue): LuaValue {
                val clazz = Class.forName(className.tojstring())
                val methodTable = methods.checktable()
                return createProxy(clazz, methodTable)
            }
        })

        globals.set("createProxyFor", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val table = args.checktable(1)
                val methods = args.checktable(2)
                val interfaces = mutableListOf<Class<*>>()
                var k = LuaValue.NIL
                while (true) {
                    val n = table.next(k)
                    k = n.arg1()
                    if (k.isnil()) break
                    interfaces.add(Class.forName(n.arg(2).tojstring()))
                }
                if (interfaces.isEmpty()) throw LuaError("at least one interface required")
                return createProxyForInterfaces(interfaces, methods)
            }
        })

        globals.set("isProxy", object : OneArgFunction() {
            override fun call(obj: LuaValue): LuaValue {
                if (!obj.isuserdata()) return LuaValue.FALSE
                val javaObj = obj.checkuserdata(Any::class.java)
                return LuaValue.valueOf(Proxy.isProxyClass(javaObj.javaClass))
            }
        })

        globals.set("getProxyHandler", object : OneArgFunction() {
            override fun call(obj: LuaValue): LuaValue {
                if (!obj.isuserdata()) return LuaValue.NIL
                val javaObj = obj.checkuserdata(Any::class.java)
                if (!Proxy.isProxyClass(javaObj.javaClass)) return LuaValue.NIL
                val handler = Proxy.getInvocationHandler(javaObj)
                if (handler is LuaInvocationHandler) {
                    return handler.luaMethods
                }
                return LuaValue.NIL
            }
        })
    }

    private fun createProxy(clazz: Class<*>, methods: LuaTable): LuaValue {
        if (clazz.isInterface) {
            return createProxyForInterfaces(listOf(clazz), methods)
        }
        val interfaces = clazz.interfaces.filter { it.isInterface }
        if (interfaces.isNotEmpty()) {
            return createProxyForInterfaces(interfaces, methods)
        }
        throw LuaError("cannot proxy class ${clazz.name}: not an interface and has no interfaces to delegate to")
    }

    private fun createProxyForInterfaces(interfaces: List<Class<*>>, methods: LuaTable): LuaValue {
        val handler = LuaInvocationHandler(methods)
        val proxy = Proxy.newProxyInstance(
            interfaces[0].classLoader,
            interfaces.toTypedArray(),
            handler
        )
        return CoerceJavaToLua.coerce(proxy)
    }
}

internal class LuaInvocationHandler(val luaMethods: LuaTable) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        val luaFunc = luaMethods.get(method.name)
        if (luaFunc.isfunction()) {
            val func = luaFunc.checkfunction()
            val result = if (args != null && args.isNotEmpty()) {
                val luaArgs = args.map { CoerceJavaToLua.coerce(it) }.toTypedArray()
                callLuaFunc(func, luaArgs)
            } else {
                func.call()
            }
            return coerceResult(result, method.returnType)
        }
        return handleDefaultMethod(proxy, method, args)
    }

    private fun callLuaFunc(func: LuaFunction, luaArgs: Array<LuaValue>): LuaValue {
        return when (luaArgs.size) {
            0 -> func.call()
            1 -> func.call(luaArgs[0])
            2 -> func.call(luaArgs[0], luaArgs[1])
            3 -> func.call(luaArgs[0], luaArgs[1], luaArgs[2])
            else -> {
                val varArgs = LuaValue.varargsOf(luaArgs)
                val lv = func as LuaValue
                lv.invoke(varArgs).arg1()
            }
        }
    }

    private fun handleDefaultMethod(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        when (method.name) {
            "equals" -> {
                val other = args?.getOrNull(0)
                return proxy === other
            }
            "hashCode" -> return System.identityHashCode(proxy)
            "toString" -> return "LuaProxy@" + Integer.toHexString(System.identityHashCode(proxy))
        }
        return null
    }

    private fun coerceResult(result: LuaValue, returnType: Class<*>): Any? {
        if (returnType == Void.TYPE || returnType == Void::class.javaPrimitiveType) return null
        if (result.isnil()) return null
        return CoerceLuaToJava.coerce(result, returnType)
    }
}
