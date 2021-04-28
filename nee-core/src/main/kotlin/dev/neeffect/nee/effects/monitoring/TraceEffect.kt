package dev.neeffect.nee.effects.monitoring

import dev.neeffect.nee.Effect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.monitoring.CodeNameFinder.guessCodePlaceName
import io.vavr.collection.List
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class TraceEffect<R : TraceProvider<R>>(private val tracerName: String) : Effect<R, Nothing> {
    override fun <A> wrap(f: (R) -> A): (R) -> Pair<Out<Nothing, A>, R> = { r: R ->
        val entry = r.getTrace().begin(tracerName)
        val traced = r.setTrace(entry.first)
        Pair(run {
            val result = f(traced)
            entry.second.codeLocation.updateAndGet { location ->
                location ?: CodeLocation(functionName = f::class.toString())
            }
            traced.getTrace().end(tracerName)
            Out.right<Nothing, A>(result)
        }, r)
    }
}

interface TraceProvider<G : TraceProvider<G>> {
    fun getTrace(): TraceResource
    fun setTrace(newState: TraceResource): G
}

class TraceResource(
    val resName: String,
    val logger: Logger<*>,
    val nanoTime: NanoTime = { System.nanoTime() },
    val traces: List<TraceEntry> = List.empty<TraceEntry>()
) {

    @Suppress("NOTHING_TO_INLINE")
    internal inline fun begin(tracerName: String): Pair<TraceResource, TraceEntry> =
        TraceEntry(tracerName, generateUUID(), nanoTime()).let { traceEntry ->

            Pair(
                TraceResource(
                    this.resName,
                    logger.log(traceEntry.toLogEntry(traceEntry.time, EntryType.Begin)),
                    this.nanoTime,
                    traces.prepend(traceEntry)
                ), traceEntry
            )
        }

    private fun generateUUID(): UUID = UUID.randomUUID()

    private fun lastTrace() = this.traces.headOption()

    @Suppress("NOTHING_TO_INLINE")
    inline fun putNamedPlace(name: CodeLocation = guessCodePlaceName()): Boolean =
        this.traces.headOption().map {
            it.codeLocation.compareAndSet(null, name)
        }.getOrElse(false)

    @Suppress("NOTHING_TO_INLINE")
    internal inline fun putGuessedPlace(placeName: CodeLocation, f: Any) =
        this.traces.headOption().map {
            it.codeLocation.compareAndSet(
                null,
                placeName.copy(customInfo = f.toString())
            )
        }.getOrElse(false)

    @Suppress("NOTHING_TO_INLINE")
    internal inline fun end(tracerName: String): TraceResource = lastTrace().let {
        it.map { traceEntry ->
            val totalTime = nanoTime()
            val diff = totalTime - traceEntry.time

            TraceResource(
                this.resName,
                logger.log(traceEntry.toLogEntry(totalTime, EntryType.End(diff))),
                this.nanoTime,
                traces.pop()
            )
        }.getOrElse {
            logger.log(
                LogEntry(
                    tracerName,
                    UUID(0, 0), nanoTime(),
                    guessCodePlaceName(2),
                    EntryType.InternalError("unpaired trace")
                )
            )
            this
        }
    }
}

object CodeNameFinder {
    @Suppress("NOTHING_TO_INLINE")
    inline fun guessCodePlaceName(suggestedStackPosition: Int = 3): CodeLocation =
        findBestStackMatchingCodePlaceName(suggestedStackPosition, Thread.currentThread().stackTrace)

    fun findBestStackMatchingCodePlaceName(
        suggestedStackPosition: Int,
        stackTraces: Array<StackTraceElement>
    ): CodeLocation =
        stackTraces.drop(1).take(maxStackSearch).mapIndexed { ind, el ->
            Pair(calcCost(ind, suggestedStackPosition, el), el)
        }.minByOrNull { it.first }?.let {
            codePointName(it.second)
        } ?: CodeLocation()

    private fun codePointName(st: StackTraceElement) =
        CodeLocation(
            className = st.className,
            functionName = st.methodName,
            fileName = st.fileName,
            lineNumber = st.lineNumber
        )

    private fun calcCost(index: Int, suggestedStackPosition: Int, element: StackTraceElement) =
        (index - suggestedStackPosition) * (index - suggestedStackPosition) + nameCost(element)

    private fun nameCost(element: StackTraceElement) =
        (if (element.className.contains(".nee.")) neeProjectClassesCost else 0) +
                (if (element.className.contains(".nee.effects.")) neeProjectClassesCost else 0) +
                (if (element.className.contains(".Nee")) neeProjectClassesCost else 0)

    private const val neeProjectClassesCost = 15
    private const val maxStackSearch = 10
}

interface Logger<T : Logger<T>> {
    fun log(entry: LogEntry): T
}

data class TraceEntry(
    val tracerName: String,
    val uuid: UUID,
    val time: Long,
    val codeLocation: AtomicReference<CodeLocation?> = AtomicReference()
) {
    fun getCodeLocation() = codeLocation.get() ?: CodeLocation()

    internal fun toLogEntry(newTime: Long = time, message: EntryType) =
        LogEntry(tracerName, uuid, newTime, getCodeLocation(), message)
}

data class LogEntry(
    val tracerName: String,
    val uuid: UUID,
    val time: Long,
    val codeLocation: CodeLocation,
    val message: EntryType
)

data class CodeLocation(
    val functionName: String? = null,
    val className: String? = null,
    val fileName: String? = null,
    val lineNumber: Int? = null,
    val customInfo: String? = null
) {
    override fun toString() =
        "${className()}->${functionName()}#${location()}#${customInfo()}"

    private fun className() = className ?: "?"
    private fun functionName() = functionName ?: "?"
    private fun customInfo(): String = customInfo ?: ""
    private fun location() = (fileName ?: "?") + "@" + (lineNumber?.toString() ?: "?")
}

typealias NanoTime = () -> Long
