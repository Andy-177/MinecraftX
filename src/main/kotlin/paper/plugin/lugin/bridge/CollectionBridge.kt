package paper.plugin.lugin.bridge

import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.*
import java.util.*
import java.util.stream.*

class CollectionBridge {

    fun register(globals: LuaValue) {
        registerListHelpers(globals)
        registerMapHelpers(globals)
        registerSetHelpers(globals)
        registerOptionalHelpers(globals)
        registerStreamHelpers(globals)
        registerConversionHelpers(globals)
    }

    private fun registerListHelpers(globals: LuaValue) {
        globals.set("newArrayList", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val list = ArrayList<Any>()
                for (i in 1..args.narg()) {
                    list.add(CoerceLuaToJava.coerce(args.arg(i), Any::class.java))
                }
                return CoerceJavaToLua.coerce(list)
            }
        })

        globals.set("newLinkedList", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                return CoerceJavaToLua.coerce(LinkedList<Any>())
            }
        })

        globals.set("listSize", object : OneArgFunction() {
            override fun call(listObj: LuaValue): LuaValue {
                val list = listObj.checkuserdata(List::class.java) as List<*>
                return LuaValue.valueOf(list.size)
            }
        })

        globals.set("listGet", object : TwoArgFunction() {
            override fun call(listObj: LuaValue, index: LuaValue): LuaValue {
                val list = listObj.checkuserdata(List::class.java) as List<*>
                val idx = index.checkint() - 1
                return CoerceJavaToLua.coerce(list[idx])
            }
        })

        globals.set("listSet", object : ThreeArgFunction() {
            override fun call(listObj: LuaValue, index: LuaValue, value: LuaValue): LuaValue {
                val list = listObj.checkuserdata(List::class.java) as MutableList<Any?>
                val idx = index.checkint() - 1
                val javaValue = CoerceLuaToJava.coerce(value, Any::class.java)
                list[idx] = javaValue
                return LuaValue.NIL
            }
        })

        globals.set("listAdd", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val list = args.checkuserdata(1, MutableList::class.java) as MutableList<Any?>
                for (i in 2..args.narg()) {
                    val javaValue = CoerceLuaToJava.coerce(args.arg(i), Any::class.java)
                    list.add(javaValue)
                }
                return LuaValue.valueOf(true)
            }
        })

        globals.set("listRemove", object : TwoArgFunction() {
            override fun call(listObj: LuaValue, value: LuaValue): LuaValue {
                val list = listObj.checkuserdata(MutableList::class.java) as MutableList<Any?>
                val javaValue = CoerceLuaToJava.coerce(value, Any::class.java)
                return LuaValue.valueOf(list.remove(javaValue))
            }
        })

        globals.set("listRemoveAt", object : TwoArgFunction() {
            override fun call(listObj: LuaValue, index: LuaValue): LuaValue {
                val list = listObj.checkuserdata(MutableList::class.java) as MutableList<Any?>
                val idx = index.checkint() - 1
                val removed = list.removeAt(idx)
                return CoerceJavaToLua.coerce(removed)
            }
        })

        globals.set("listClear", object : OneArgFunction() {
            override fun call(listObj: LuaValue): LuaValue {
                val list = listObj.checkuserdata(MutableList::class.java) as MutableList<*>
                list.clear()
                return LuaValue.NIL
            }
        })

        globals.set("listContains", object : TwoArgFunction() {
            override fun call(listObj: LuaValue, value: LuaValue): LuaValue {
                val list = listObj.checkuserdata(List::class.java) as List<*>
                val javaValue = CoerceLuaToJava.coerce(value, Any::class.java)
                return LuaValue.valueOf(list.contains(javaValue))
            }
        })

        globals.set("listIsEmpty", object : OneArgFunction() {
            override fun call(listObj: LuaValue): LuaValue {
                val list = listObj.checkuserdata(List::class.java) as List<*>
                return LuaValue.valueOf(list.isEmpty())
            }
        })

        @Suppress("UNCHECKED_CAST")
        globals.set("listSort", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val list = args.checkuserdata(1, MutableList::class.java) as MutableList<Any>
                if (args.narg() >= 2) {
                    val comparator = args.checkuserdata(2, Comparator::class.java) as Comparator<Any>
                    list.sortWith(comparator)
                } else {
                    (list as MutableList<Comparable<Any>>).sort()
                }
                return LuaValue.NIL
            }
        })

        globals.set("listToTable", object : OneArgFunction() {
            override fun call(listObj: LuaValue): LuaValue {
                val list = listObj.checkuserdata(List::class.java) as List<*>
                val table = LuaTable()
                for ((i, item) in list.withIndex()) {
                    table.set(i + 1, CoerceJavaToLua.coerce(item))
                }
                return table
            }
        })
    }

    private fun registerMapHelpers(globals: LuaValue) {
        globals.set("newHashMap", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                return CoerceJavaToLua.coerce(HashMap<String, Any>())
            }
        })

        globals.set("newLinkedHashMap", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                return CoerceJavaToLua.coerce(LinkedHashMap<String, Any>())
            }
        })

        globals.set("newTreeMap", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                return CoerceJavaToLua.coerce(TreeMap<String, Any>())
            }
        })

        globals.set("mapPut", object : ThreeArgFunction() {
            override fun call(mapObj: LuaValue, key: LuaValue, value: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(MutableMap::class.java) as MutableMap<Any?, Any?>
                val k = CoerceLuaToJava.coerce(key, Any::class.java)
                val v = CoerceLuaToJava.coerce(value, Any::class.java)
                map.put(k, v)
                return LuaValue.NIL
            }
        })

        globals.set("mapGet", object : TwoArgFunction() {
            override fun call(mapObj: LuaValue, key: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(Map::class.java) as Map<*, *>
                val k = CoerceLuaToJava.coerce(key, Any::class.java)
                return CoerceJavaToLua.coerce(map[k])
            }
        })

        globals.set("mapRemove", object : TwoArgFunction() {
            override fun call(mapObj: LuaValue, key: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(MutableMap::class.java) as MutableMap<*, *>
                val k = CoerceLuaToJava.coerce(key, Any::class.java)
                val removed = map.remove(k)
                return CoerceJavaToLua.coerce(removed)
            }
        })

        globals.set("mapContainsKey", object : TwoArgFunction() {
            override fun call(mapObj: LuaValue, key: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(Map::class.java) as Map<*, *>
                val k = CoerceLuaToJava.coerce(key, Any::class.java)
                return LuaValue.valueOf(map.containsKey(k))
            }
        })

        globals.set("mapContainsValue", object : TwoArgFunction() {
            override fun call(mapObj: LuaValue, value: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(Map::class.java) as Map<*, *>
                val v = CoerceLuaToJava.coerce(value, Any::class.java)
                return LuaValue.valueOf(map.containsValue(v))
            }
        })

        globals.set("mapKeys", object : OneArgFunction() {
            override fun call(mapObj: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(Map::class.java) as Map<*, *>
                val table = LuaTable()
                var idx = 1
                for (key in map.keys) {
                    table.set(idx, CoerceJavaToLua.coerce(key))
                    idx++
                }
                return table
            }
        })

        globals.set("mapValues", object : OneArgFunction() {
            override fun call(mapObj: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(Map::class.java) as Map<*, *>
                val table = LuaTable()
                var idx = 1
                for (value in map.values) {
                    table.set(idx, CoerceJavaToLua.coerce(value))
                    idx++
                }
                return table
            }
        })

        globals.set("mapSize", object : OneArgFunction() {
            override fun call(mapObj: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(Map::class.java) as Map<*, *>
                return LuaValue.valueOf(map.size)
            }
        })

        globals.set("mapIsEmpty", object : OneArgFunction() {
            override fun call(mapObj: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(Map::class.java) as Map<*, *>
                return LuaValue.valueOf(map.isEmpty())
            }
        })

        globals.set("mapClear", object : OneArgFunction() {
            override fun call(mapObj: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(MutableMap::class.java) as MutableMap<*, *>
                map.clear()
                return LuaValue.NIL
            }
        })

        globals.set("mapToTable", object : OneArgFunction() {
            override fun call(mapObj: LuaValue): LuaValue {
                val map = mapObj.checkuserdata(Map::class.java) as Map<*, *>
                return javaMapToLuaTable(map)
            }
        })
    }

    private fun registerSetHelpers(globals: LuaValue) {
        globals.set("newHashSet", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val set = HashSet<Any>()
                for (i in 1..args.narg()) {
                    set.add(CoerceLuaToJava.coerce(args.arg(i), Any::class.java))
                }
                return CoerceJavaToLua.coerce(set)
            }
        })

        globals.set("setAdd", object : TwoArgFunction() {
            override fun call(setObj: LuaValue, value: LuaValue): LuaValue {
                val set = setObj.checkuserdata(MutableSet::class.java) as MutableSet<Any?>
                val javaValue = CoerceLuaToJava.coerce(value, Any::class.java)
                return LuaValue.valueOf(set.add(javaValue))
            }
        })

        globals.set("setRemove", object : TwoArgFunction() {
            override fun call(setObj: LuaValue, value: LuaValue): LuaValue {
                val set = setObj.checkuserdata(MutableSet::class.java) as MutableSet<Any?>
                val javaValue = CoerceLuaToJava.coerce(value, Any::class.java)
                return LuaValue.valueOf(set.remove(javaValue))
            }
        })

        globals.set("setContains", object : TwoArgFunction() {
            override fun call(setObj: LuaValue, value: LuaValue): LuaValue {
                val set = setObj.checkuserdata(Set::class.java) as Set<*>
                val javaValue = CoerceLuaToJava.coerce(value, Any::class.java)
                return LuaValue.valueOf(set.contains(javaValue))
            }
        })

        globals.set("setSize", object : OneArgFunction() {
            override fun call(setObj: LuaValue): LuaValue {
                val set = setObj.checkuserdata(Set::class.java) as Set<*>
                return LuaValue.valueOf(set.size)
            }
        })

        globals.set("setToTable", object : OneArgFunction() {
            override fun call(setObj: LuaValue): LuaValue {
                val set = setObj.checkuserdata(Set::class.java) as Set<*>
                val table = LuaTable()
                var idx = 1
                for (item in set) {
                    table.set(idx, CoerceJavaToLua.coerce(item))
                    idx++
                }
                return table
            }
        })

        globals.set("setClear", object : OneArgFunction() {
            override fun call(setObj: LuaValue): LuaValue {
                val set = setObj.checkuserdata(MutableSet::class.java) as MutableSet<*>
                set.clear()
                return LuaValue.NIL
            }
        })
    }

    private fun registerOptionalHelpers(globals: LuaValue) {
        globals.set("optionalOf", object : OneArgFunction() {
            override fun call(value: LuaValue): LuaValue {
                val javaValue = CoerceLuaToJava.coerce(value, Any::class.java)
                return CoerceJavaToLua.coerce(Optional.ofNullable(javaValue))
            }
        })

        globals.set("optionalEmpty", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                return CoerceJavaToLua.coerce(Optional.empty<Any>())
            }
        })

        globals.set("optionalIsPresent", object : OneArgFunction() {
            override fun call(optObj: LuaValue): LuaValue {
                val opt = optObj.checkuserdata(Optional::class.java) as Optional<*>
                return LuaValue.valueOf(opt.isPresent)
            }
        })

        globals.set("optionalGet", object : OneArgFunction() {
            override fun call(optObj: LuaValue): LuaValue {
                val opt = optObj.checkuserdata(Optional::class.java) as Optional<*>
                return CoerceJavaToLua.coerce(opt.orElse(null))
            }
        })

        @Suppress("UNCHECKED_CAST")
        globals.set("optionalOrElse", object : TwoArgFunction() {
            override fun call(optObj: LuaValue, default: LuaValue): LuaValue {
                val opt = optObj.checkuserdata(Optional::class.java) as Optional<Any?>
                val javaDefault = CoerceLuaToJava.coerce(default, Any::class.java)
                return CoerceJavaToLua.coerce(opt.orElse(javaDefault))
            }
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerStreamHelpers(globals: LuaValue) {
        globals.set("streamToList", object : OneArgFunction() {
            override fun call(streamObj: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<*>
                val list = stream.collect(Collectors.toList())
                return CoerceJavaToLua.coerce(list)
            }
        })

        globals.set("streamToSet", object : OneArgFunction() {
            override fun call(streamObj: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<*>
                val set = stream.collect(Collectors.toSet())
                return CoerceJavaToLua.coerce(set)
            }
        })

        globals.set("streamMap", object : TwoArgFunction() {
            override fun call(streamObj: LuaValue, func: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                val luaFunc = func.checkfunction()
                val mapped = stream.map<Any?> { item ->
                    CoerceLuaToJava.coerce(luaFunc.call(CoerceJavaToLua.coerce(item)), Any::class.java)
                }
                return CoerceJavaToLua.coerce(mapped)
            }
        })

        globals.set("streamFilter", object : TwoArgFunction() {
            override fun call(streamObj: LuaValue, func: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                val luaFunc = func.checkfunction()
                val filtered = stream.filter { item ->
                    luaFunc.call(CoerceJavaToLua.coerce(item)).toboolean()
                }
                return CoerceJavaToLua.coerce(filtered)
            }
        })

        globals.set("streamForEach", object : TwoArgFunction() {
            override fun call(streamObj: LuaValue, func: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                val luaFunc = func.checkfunction()
                stream.forEach { item -> luaFunc.call(CoerceJavaToLua.coerce(item)) }
                return LuaValue.NIL
            }
        })

        globals.set("streamCount", object : OneArgFunction() {
            override fun call(streamObj: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<*>
                val count = stream.count()
                return LuaValue.valueOf(count.toInt())
            }
        })

        @Suppress("UNCHECKED_CAST")
        globals.set("streamCollect", object : TwoArgFunction() {
            override fun call(streamObj: LuaValue, collectorObj: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                val collector = collectorObj.checkuserdata(Collector::class.java) as Collector<Any?, Any?, Any?>
                val result = stream.collect(collector)
                return CoerceJavaToLua.coerce(result)
            }
        })

        globals.set("streamDistinct", object : OneArgFunction() {
            override fun call(streamObj: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                return CoerceJavaToLua.coerce(stream.distinct())
            }
        })

        globals.set("streamSorted", object : OneArgFunction() {
            override fun call(streamObj: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                return CoerceJavaToLua.coerce(stream.sorted())
            }
        })

        globals.set("streamLimit", object : TwoArgFunction() {
            override fun call(streamObj: LuaValue, max: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                return CoerceJavaToLua.coerce(stream.limit(max.tolong()))
            }
        })

        globals.set("streamSkip", object : TwoArgFunction() {
            override fun call(streamObj: LuaValue, n: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                return CoerceJavaToLua.coerce(stream.skip(n.tolong()))
            }
        })

        globals.set("streamFindFirst", object : OneArgFunction() {
            override fun call(streamObj: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                val opt = stream.findFirst()
                return CoerceJavaToLua.coerce(opt.orElse(null))
            }
        })

        globals.set("streamAnyMatch", object : TwoArgFunction() {
            override fun call(streamObj: LuaValue, func: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                val luaFunc = func.checkfunction()
                return LuaValue.valueOf(stream.anyMatch { item ->
                    luaFunc.call(CoerceJavaToLua.coerce(item)).toboolean()
                })
            }
        })

        globals.set("streamAllMatch", object : TwoArgFunction() {
            override fun call(streamObj: LuaValue, func: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                val luaFunc = func.checkfunction()
                return LuaValue.valueOf(stream.allMatch { item ->
                    luaFunc.call(CoerceJavaToLua.coerce(item)).toboolean()
                })
            }
        })

        globals.set("streamNoneMatch", object : TwoArgFunction() {
            override fun call(streamObj: LuaValue, func: LuaValue): LuaValue {
                val stream = streamObj.checkuserdata(Stream::class.java) as Stream<Any?>
                val luaFunc = func.checkfunction()
                return LuaValue.valueOf(stream.noneMatch { item ->
                    luaFunc.call(CoerceJavaToLua.coerce(item)).toboolean()
                })
            }
        })

        globals.set("collectorsToList", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                return CoerceJavaToLua.coerce(Collectors.toList<Any?>())
            }
        })

        globals.set("collectorsToSet", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                return CoerceJavaToLua.coerce(Collectors.toSet<Any?>())
            }
        })

        globals.set("collectorsJoining", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val delimiter = if (args.narg() >= 1) args.checkjstring(1) else ""
                val prefix = if (args.narg() >= 2) args.checkjstring(2) else ""
                val suffix = if (args.narg() >= 3) args.checkjstring(3) else ""
                return CoerceJavaToLua.coerce(Collectors.joining(delimiter, prefix, suffix))
            }
        })

        globals.set("collectorsToMap", object : TwoArgFunction() {
            override fun call(keyMapper: LuaValue, valueMapper: LuaValue): LuaValue {
                val keyFunc = keyMapper.checkfunction()
                val valFunc = valueMapper.checkfunction()
                val collector = Collectors.toMap<Any?, Any?, Any?>(
                    { k -> CoerceLuaToJava.coerce(keyFunc.call(CoerceJavaToLua.coerce(k)), Any::class.java) },
                    { v -> CoerceLuaToJava.coerce(valFunc.call(CoerceJavaToLua.coerce(v)), Any::class.java) }
                )
                return CoerceJavaToLua.coerce(collector)
            }
        })

        globals.set("collectorsGroupingBy", object : OneArgFunction() {
            override fun call(classifier: LuaValue): LuaValue {
                val func = classifier.checkfunction()
                val collector = Collectors.groupingBy<Any?, Any?> { k ->
                    CoerceLuaToJava.coerce(func.call(CoerceJavaToLua.coerce(k)), Any::class.java)
                }
                return CoerceJavaToLua.coerce(collector)
            }
        })
    }

    private fun registerConversionHelpers(globals: LuaValue) {
        globals.set("asList", object : OneArgFunction() {
            override fun call(tableObj: LuaValue): LuaValue {
                val table = tableObj.checktable()
                val list = ArrayList<Any>()
                var i = 1
                while (true) {
                    val value = table.get(i)
                    if (value.isnil()) break
                    list.add(CoerceLuaToJava.coerce(value, Any::class.java))
                    i++
                }
                if (list.isEmpty()) {
                    var k = LuaValue.NIL
                    while (true) {
                        val n = table.next(k)
                        k = n.arg1()
                        if (k.isnil()) break
                        if (!k.isint()) {
                            list.add(CoerceLuaToJava.coerce(k, Any::class.java))
                        }
                    }
                }
                return CoerceJavaToLua.coerce(list)
            }
        })

        globals.set("asMap", object : OneArgFunction() {
            override fun call(tableObj: LuaValue): LuaValue {
                val table = tableObj.checktable()
                val map = LinkedHashMap<Any?, Any?>()
                var k = LuaValue.NIL
                while (true) {
                    val n = table.next(k)
                    k = n.arg1()
                    if (k.isnil()) break
                    if (k.isstring()) {
                        map[k.tojstring()] = CoerceLuaToJava.coerce(n.arg(2), Any::class.java)
                    }
                }
                return CoerceJavaToLua.coerce(map)
            }
        })

        globals.set("asSet", object : OneArgFunction() {
            override fun call(tableObj: LuaValue): LuaValue {
                val table = tableObj.checktable()
                val set = LinkedHashSet<Any>()
                var i = 1
                while (true) {
                    val value = table.get(i)
                    if (value.isnil()) break
                    set.add(CoerceLuaToJava.coerce(value, Any::class.java))
                    i++
                }
                return CoerceJavaToLua.coerce(set)
            }
        })

        globals.set("toTable", object : OneArgFunction() {
            override fun call(collectionObj: LuaValue): LuaValue {
                val obj = collectionObj.checkuserdata(Any::class.java)
                return when (obj) {
                    is Map<*, *> -> javaMapToLuaTable(obj)
                    is Collection<*> -> {
                        val table = LuaTable()
                        var idx = 1
                        for (item in obj) {
                            table.set(idx, CoerceJavaToLua.coerce(item))
                            idx++
                        }
                        table
                    }
                    is Array<*> -> {
                        val table = LuaTable()
                        for ((i, item) in obj.withIndex()) {
                            table.set(i + 1, CoerceJavaToLua.coerce(item))
                        }
                        table
                    }
                    else -> {
                        val table = LuaTable()
                        table.set(1, CoerceJavaToLua.coerce(obj))
                        table
                    }
                }
            }
        })

        globals.set("toArray", object : TwoArgFunction() {
            override fun call(tableObj: LuaValue, componentType: LuaValue): LuaValue {
                val table = tableObj.checktable()
                val clazz = Class.forName(componentType.tojstring())
                val values = mutableListOf<Any?>()
                var i = 1
                while (true) {
                    val value = table.get(i)
                    if (value.isnil()) break
                    values.add(CoerceLuaToJava.coerce(value, clazz))
                    i++
                }
                val arr = java.lang.reflect.Array.newInstance(clazz, values.size)
                for ((idx, v) in values.withIndex()) {
                    java.lang.reflect.Array.set(arr, idx, v)
                }
                return CoerceJavaToLua.coerce(arr)
            }
        })

        globals.set("iteratorToTable", object : OneArgFunction() {
            override fun call(iterObj: LuaValue): LuaValue {
                val iter = iterObj.checkuserdata(Iterator::class.java) as Iterator<*>
                val table = LuaTable()
                var idx = 1
                while (iter.hasNext()) {
                    table.set(idx, CoerceJavaToLua.coerce(iter.next()))
                    idx++
                }
                return table
            }
        })

        globals.set("enumerationToTable", object : OneArgFunction() {
            override fun call(enumObj: LuaValue): LuaValue {
                val enumeration = enumObj.checkuserdata(Enumeration::class.java) as Enumeration<*>
                val table = LuaTable()
                var idx = 1
                while (enumeration.hasMoreElements()) {
                    table.set(idx, CoerceJavaToLua.coerce(enumeration.nextElement()))
                    idx++
                }
                return table
            }
        })
    }

    companion object {
        fun javaMapToLuaTable(map: Map<*, *>): LuaTable {
            val table = LuaTable()
            for ((key, value) in map) {
                val luaKey = if (key is String) LuaValue.valueOf(key)
                else CoerceJavaToLua.coerce(key)
                table.set(luaKey, CoerceJavaToLua.coerce(value))
            }
            return table
        }
    }
}
