package com.repforth.core.userdata

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.database.dao.TemplateDao
import com.repforth.core.database.dao.TemplateWithExercises
import com.repforth.core.database.entity.TemplateExerciseEntity
import com.repforth.core.database.entity.WorkoutTemplateEntity
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomTemplateRepositoryTest {

    private lateinit var fakeDao: FakeTemplateDao
    private lateinit var time: FakeTimeSource
    private lateinit var repository: RoomTemplateRepository

    @Before
    fun setUp() {
        fakeDao = FakeTemplateDao()
        time = FakeTimeSource(1_000_000L)
        repository = RoomTemplateRepository(fakeDao, time)
    }

    private fun sampleTemplate(id: String, name: String) = WorkoutTemplate(
        id = id,
        name = name,
        source = PlanSource.MANUAL,
        exercises = listOf(
            PlannedExercise(
                id = "pe-1",
                exerciseId = ExerciseId("ex-1"),
                position = 0,
                target = ExerciseTarget.Reps(sets = 3, reps = 10, weightKg = 50.0),
                restMs = 60_000L,
            ),
        ),
    )

    @Test
    fun `saving and finding a template preserves domain fields`() = runTest {
        val template = sampleTemplate("tmpl-1", "Chest & Triceps")
        repository.save(template)

        val found = repository.find("tmpl-1")
        assertNotNull(found)
        assertEquals("Chest & Triceps", found!!.name)
        assertEquals(1, found.exercises.size)
        assertEquals(ExerciseId("ex-1"), found.exercises[0].exerciseId)
        assertEquals(ExerciseTarget.Reps(3, 10, 50.0), found.exercises[0].target)
    }

    /**
     * Saving a day of a week must not take it out of the week.
     *
     * `WorkoutTemplate` is the standalone-plan shape and carries nothing about
     * weeks, while `replaceTemplate` upserts with REPLACE — so an entity built
     * from the domain type alone reset `week_id`, `week_position` and
     * `day_of_week` to null. Editing one day of a saved week and saving it would
     * have removed that day from the week and left it loose in the library,
     * with no way back. Found while making a week's day rows tappable in Plans,
     * which is what would have fired it.
     */
    @Test
    fun `saving a day of a week keeps it in that week`() = runTest {
        fakeDao.upsertTemplate(
            WorkoutTemplateEntity(
                id = "day-1",
                name = "Push",
                notes = null,
                source = PlanSource.AI.name,
                weekId = "week-1",
                weekPosition = 2,
                dayOfWeek = 3,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        repository.save(sampleTemplate("day-1", "Push, edited"))

        val stored = fakeDao.findById("day-1")!!.template
        assertEquals("Push, edited", stored.name)
        assertEquals("week-1", stored.weekId)
        assertEquals(2, stored.weekPosition)
        assertEquals(3, stored.dayOfWeek)
        assertTrue(
            "A day of a week must not appear in the standalone plan list",
            repository.observeAll().first().none { it.id == "day-1" },
        )
    }

    /** A plan that was never in a week does not acquire one by being saved. */
    @Test
    fun `saving a standalone plan leaves it standalone`() = runTest {
        repository.save(sampleTemplate("tmpl-1", "Chest"))
        repository.save(sampleTemplate("tmpl-1", "Chest, edited"))

        val stored = fakeDao.findById("tmpl-1")!!.template
        assertNull(stored.weekId)
        assertNull(stored.weekPosition)
        assertNull(stored.dayOfWeek)
    }

    @Test
    fun `observeAll returns standalone templates`() = runTest {
        repository.save(sampleTemplate("tmpl-1", "Chest"))
        repository.save(sampleTemplate("tmpl-2", "Back"))

        val all = repository.observeAll().first()
        assertEquals(2, all.size)
    }

    @Test
    fun `delete removes the template`() = runTest {
        repository.save(sampleTemplate("tmpl-1", "Chest"))
        repository.delete("tmpl-1")

        assertNull(repository.find("tmpl-1"))
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `deleteAll clears all templates`() = runTest {
        repository.save(sampleTemplate("tmpl-1", "Chest"))
        repository.save(sampleTemplate("tmpl-2", "Back"))
        repository.deleteAll()

        assertTrue(repository.observeAll().first().isEmpty())
    }
}

private class FakeTemplateDao : TemplateDao {
    private val templates = mutableMapOf<String, WorkoutTemplateEntity>()
    private val exercises = mutableMapOf<String, MutableList<TemplateExerciseEntity>>()

    private val flow = MutableStateFlow<List<TemplateWithExercises>>(emptyList())

    private fun refresh() {
        // Models SQL: WHERE week_id IS NULL ORDER BY updated_at DESC
        flow.value = templates.values
            .filter { it.weekId == null }
            .sortedByDescending { it.updatedAt }
            .map { TemplateWithExercises(it, exercises[it.id].orEmpty()) }
    }

    override fun observeAll(): Flow<List<TemplateWithExercises>> = flow

    override suspend fun findById(id: String): TemplateWithExercises? {
        val t = templates[id] ?: return null
        return TemplateWithExercises(t, exercises[id].orEmpty())
    }

    override suspend fun replaceTemplate(
        template: WorkoutTemplateEntity,
        exercises: List<TemplateExerciseEntity>,
    ) {
        upsertTemplate(template)
        deleteExercisesFor(template.id)
        insertExercises(exercises)
    }

    override suspend fun upsertTemplate(template: WorkoutTemplateEntity) {
        templates[template.id] = template
        refresh()
    }

    override suspend fun insertExercises(rows: List<TemplateExerciseEntity>) {
        rows.forEach { r ->
            val list = exercises.getOrPut(r.templateId) { mutableListOf() }
            list.removeAll { it.id == r.id }
            list += r
        }
        refresh()
    }

    override suspend fun deleteExercisesFor(templateId: String) {
        exercises.remove(templateId)
        refresh()
    }

    override suspend fun delete(id: String) {
        exercises.remove(id)
        templates.remove(id)
        refresh()
    }

    override suspend fun deleteAll() {
        exercises.clear()
        templates.clear()
        refresh()
    }

    override suspend fun count(): Int = templates.size
}
