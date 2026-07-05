package paper.plugin.minecraftX.bridge

import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.*
import java.lang.reflect.*

class ReflectionBridge {

    fun register(globals: LuaValue) {
        registerFieldHelpers(globals)
        registerMethodHelpers(globals)
        registerConstructorHelpers(globals)
        registerEnumHelpers(globals)
        registerArrayHelpers(globals)
        registerClassHelpers(globals)
        registerModifierHelpers(globals)
    }

    private fun registerFieldHelpers(globals: LuaValue) {
        globals.set("getField", object : TwoArgFunction() {
            override fun call(obj: LuaValue, fieldName: LuaValue): LuaValue {
                val target = obj.checkuserdata(Any::class.java)
                val name = fieldName.tojstring()
                val field = findField(target.javaClass, name)
                field.isAccessible = true
                val value = field.get(target)
                return CoerceJavaToLua.coerce(value)
            }
        })

        globals.set("setField", object : ThreeArgFunction() {
            override fun call(obj: LuaValue, fieldName: LuaValue, value: LuaValue): LuaValue {
                val target = obj.checkuserdata(Any::class.java)
                val name = fieldName.tojstring()
                val field = findField(target.javaClass, name)
                field.isAccessible = true
                val javaValue = CoerceLuaToJava.coerce(value, field.type)
                field.set(target, javaValue)
                return LuaValue.NIL
            }
        })

        globals.set("getStaticField", object : TwoArgFunction() {
            override fun call(classObj: LuaValue, fieldName: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val name = fieldName.tojstring()
                val field = findField(clazz, name)
                field.isAccessible = true
                val value = field.get(null)
                return CoerceJavaToLua.coerce(value)
            }
        })

        globals.set("setStaticField", object : ThreeArgFunction() {
            override fun call(classObj: LuaValue, fieldName: LuaValue, value: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val name = fieldName.tojstring()
                val field = findField(clazz, name)
                field.isAccessible = true
                val javaValue = CoerceLuaToJava.coerce(value, field.type)
                field.set(null, javaValue)
                return LuaValue.NIL
            }
        })

        globals.set("getDeclaredFields", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val fields = clazz.declaredFields
                val table = LuaTable()
                var idx = 1
                for (f in fields) {
                    val ft = LuaTable()
                    ft.set("name", f.name)
                    ft.set("type", f.type.name)
                    ft.set("modifiers", f.modifiers)
                    ft.set("declaringClass", f.declaringClass.name)
                    table.set(idx, ft)
                    idx++
                }
                return table
            }
        })

        globals.set("getFields", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val fields = clazz.fields
                val table = LuaTable()
                var idx = 1
                for (f in fields) {
                    val ft = LuaTable()
                    ft.set("name", f.name)
                    ft.set("type", f.type.name)
                    ft.set("modifiers", f.modifiers)
                    table.set(idx, ft)
                    idx++
                }
                return table
            }
        })
    }

    private fun registerMethodHelpers(globals: LuaValue) {
        globals.set("getMethods", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val methods = clazz.methods
                val table = LuaTable()
                var idx = 1
                for (m in methods) {
                    table.set(idx, CoerceJavaToLua.coerce(m))
                    idx++
                }
                return table
            }
        })

        globals.set("getDeclaredMethods", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val methods = clazz.declaredMethods
                val table = LuaTable()
                var idx = 1
                for (m in methods) {
                    table.set(idx, CoerceJavaToLua.coerce(m))
                    idx++
                }
                return table
            }
        })

        globals.set("invokeMethod", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val obj = args.checkuserdata(1, Any::class.java)
                val methodName = args.checkjstring(2)
                val methodArgs = mutableListOf<Any?>()
                for (i in 3..args.narg()) {
                    methodArgs.add(CoerceLuaToJava.coerce(args.arg(i), Any::class.java))
                }
                val method = findMethod(obj.javaClass, methodName, methodArgs.size)
                method.isAccessible = true
                val result = method.invoke(obj, *methodArgs.toTypedArray())
                return CoerceJavaToLua.coerce(result)
            }
        })

        globals.set("invokeStatic", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val clazz = resolveClass(args.checkvalue(1))
                val methodName = args.checkjstring(2)
                val methodArgs = mutableListOf<Any?>()
                for (i in 3..args.narg()) {
                    methodArgs.add(CoerceLuaToJava.coerce(args.arg(i), Any::class.java))
                }
                val method = findMethod(clazz, methodName, methodArgs.size)
                method.isAccessible = true
                val result = method.invoke(null, *methodArgs.toTypedArray())
                return CoerceJavaToLua.coerce(result)
            }
        })

        globals.set("methodInfo", object : OneArgFunction() {
            override fun call(methodObj: LuaValue): LuaValue {
                val method = methodObj.checkuserdata(Method::class.java) as Method
                val table = LuaTable()
                table.set("name", method.name)
                table.set("returnType", method.returnType.name)
                table.set("modifiers", method.modifiers)
                table.set("declaringClass", method.declaringClass.name)
                val paramTypes = LuaTable()
                for ((i, p) in method.parameterTypes.withIndex()) {
                    paramTypes.set(i + 1, p.name)
                }
                table.set("parameterTypes", paramTypes)
                val exceptionTypes = LuaTable()
                for ((i, e) in method.exceptionTypes.withIndex()) {
                    exceptionTypes.set(i + 1, e.name)
                }
                table.set("exceptionTypes", exceptionTypes)
                return table
            }
        })
    }

    private fun registerConstructorHelpers(globals: LuaValue) {
        globals.set("getConstructors", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val constructors = clazz.constructors
                val table = LuaTable()
                var idx = 1
                for (c in constructors) {
                    table.set(idx, CoerceJavaToLua.coerce(c))
                    idx++
                }
                return table
            }
        })

        globals.set("getDeclaredConstructors", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val constructors = clazz.declaredConstructors
                val table = LuaTable()
                var idx = 1
                for (c in constructors) {
                    table.set(idx, CoerceJavaToLua.coerce(c))
                    idx++
                }
                return table
            }
        })

        globals.set("newInstance", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val clazz = resolveClass(args.checkvalue(1))
                val initArgs = mutableListOf<Any?>()
                for (i in 2..args.narg()) {
                    initArgs.add(CoerceLuaToJava.coerce(args.arg(i), Any::class.java))
                }
                val constructor = findConstructor(clazz, initArgs.size)
                constructor.isAccessible = true
                val instance = constructor.newInstance(*initArgs.toTypedArray())
                return CoerceJavaToLua.coerce(instance)
            }
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerEnumHelpers(globals: LuaValue) {
        globals.set("enumValues", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                if (!clazz.isEnum) throw LuaError("${clazz.name} is not an enum")
                val values = clazz.enumConstants
                val table = LuaTable()
                for ((i, v) in values.withIndex()) {
                    table.set(i + 1, CoerceJavaToLua.coerce(v))
                }
                return table
            }
        })

        globals.set("enumValueOf", object : TwoArgFunction() {
            override fun call(classObj: LuaValue, name: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                if (!clazz.isEnum) throw LuaError("${clazz.name} is not an enum")
                val targetName = name.tojstring()
                val values = clazz.enumConstants
                for (v in values) {
                    val enumVal = v as java.lang.Enum<*>
                    val nameMethod = java.lang.Enum::class.java.getMethod("name")
                    val enumName = nameMethod.invoke(enumVal) as String
                    if (enumName == targetName) return CoerceJavaToLua.coerce(enumVal)
                }
                throw LuaError("no enum constant ${clazz.name}.$targetName")
            }
        })

        globals.set("enumName", object : OneArgFunction() {
            override fun call(enumObj: LuaValue): LuaValue {
                val enumVal = enumObj.checkuserdata(Enum::class.java) as Enum<*>
                return LuaValue.valueOf(enumVal.name)
            }
        })

        globals.set("enumOrdinal", object : OneArgFunction() {
            override fun call(enumObj: LuaValue): LuaValue {
                val enumVal = enumObj.checkuserdata(Enum::class.java) as Enum<*>
                return LuaValue.valueOf(enumVal.ordinal)
            }
        })
    }

    private fun registerArrayHelpers(globals: LuaValue) {
        globals.set("arrayLength", object : OneArgFunction() {
            override fun call(arr: LuaValue): LuaValue {
                val javaArr = arr.checkuserdata(Any::class.java)
                return LuaValue.valueOf(java.lang.reflect.Array.getLength(javaArr))
            }
        })

        globals.set("arrayGet", object : TwoArgFunction() {
            override fun call(arr: LuaValue, index: LuaValue): LuaValue {
                val javaArr = arr.checkuserdata(Any::class.java)
                val idx = index.checkint() - 1
                val value = java.lang.reflect.Array.get(javaArr, idx)
                return CoerceJavaToLua.coerce(value)
            }
        })

        globals.set("arraySet", object : ThreeArgFunction() {
            override fun call(arr: LuaValue, index: LuaValue, value: LuaValue): LuaValue {
                val javaArr = arr.checkuserdata(Any::class.java)
                val idx = index.checkint() - 1
                val componentType = javaArr.javaClass.componentType
                val javaValue = CoerceLuaToJava.coerce(value, componentType)
                java.lang.reflect.Array.set(javaArr, idx, javaValue)
                return LuaValue.NIL
            }
        })

        globals.set("newArray", object : TwoArgFunction() {
            override fun call(componentClass: LuaValue, length: LuaValue): LuaValue {
                val clazz = Class.forName(componentClass.tojstring())
                val size = length.checkint()
                val arr = java.lang.reflect.Array.newInstance(clazz, size)
                return CoerceJavaToLua.coerce(arr)
            }
        })

        globals.set("newPrimitiveArray", object : TwoArgFunction() {
            override fun call(typeName: LuaValue, length: LuaValue): LuaValue {
                val type = typeName.tojstring()
                val size = length.checkint()
                val arr = when (type.lowercase()) {
                    "int" -> IntArray(size)
                    "long" -> LongArray(size)
                    "double" -> DoubleArray(size)
                    "float" -> FloatArray(size)
                    "boolean" -> BooleanArray(size)
                    "byte" -> ByteArray(size)
                    "short" -> ShortArray(size)
                    "char" -> CharArray(size)
                    else -> throw LuaError("unknown primitive type: $type")
                }
                return CoerceJavaToLua.coerce(arr)
            }
        })

        globals.set("arrayToList", object : OneArgFunction() {
            override fun call(obj: LuaValue): LuaValue {
                val javaArr = obj.checkuserdata(Any::class.java)
                if (!javaArr.javaClass.isArray) throw LuaError("not an array")
                val len = java.lang.reflect.Array.getLength(javaArr)
                val list = ArrayList<Any>(len)
                for (i in 0 until len) {
                    list.add(java.lang.reflect.Array.get(javaArr, i))
                }
                return CoerceJavaToLua.coerce(list)
            }
        })
    }

    private fun registerClassHelpers(globals: LuaValue) {
        globals.set("classOf", object : OneArgFunction() {
            override fun call(obj: LuaValue): LuaValue {
                if (!obj.isuserdata()) return LuaValue.NIL
                val javaObj = obj.checkuserdata(Any::class.java)
                return CoerceJavaToLua.coerce(javaObj.javaClass)
            }
        })

        globals.set("className", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                return LuaValue.valueOf(clazz.name)
            }
        })

        globals.set("classSimpleName", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                return LuaValue.valueOf(clazz.simpleName)
            }
        })

        globals.set("superClass", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val superClass = clazz.superclass ?: return LuaValue.NIL
                return CoerceJavaToLua.coerce(superClass)
            }
        })

        globals.set("interfaces", object : OneArgFunction() {
            override fun call(classObj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val interfaces = clazz.interfaces
                val table = LuaTable()
                for ((i, intf) in interfaces.withIndex()) {
                    table.set(i + 1, intf.name)
                }
                return table
            }
        })

        globals.set("isAssignableFrom", object : TwoArgFunction() {
            override fun call(classObj: LuaValue, otherClass: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                val other = resolveClass(otherClass)
                return LuaValue.valueOf(clazz.isAssignableFrom(other))
            }
        })

        globals.set("isInstance", object : TwoArgFunction() {
            override fun call(classObj: LuaValue, obj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                if (!obj.isuserdata()) return LuaValue.FALSE
                val javaObj = obj.checkuserdata(Any::class.java)
                return LuaValue.valueOf(clazz.isInstance(javaObj))
            }
        })

        globals.set("cast", object : TwoArgFunction() {
            override fun call(classObj: LuaValue, obj: LuaValue): LuaValue {
                val clazz = resolveClass(classObj)
                if (!obj.isuserdata()) throw LuaError("cannot cast non-Java object")
                val javaObj = obj.checkuserdata(Any::class.java)
                return CoerceJavaToLua.coerce(clazz.cast(javaObj))
            }
        })
    }

    private fun registerModifierHelpers(globals: LuaValue) {
        globals.set("isPublic", object : OneArgFunction() {
            override fun call(mods: LuaValue): LuaValue {
                return LuaValue.valueOf(Modifier.isPublic(mods.checkint()))
            }
        })

        globals.set("isPrivate", object : OneArgFunction() {
            override fun call(mods: LuaValue): LuaValue {
                return LuaValue.valueOf(Modifier.isPrivate(mods.checkint()))
            }
        })

        globals.set("isStatic", object : OneArgFunction() {
            override fun call(mods: LuaValue): LuaValue {
                return LuaValue.valueOf(Modifier.isStatic(mods.checkint()))
            }
        })

        globals.set("isFinal", object : OneArgFunction() {
            override fun call(mods: LuaValue): LuaValue {
                return LuaValue.valueOf(Modifier.isFinal(mods.checkint()))
            }
        })

        globals.set("isAbstract", object : OneArgFunction() {
            override fun call(mods: LuaValue): LuaValue {
                return LuaValue.valueOf(Modifier.isAbstract(mods.checkint()))
            }
        })
    }

    private fun resolveClass(value: LuaValue): Class<*> {
        if (value.isuserdata()) {
            val obj = value.checkuserdata(Any::class.java)
            if (obj is Class<*>) return obj
            return obj.javaClass
        }
        if (value.isstring()) {
            return Class.forName(value.tojstring())
        }
        throw LuaError("cannot resolve class from: ${value.type()}")
    }

    private fun findField(clazz: Class<*>, name: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                try {
                    return current.getField(name)
                } catch (_: NoSuchFieldException) {
                    current = current.superclass
                }
            }
        }
        throw LuaError("field not found: $name in ${clazz.name}")
    }

    private fun findMethod(clazz: Class<*>, name: String, argCount: Int): Method {
        for (method in clazz.methods) {
            if (method.name == name && method.parameterCount == argCount) {
                return method
            }
        }
        var current: Class<*>? = clazz
        while (current != null) {
            for (method in current.declaredMethods) {
                if (method.name == name && method.parameterCount == argCount) {
                    return method
                }
            }
            current = current.superclass
        }
        throw LuaError("method not found: $name with $argCount args in ${clazz.name}")
    }

    private fun findConstructor(clazz: Class<*>, argCount: Int): Constructor<*> {
        for (ctor in clazz.constructors) {
            if (ctor.parameterCount == argCount) return ctor
        }
        for (ctor in clazz.declaredConstructors) {
            if (ctor.parameterCount == argCount) return ctor
        }
        throw LuaError("constructor with $argCount args not found in ${clazz.name}")
    }
}
