package com.repforth.core.exercisedata

import com.repforth.core.database.dao.ExerciseDao
import com.repforth.core.database.dao.ExerciseSummaryRow
import com.repforth.core.database.dao.ExerciseWithDetails
import com.repforth.core.model.ExerciseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolving a plan's exercise names.
 *
 * A plan stores ids and nothing else, so drawing one is a batch lookup. The
 * parts worth testing are the ones that only show up at the edges: an empty
 * plan must not reach SQLite at all (`IN ()` is a syntax error), a long list
 * must not exceed SQLite's parameter ceiling, and an id the catalog no longer
 * has must be visibly absent rather than silently shortening the result.
 */
class BatchSummaryTest {

    private class RecordingDao(private val known: Set<String>) : ExerciseDao {
        val calls = mutableListOf<List<String>>()

        override suspend fun count(): Int = known.size

        override suspend fun findById(id: String): ExerciseWithDetails? = null

        override fun observePage(limit: Int, offset: Int): Flow<List<ExerciseWithDetails>> =
            emptyFlow()

        override fun observeCatalog(
            query: String,
            bodyPart: String?,
            equipment: String?,
            muscles: List<String>,
            ignoreMuscles: Boolean,
        ): Flow<List<ExerciseSummaryRow>> = emptyFlow()

        override suspend fun summariesFor(ids: List<String>): List<ExerciseSummaryRow> {
            calls += ids
            return ids.filter { it in known }.map { id ->
                ExerciseSummaryRow(
                    id = id,
                    name = "Exercise $id",
                    bodyPart = "chest",
                    target = "pectorals",
                    equipment = "barbell",
                )
            }
        }
    }

    private fun repository(known: Set<String>) = RecordingDao(known)

    @Test
    fun `an empty plan never reaches the database`() = runTest {
        val dao = repository(emptySet())

        val result = RoomExerciseRepository(dao).summaries(emptyList())

        assertTrue(result.isEmpty())
        assertTrue("`IN ()` is a SQL syntax error, so this must not query", dao.calls.isEmpty())
    }

    @Test
    fun `ids are resolved and keyed by id`() = runTest {
        val dao = repository(setOf("a", "b"))

        val result = RoomExerciseRepository(dao)
            .summaries(listOf(ExerciseId("a"), ExerciseId("b")))

        assertEquals(setOf(ExerciseId("a"), ExerciseId("b")), result.keys)
        assertEquals("Exercise a", result[ExerciseId("a")]?.name)
    }

    /**
     * A dataset update can remove an exercise a saved plan still references.
     * The map makes that visible; a list would just come back one shorter.
     */
    @Test
    fun `an id the catalog no longer has is absent rather than substituted`() = runTest {
        val dao = repository(setOf("a"))

        val result = RoomExerciseRepository(dao)
            .summaries(listOf(ExerciseId("a"), ExerciseId("gone")))

        assertEquals(setOf(ExerciseId("a")), result.keys)
        assertTrue(ExerciseId("gone") !in result)
    }

    @Test
    fun `duplicate ids are asked for once`() = runTest {
        val dao = repository(setOf("a"))

        RoomExerciseRepository(dao)
            .summaries(listOf(ExerciseId("a"), ExerciseId("a"), ExerciseId("a")))

        assertEquals(listOf("a"), dao.calls.single())
    }

    /**
     * SQLite's default ceiling is 999 host parameters. A hand-built plan will
     * never approach that; an import might, and failing at a threshold nobody
     * tested is the kind of bug that only appears in someone else's data.
     */
    @Test
    fun `a long list is split into statements sqlite will accept`() = runTest {
        val ids = (1..1_200).map { ExerciseId("id-$it") }
        val dao = repository(ids.map { it.value }.toSet())

        val result = RoomExerciseRepository(dao).summaries(ids)

        assertEquals(1_200, result.size)
        assertTrue("Expected more than one statement", dao.calls.size > 1)
        assertTrue(
            "Every statement must stay under SQLite's parameter ceiling",
            dao.calls.all { it.size <= 999 },
        )
    }
}
