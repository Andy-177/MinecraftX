package paper.plugin.minecraftX.bridge

import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.*
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Method

class AnnotationBridge {

    fun register(globals: LuaValue) {
        globals.set("getAnnotations", object : OneArgFunction() {
            override fun call(obj: LuaValue): LuaValue {
                val element = resolveAnnotatedElement(obj)
                val table = LuaTable()
                var idx = 1
                for (ann in element.annotations) {
                    table.set(idx, annotationToJavaTable(ann as java.lang.annotation.Annotation))
                    idx++
                }
                return table
            }
        })

        @Suppress("UNCHECKED_CAST")
        globals.set("getAnnotation", object : TwoArgFunction() {
            override fun call(obj: LuaValue, annClass: LuaValue): LuaValue {
                val element = resolveAnnotatedElement(obj)
                val annType = Class.forName(annClass.tojstring()) as Class<out kotlin.Annotation>
                val annotation = element.getAnnotation(annType)
                return if (annotation != null) annotationToJavaTable(annotation as java.lang.annotation.Annotation)
                else LuaValue.NIL
            }
        })

        globals.set("hasAnnotation", object : TwoArgFunction() {
            override fun call(obj: LuaValue, annClass: LuaValue): LuaValue {
                return try {
                    val element = resolveAnnotatedElement(obj)
                    @Suppress("UNCHECKED_CAST")
                    val annType = Class.forName(annClass.tojstring()) as Class<out kotlin.Annotation>
                    LuaValue.valueOf(element.isAnnotationPresent(annType))
                } catch (_: Exception) {
                    LuaValue.FALSE
                }
            }
        })

        @Suppress("UNCHECKED_CAST")
        globals.set("getAnnotationsByType", object : TwoArgFunction() {
            override fun call(obj: LuaValue, annClass: LuaValue): LuaValue {
                val element = resolveAnnotatedElement(obj)
                val annType = Class.forName(annClass.tojstring()) as Class<out kotlin.Annotation>
                val annotations = element.getAnnotationsByType(annType)
                val table = LuaTable()
                var idx = 1
                for (ann in annotations) {
                    table.set(idx, annotationToJavaTable(ann as java.lang.annotation.Annotation))
                    idx++
                }
                return table
            }
        })

        globals.set("getDeclaredAnnotations", object : OneArgFunction() {
            override fun call(obj: LuaValue): LuaValue {
                val element = resolveAnnotatedElement(obj)
                val table = LuaTable()
                var idx = 1
                for (ann in element.declaredAnnotations) {
                    table.set(idx, annotationToJavaTable(ann as java.lang.annotation.Annotation))
                    idx++
                }
                return table
            }
        })

        globals.set("getMethodAnnotations", object : OneArgFunction() {
            override fun call(methodObj: LuaValue): LuaValue {
                val method = methodObj.checkuserdata(Method::class.java) as Method
                val table = LuaTable()
                var idx = 1
                for (ann in method.declaredAnnotations) {
                    table.set(idx, annotationToJavaTable(ann as java.lang.annotation.Annotation))
                    idx++
                }
                return table
            }
        })

        globals.set("getFieldAnnotations", object : OneArgFunction() {
            override fun call(fieldObj: LuaValue): LuaValue {
                val field = fieldObj.checkuserdata(Field::class.java) as Field
                val table = LuaTable()
                var idx = 1
                for (ann in field.declaredAnnotations) {
                    table.set(idx, annotationToJavaTable(ann as java.lang.annotation.Annotation))
                    idx++
                }
                return table
            }
        })

        globals.set("getParameterAnnotations", object : TwoArgFunction() {
            override fun call(methodObj: LuaValue, paramIndex: LuaValue): LuaValue {
                val method = methodObj.checkuserdata(Method::class.java) as Method
                val index = paramIndex.checkint()
                val allParams = method.parameterAnnotations
                if (index < 0 || index >= allParams.size) return LuaTable()
                val table = LuaTable()
                var idx = 1
                for (ann in allParams[index]) {
                    table.set(idx, annotationToJavaTable(ann as java.lang.annotation.Annotation))
                    idx++
                }
                return table
            }
        })
    }

    private fun resolveAnnotatedElement(obj: LuaValue): AnnotatedElement {
        if (obj.isuserdata()) {
            val userObj = obj.checkuserdata(Any::class.java)
            if (userObj is Class<*>) return userObj
            if (userObj is Method) return userObj
            if (userObj is Field) return userObj
            if (userObj is java.lang.reflect.Constructor<*>) return userObj
            if (userObj is java.lang.reflect.Parameter) return userObj
            return userObj.javaClass
        }
        if (obj.isstring()) {
            val className = obj.tojstring()
            return Class.forName(className)
        }
        throw LuaError("cannot resolve annotated element from: ${obj.type()}")
    }

    companion object {
        fun annotationToJavaTable(annotation: java.lang.annotation.Annotation): LuaValue {
            val table = LuaTable()
            val annType = annotation.annotationType()
            table.set("annotationType", annType.name)
            for (method in annType.declaredMethods) {
                if (method.parameterCount == 0) {
                    try {
                        method.isAccessible = true
                        val value = method.invoke(annotation)
                        table.set(method.name, CoerceJavaToLua.coerce(value))
                    } catch (_: Exception) {
                    }
                }
            }
            return CoerceJavaToLua.coerce(annotation)
        }
    }
}
