package paper.plugin.minecraftX.bridge

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.luaj.vm2.*
import org.luaj.vm2.lib.*
import org.luaj.vm2.lib.jse.*
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock

class AsyncBridge(private val plugin: JavaPlugin) {

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "MinecraftX-Async-%d".format(Executors.defaultThreadFactory().hashCode())).also {
            it.isDaemon = true
        }
    }

    fun register(globals: LuaValue) {
        globals.set("async", object : OneArgFunction() {
            override fun call(func: LuaValue): LuaValue {
                val luaFunc = func.checkfunction()
                val future = CompletableFuture.supplyAsync(
                    java.util.function.Supplier<Any?> {
                        try {
                            val result = luaFunc.call()
                            CoerceJavaToLua.coerce(result)
                        } catch (e: Exception) {
                            throw CompletionException(e)
                        }
                    },
                    executor
                )
                return CoerceJavaToLua.coerce(future)
            }
        })

        globals.set("asyncWithDelay", object : TwoArgFunction() {
            override fun call(delayMs: LuaValue, func: LuaValue): LuaValue {
                val luaFunc = func.checkfunction()
                val delay = delayMs.tolong()
                val future = CompletableFuture.supplyAsync(
                    java.util.function.Supplier<Any?> {
                        try {
                            Thread.sleep(delay)
                            val result = luaFunc.call()
                            CoerceJavaToLua.coerce(result)
                        } catch (e: Exception) {
                            throw CompletionException(e)
                        }
                    },
                    executor
                )
                return CoerceJavaToLua.coerce(future)
            }
        })

        globals.set("runOnMainThread", object : OneArgFunction() {
            override fun call(func: LuaValue): LuaValue {
                val luaFunc = func.checkfunction()
                val future = CompletableFuture<Void>()
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        luaFunc.call()
                        future.complete(null)
                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                })
                return CoerceJavaToLua.coerce(future)
            }
        })

        globals.set("thenApply", object : TwoArgFunction() {
            override fun call(futureObj: LuaValue, func: LuaValue): LuaValue {
                val future = futureObj.checkuserdata(CompletableFuture::class.java) as CompletableFuture<*>
                val luaFunc = func.checkfunction()
                val newFuture = future.thenApply(
                    java.util.function.Function<Any?, Any?> { result ->
                        val luaResult = CoerceJavaToLua.coerce(result)
                        val transformed = luaFunc.call(luaResult)
                        CoerceLuaToJava.coerce(transformed, Any::class.java)
                    }
                )
                return CoerceJavaToLua.coerce(newFuture)
            }
        })

        globals.set("thenAccept", object : TwoArgFunction() {
            override fun call(futureObj: LuaValue, func: LuaValue): LuaValue {
                val future = futureObj.checkuserdata(CompletableFuture::class.java) as CompletableFuture<*>
                val luaFunc = func.checkfunction()
                val newFuture = future.thenAccept(
                    java.util.function.Consumer<Any?> { result ->
                        luaFunc.call(CoerceJavaToLua.coerce(result))
                    }
                )
                return CoerceJavaToLua.coerce(newFuture)
            }
        })

        globals.set("thenRun", object : TwoArgFunction() {
            override fun call(futureObj: LuaValue, func: LuaValue): LuaValue {
                val future = futureObj.checkuserdata(CompletableFuture::class.java) as CompletableFuture<*>
                val luaFunc = func.checkfunction()
                val newFuture = future.thenRun(Runnable { luaFunc.call() })
                return CoerceJavaToLua.coerce(newFuture)
            }
        })

        @Suppress("UNCHECKED_CAST")
        globals.set("exceptionHandler", object : TwoArgFunction() {
            override fun call(futureObj: LuaValue, func: LuaValue): LuaValue {
                val future = futureObj.checkuserdata(CompletableFuture::class.java) as CompletableFuture<Any?>
                val luaFunc = func.checkfunction()
                val newFuture = future.exceptionally { throwable ->
                    luaFunc.call(CoerceJavaToLua.coerce(throwable))
                    null
                }
                return CoerceJavaToLua.coerce(newFuture)
            }
        })

        globals.set("allOf", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val futures = mutableListOf<CompletableFuture<*>>()
                for (i in 1..args.narg()) {
                    val future = args.checkuserdata(i, CompletableFuture::class.java) as CompletableFuture<*>
                    futures.add(future)
                }
                val combined = CompletableFuture.allOf(*futures.toTypedArray())
                return CoerceJavaToLua.coerce(combined)
            }
        })

        globals.set("anyOf", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val futures = mutableListOf<CompletableFuture<*>>()
                for (i in 1..args.narg()) {
                    val future = args.checkuserdata(i, CompletableFuture::class.java) as CompletableFuture<*>
                    futures.add(future)
                }
                val combined = CompletableFuture.anyOf(*futures.toTypedArray())
                return CoerceJavaToLua.coerce(combined)
            }
        })

        globals.set("await", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val futureObj = args.checkuserdata(1, CompletableFuture::class.java) as CompletableFuture<*>
                val timeout = if (args.narg() >= 2) args.tolong(2) else 0L
                val result = if (timeout > 0) {
                    futureObj.get(timeout, TimeUnit.MILLISECONDS)
                } else {
                    futureObj.get()
                }
                return CoerceJavaToLua.coerce(result)
            }
        })

        globals.set("isDone", object : OneArgFunction() {
            override fun call(futureObj: LuaValue): LuaValue {
                val future = futureObj.checkuserdata(CompletableFuture::class.java) as CompletableFuture<*>
                return LuaValue.valueOf(future.isDone)
            }
        })

        globals.set("isCancelled", object : OneArgFunction() {
            override fun call(futureObj: LuaValue): LuaValue {
                val future = futureObj.checkuserdata(CompletableFuture::class.java) as CompletableFuture<*>
                return LuaValue.valueOf(future.isCancelled)
            }
        })

        globals.set("cancelFuture", object : OneArgFunction() {
            override fun call(futureObj: LuaValue): LuaValue {
                val future = futureObj.checkuserdata(CompletableFuture::class.java) as CompletableFuture<*>
                return LuaValue.valueOf(future.cancel(true))
            }
        })

        globals.set("newExecutor", object : OneArgFunction() {
            override fun call(name: LuaValue): LuaValue {
                val threadName = name.tojstring()
                val exec = Executors.newCachedThreadPool { r ->
                    Thread(r, "MinecraftX-Pool-$threadName").also { it.isDaemon = true }
                }
                return CoerceJavaToLua.coerce(exec)
            }
        })

        globals.set("newFixedPool", object : TwoArgFunction() {
            override fun call(name: LuaValue, threads: LuaValue): LuaValue {
                val threadName = name.tojstring()
                val nThreads = threads.checkint()
                val exec = Executors.newFixedThreadPool(nThreads) { r ->
                    Thread(r, "MinecraftX-Pool-$threadName").also { it.isDaemon = true }
                }
                return CoerceJavaToLua.coerce(exec)
            }
        })

        globals.set("submitToExecutor", object : TwoArgFunction() {
            override fun call(execObj: LuaValue, func: LuaValue): LuaValue {
                val exec = execObj.checkuserdata(ExecutorService::class.java) as ExecutorService
                val luaFunc = func.checkfunction()
                val future = exec.submit(Callable<LuaValue> { luaFunc.call() })
                return CoerceJavaToLua.coerce(future)
            }
        })

        globals.set("sleep", object : OneArgFunction() {
            override fun call(millis: LuaValue): LuaValue {
                return try {
                    Thread.sleep(millis.tolong())
                    LuaValue.TRUE
                } catch (e: InterruptedException) {
                    LuaValue.FALSE
                }
            }
        })

        globals.set("newAtomicInt", object : OneArgFunction() {
            override fun call(initial: LuaValue): LuaValue {
                return CoerceJavaToLua.coerce(java.util.concurrent.atomic.AtomicInteger(initial.checkint()))
            }
        })

        globals.set("newAtomicLong", object : OneArgFunction() {
            override fun call(initial: LuaValue): LuaValue {
                return CoerceJavaToLua.coerce(java.util.concurrent.atomic.AtomicLong(initial.tolong()))
            }
        })

        globals.set("newAtomicBool", object : OneArgFunction() {
            override fun call(initial: LuaValue): LuaValue {
                return CoerceJavaToLua.coerce(java.util.concurrent.atomic.AtomicBoolean(initial.toboolean()))
            }
        })

        globals.set("newCountDownLatch", object : OneArgFunction() {
            override fun call(count: LuaValue): LuaValue {
                return CoerceJavaToLua.coerce(CountDownLatch(count.checkint()))
            }
        })

        globals.set("newCyclicBarrier", object : OneArgFunction() {
            override fun call(parties: LuaValue): LuaValue {
                return CoerceJavaToLua.coerce(CyclicBarrier(parties.checkint()))
            }
        })

        globals.set("semaphore", object : OneArgFunction() {
            override fun call(permits: LuaValue): LuaValue {
                return CoerceJavaToLua.coerce(Semaphore(permits.checkint()))
            }
        })

        globals.set("lock", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                return CoerceJavaToLua.coerce(ReentrantLock())
            }
        })
    }
}
