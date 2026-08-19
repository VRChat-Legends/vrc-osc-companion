package com.vrchatlegends.osccompanion.scripts

import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.ResourceFinder
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.JseBaseLib
import org.luaj.vm2.lib.jse.JseMathLib

/** Raised inside the interpreter to unwind a script that must stop now. */
class ScriptStopped(message: String) : RuntimeException(message)

/**
 * Everything a script may do to the outside world. Implementations own all
 * rate limits, connection checks, and value validation; the sandbox owns the
 * language-level isolation.
 */
interface LuaHostApi {
    fun chatbox(text: String)
    fun setParameter(name: String, rawValue: String)
    fun sleep(ms: Long)
    fun log(line: String)
    fun elapsedMs(): Long
}

/**
 * A Lua 5.2 interpreter with every escape hatch removed. No io, os, require,
 * package, luajava, coroutine, or debug access: the only reachable side
 * effects are the [LuaHostApi] functions. A per-instruction hook enforces the
 * cancellation flag and an instruction budget, so `while true do end` dies in
 * milliseconds instead of hanging the runner.
 */
class LuaSandbox(
    private val host: LuaHostApi,
    private val cancelled: () -> Boolean,
    private val maxInstructions: Long = MAX_INSTRUCTIONS,
) {
    private var instructions = 0L

    fun run(source: String): Result<Unit> = try {
        val globals = buildGlobals()
        val chunk = globals.load(source, "script")
        chunk.call()
        Result.success(Unit)
    } catch (stopped: ScriptStopped) {
        Result.failure(stopped)
    } catch (error: LuaError) {
        val stop = generateSequence<Throwable>(error) { it.cause }.firstOrNull { it is ScriptStopped }
        Result.failure(stop ?: IllegalStateException(luaErrorMessage(error)))
    } catch (error: Throwable) {
        Result.failure(IllegalStateException("The script failed: ${error.message ?: "unknown error"}"))
    }

    private fun buildGlobals(): Globals {
        val globals = Globals()
        // luaj's library installers unconditionally register themselves in
        // package.loaded even when PackageLib is not installed, and crash with
        // "attempt to index ?" without this stub. It is nil'd again below.
        val packageStub = LuaTable()
        packageStub.set("loaded", LuaTable())
        globals.set("package", packageStub)
        globals.load(JseBaseLib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(JseMathLib())
        globals.load(Bit32Lib())
        LoadState.install(globals)
        LuaC.install(globals)
        // The debug library object stays installed so onInstruction fires, but
        // the script never gets a handle to it.
        globals.load(BudgetDebugLib())

        // JseBaseLib can load Lua files off the classpath through this finder.
        globals.finder = ResourceFinder { null }
        for (name in ESCAPE_HATCHES) globals.set(name, LuaValue.NIL)

        val print = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val parts = (1..args.narg()).joinToString("\t") { args.arg(it).tojstring() }
                host.log(parts)
                return NONE
            }
        }
        globals.set("print", print)

        val vrc = LuaTable()
        vrc.set("chatbox", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                host.chatbox(args.checkjstring(1))
                return TRUE
            }
        })
        vrc.set("param", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val name = args.checkjstring(1)
                val value = args.arg(2)
                val raw = when {
                    value.isboolean() -> value.toboolean().toString()
                    value.isnumber() -> formatNumber(value.todouble())
                    value.isstring() -> value.tojstring()
                    else -> throw ScriptStopped("vrc.param values must be a number, true, or false.")
                }
                host.setParameter(name, raw)
                return TRUE
            }
        })
        val wait = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                host.sleep(args.checklong(1))
                return NONE
            }
        }
        vrc.set("wait", wait)
        globals.set("wait", wait)
        vrc.set("log", print)
        vrc.set("elapsed", object : ZeroArgFunction() {
            override fun call(): LuaValue = valueOf(host.elapsedMs().toDouble())
        })
        globals.set("vrc", vrc)
        return globals
    }

    /** Whole numbers must not pick up a .0 suffix, VRChat int parameters reject "3.0". */
    private fun formatNumber(value: Double): String =
        if (value.isFinite() && value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private fun luaErrorMessage(error: LuaError): String {
        val message = error.message ?: "unknown error"
        return "The script failed: $message"
    }

    private inner class BudgetDebugLib : DebugLib() {
        override fun onInstruction(pc: Int, v: Varargs, top: Int) {
            if (cancelled()) throw ScriptStopped("Script stopped")
            if (++instructions > maxInstructions) {
                throw ScriptStopped("The script did too much work and was stopped.")
            }
            super.onInstruction(pc, v, top)
        }
    }

    companion object {
        const val MAX_INSTRUCTIONS = 4_000_000L

        /** Globals JseBaseLib ships that could reach outside the sandbox. */
        private val ESCAPE_HATCHES = listOf(
            "dofile", "loadfile", "require", "package", "io", "os",
            "luajava", "debug", "collectgarbage", "coroutine",
        )
    }
}
