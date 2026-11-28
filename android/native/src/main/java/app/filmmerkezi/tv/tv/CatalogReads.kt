package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.core.CatalogVisit
import app.filmmerkezi.tv.core.UnifiedPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/** The Activity owns the first-page reads; replacing a screen only replaces its observer. */
internal class CatalogReads(private val scope: CoroutineScope) {
    private data class Result(
        val page: UnifiedPage? = null,
        val done: Boolean = false,
        val error: Throwable? = null,
    )

    private class Read(val visit: CatalogVisit) {
        val result = MutableStateFlow(Result())
        lateinit var job: Job
    }

    private val reads = LinkedHashMap<String, Read>(12, 0.75f, true)
    private var visible = emptySet<String>()

    fun prioritize(keys: Set<String>) {
        visible = keys
        reads.forEach { (key, read) -> read.visit.visible = key in visible }
    }

    fun observe(key: String, load: () -> Flow<UnifiedPage>): Flow<UnifiedPage> = flow {
        val read = reads[key] ?: Read(CatalogVisit(key in visible)).also { entry ->
            reads[key] = entry
            entry.job = scope.launch(entry.visit, start = CoroutineStart.LAZY) {
                try {
                    load().collect { entry.result.value = Result(page = it) }
                    entry.result.value = entry.result.value.copy(done = true)
                } catch (e: CancellationException) {
                    entry.result.value = Result(done = true, error = e)
                    throw e
                } catch (e: Exception) {
                    entry.result.value = Result(done = true, error = e)
                }
            }
            trimTo(12)
            entry.job.start()
        }
        read.result.transformWhile { state ->
            state.error?.let { throw it }
            state.page?.let { emit(it) }
            !state.done
        }.collect { emit(it) }
    }

    fun invalidate(keys: Set<String>) {
        keys.forEach { reads.remove(it)?.job?.cancel() }
    }

    fun cancelActive(): List<Job> {
        val active = reads.filterValues { !it.result.value.done }
        invalidate(active.keys)
        return active.values.map { it.job }
    }

    fun clear() = invalidate(reads.keys.toSet())

    fun trimTo(count: Int) {
        reads.entries.filter { it.value.result.value.done && it.key !in visible }
            .take((reads.size - count).coerceAtLeast(0)).forEach { reads.remove(it.key) }
    }
}
