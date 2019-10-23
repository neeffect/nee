package pl.setblack.nee.effects.monitoring

import io.vavr.collection.List
import io.vavr.control.Either
import pl.setblack.nee.Effect
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class TraceEffect<R : TraceProvider<R>>(private val tracerName: String) : Effect<R, Nothing> {
    override fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Either<Nothing, A>, R> = { r: R ->
        val entry = r.getTrace().begin(tracerName)
        val traced = r.setTrace(entry.first)
        Pair({ p: P ->
            val r1 = f(traced)
            val result = r1(p)
            entry.second.name.updateAndGet { name ->
                name ?: r1.javaClass.name
            }
            val ended = traced.getTrace().end(tracerName, entry.second)
            Either.right<Nothing, A>(result)
        }, r)
    }
}

interface TraceProvider<G : TraceProvider<G>> {
    fun getTrace(): TraceResource
    fun setTrace(newState: TraceResource): G
}

class TraceResource(
    val resName: String,
    val logger: Logger,
    val nanoTime: NanoTime = { System.nanoTime() },
    val traces: List<TraceEntry> = List.empty<TraceEntry>()
) {

    internal inline fun begin(tracerName: String): Pair<TraceResource, TraceEntry> =
        TraceEntry(tracerName, generateUUID(), nanoTime()).let { traceEntry ->
            Pair(
                TraceResource(
                    this.resName,
                    logger.log(traceEntry, "started"),
                    this.nanoTime,
                    traces.prepend(traceEntry)
                )
                , traceEntry
            )

        }


    private fun generateUUID(): UUID = UUID.randomUUID()

    private fun lastTrace() = this.traces.headOption()

    internal inline fun monitor() =
        this.traces.headOption().forEach {
            it.name.compareAndSet(null, placeName())
        }

    internal inline fun guessPlace( f: Any) =
        this.traces.headOption().forEach {
            it.name.compareAndSet(null, f.toString() )
        }

    internal inline fun end(tracerName: String, first: TraceEntry): TraceResource = lastTrace().let {
        it.map { traceEntry ->
            val totalTime = nanoTime()
            val diff = totalTime - traceEntry.time

            TraceResource(
                this.resName,
                logger.log(traceEntry, "ended after $diff ns"),
                this.nanoTime,
                traces.pop()
            )
        }.getOrElse {
            logger.log(TraceEntry(tracerName, UUID(0, 0), nanoTime()), "unpaired end at: ${placeName(2)}")
            this
        }
    }


}

inline fun placeName(idx: Int = 1): String {
    val stackTrace = Thread.currentThread().stackTrace
    return if (stackTrace.size > idx) {
        val st = stackTrace[idx]
        "${st.moduleName} ${st.fileName} ${st.lineNumber} ${st.methodName}"
    } else {
        "<unknown place>"
    }
}

interface Logger {
    fun log(entry: TraceEntry, msg: String): Logger
}

data class TraceEntry(
    val tracerName: String,
    val uuid: UUID,
    val time: Long,
    val name: AtomicReference<String?> = AtomicReference()
)


typealias NanoTime = () -> Long

class X {
    fun a() {
        println(placeName())
    }
}

fun main() {
    X().a()
    val z = X()::a
    println(z.name)
}