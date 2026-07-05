package paper.plugin.minecraftX.bridge

import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.*
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class LambdaBridge {

    fun register(globals: LuaValue) {
        globals.set("createSAM", object : TwoArgFunction() {
            override fun call(className: LuaValue, func: LuaValue): LuaValue {
                val clazz = Class.forName(className.tojstring())
                val luaFunc = func.checkfunction()
                return createSAMProxy(clazz, luaFunc)
            }
        })

        globals.set("wrapLambda", object : TwoArgFunction() {
            override fun call(func: LuaValue, interfaceClass: LuaValue): LuaValue {
                val clazz = Class.forName(interfaceClass.tojstring())
                val luaFunc = func.checkfunction()
                if (!clazz.isInterface) throw LuaError("$clazz is not an interface")
                val samMethod = findSAMMethod(clazz)
                if (samMethod == null) throw LuaError("$clazz is not a functional interface")
                return createSAMProxy(clazz, luaFunc)
            }
        })

        globals.set("isFunctionalInterface", object : OneArgFunction() {
            override fun call(className: LuaValue): LuaValue {
                return try {
                    val clazz = Class.forName(className.tojstring())
                    LuaValue.valueOf(clazz.isInterface && findSAMMethod(clazz) != null)
                } catch (_: Exception) {
                    LuaValue.FALSE
                }
            }
        })

        globals.set("createRunnable", object : OneArgFunction() {
            override fun call(func: LuaValue): LuaValue {
                return createSAMProxy(Runnable::class.java, func.checkfunction())
            }
        })

        globals.set("createSupplier", object : OneArgFunction() {
            override fun call(func: LuaValue): LuaValue {
                return createSAMProxy(java.util.function.Supplier::class.java, func.checkfunction())
            }
        })

        globals.set("createConsumer", object : OneArgFunction() {
            override fun call(func: LuaValue): LuaValue {
                return createSAMProxy(java.util.function.Consumer::class.java, func.checkfunction())
            }
        })

        globals.set("createFunction", object : OneArgFunction() {
            override fun call(func: LuaValue): LuaValue {
                return createSAMProxy(java.util.function.Function::class.java, func.checkfunction())
            }
        })

        globals.set("createPredicate", object : OneArgFunction() {
            override fun call(func: LuaValue): LuaValue {
                return createSAMProxy(java.util.function.Predicate::class.java, func.checkfunction())
            }
        })
    }

    private fun findSAMMethod(clazz: Class<*>): Method? {
        val methods = clazz.methods.filter {
            it.declaringClass == clazz && java.lang.reflect.Modifier.isAbstract(it.modifiers)
        }
        return if (methods.size == 1) methods[0] else null
    }

    private fun createSAMProxy(clazz: Class<*>, func: LuaFunction): LuaValue {
        val handler = SAMInvocationHandler(func)
        val proxy = Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz),
            handler
        )
        return CoerceJavaToLua.coerce(proxy)
    }
}

internal class SAMInvocationHandler(private val func: LuaFunction) : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        if (method.declaringClass == Any::class.java) {
            return when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "LambdaProxy@" + Integer.toHexString(System.identityHashCode(proxy))
                else -> null
            }
        }
        val result = if (args != null && args.isNotEmpty()) {
            val luaArgs = args.map { CoerceJavaToLua.coerce(it) }.toTypedArray()
            callLuaFunc(func, luaArgs)
        } else {
            func.call()
        }
        if (method.returnType == Void.TYPE || method.returnType == Void::class.javaPrimitiveType) return null
        if (result.isnil()) return null
        return CoerceLuaToJava.coerce(result, method.returnType)
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
}
