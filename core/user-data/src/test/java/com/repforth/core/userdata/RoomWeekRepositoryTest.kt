package com.repforth.core.userdata

import com.repforth.core.common.time.FakeTimeSource
import com.repforth.core.database.dao.TemplateWithExercises
import com.repforth.core.database.dao.WeekDao
import com.repforth.core.database.dao.WeekWithDays
import com.repforth.core.database.entity.TemplateExerciseEntity
import com.repforth.core.database.entity.TrainingWeekEntity
import com.repforth.core.database.entity.WorkoutTemplateEntity
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.ExerciseTarget
import com.repforth.core.model.PlanSource
import com.repforth.core.model.PlannedExercise
import com.repforth.core.model.TrainingWeek
import com.repforth.core.model.WeekDay
import com.repforth.core.model.WorkoutTemplate
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomWeekRepositoryTest {

    private lateinit var fakeDao: FakeWeekDao
    private lateinit var time: FakeTimeSource
    private lateinit var repository: RoomWeekRepository

    @Before
    fun setUp() {
        fakeDao = FakeWeekDao()
        time = FakeTimeSource(1_000_000L)
        repository = RoomWeekRepository(fakeDao, time)
    }

    private fun sampleWeek(id: String, active: Boolean = false) = TrainingWeek(
        id = id,
        name = "Push Pull Legs",
        source = PlanSource.AI,
        active = active,
        days = listOf(
            WeekDay(
                position = 0,
                title = "Push Day",
                dayOfWeek = DayOfWeek.MONDAY,
                workout = WorkoutTemplate(
                    id = "tmpl-1",
                    name = "Push Day",
                    source = PlanSource.AI,
                    exercises = listOf(
                        PlannedExercise(
                            id = "pe-1",
                            exerciseId = ExerciseId("ex-bench"),
                            position = 0,
                            target = ExerciseTarget.Reps(sets = 3, reps = 8, weightKg = 80.0),
                            restMs = 90_000L,
                        ),
                    ),
                ),
            ),
            WeekDay(
                position = 1,
                title = "Pull Day",
                dayOfWeek = DayOfWeek.WEDNESDAY,
                workout = WorkoutTemplate(
                    id = "tmpl-2",
                    name = "Pull Day",
                    source = PlanSource.AI,
                    exercises = listOf(
                        PlannedExercise(
                            id = "pe-2",
                            exerciseId = ExerciseId("ex-row"),
                            position = 0,
                            target = ExerciseTarget.Reps(sets = 4, reps = 10, weightKg = 60.0),
                            restMs = 60_000L,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `saving and finding a week preserves all day templates and targets`() = runTest {
        val week = sampleWeek("week-1", active = true)
        repository.save(week)

        val found = repository.find("week-1")
        assertNotNull(found)
        assertEquals("Push Pull Legs", found!!.name)
        assertTrue(found.active)
        assertEquals(2, found.days.size)

        val day0 = found.days[0]
        assertEquals(0, day0.position)
        assertEquals(DayOfWeek.MONDAY, day0.dayOfWeek)
        assertEquals(1, day0.workout.exercises.size)
        val ex0 = day0.workout.exercises[0]
        assertEquals(ExerciseId("ex-bench"), ex0.exerciseId)
        assertEquals(ExerciseTarget.Reps(3, 8, 80.0), ex0.target)

        val day1 = found.days[1]
        assertEquals(1, day1.position)
        assertEquals(DayOfWeek.WEDNESDAY, day1.dayOfWeek)
        assertEquals(ExerciseId("ex-row"), day1.workout.exercises[0].exerciseId)
    }

    @Test
    fun `observing active week emits currently active week`() = runTest {
        val week1 = sampleWeek("week-1", active = true)
        repository.save(week1)

        val active = repository.observeActive().first()
        assertNotNull(active)
        assertEquals("week-1", active!!.id)

        val week2 = sampleWeek("week-2", active = true)
        repository.save(week2)

        val updatedActive = repository.observeActive().first()
        assertNotNull(updatedActive)
        assertEquals("week-2", updatedActive!!.id)
    }

    @Test
    fun `setActive activates target week and deactivates others`() = runTest {
        repository.save(sampleWeek("week-1", active = true))
        repository.save(sampleWeek("week-2", active = false))

        repository.setActive("week-2")

        val active = repository.observeActive().first()
        assertNotNull(active)
        assertEquals("week-2", active!!.id)

        val week1 = repository.find("week-1")
        assertNotNull(week1)
        assertEquals(false, week1!!.active)
    }

    @Test
    fun `delete removes week and cascades`() = runTest {
        repository.save(sampleWeek("week-1"))
        repository.delete("week-1")

        assertNull(repository.find("week-1"))
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `deleteAll removes all weeks`() = runTest {
        repository.save(sampleWeek("week-1"))
        repository.save(sampleWeek("week-2"))
        repository.deleteAll()

        assertTrue(repository.observeAll().first().isEmpty())
        assertNull(repository.observeActive().first())
    }
}

private class FakeWeekDao : WeekDao {
    private val weeks = mutableMapOf<String, TrainingWeekEntity>()
    private val templates = mutableMapOf<String, MutableList<WorkoutTemplateEntity>>()
    private val exercises = mutableMapOf<String, MutableList<TemplateExerciseEntity>>()

    private val flow = MutableStateFlow<List<WeekWithDays>>(emptyList())

    private fun refresh() {
        flow.value = weeks.values.sortedByDescending { it.updatedAt }.map { w ->
            val tmpls = templates[w.id].orEmpty().map { t ->
                TemplateWithExercises(t, exercises[t.id].orEmpty())
            }
            WeekWithDays(w, tmpls)
        }
    }

    override fun observeAll(): Flow<List<WeekWithDays>> = flow

    override fun observeActive(): Flow<WeekWithDays?> =
        flow.map { list -> list.firstOrNull { it.week.active } }

    override suspend fun findById(id: String): WeekWithDays? {
        val w = weeks[id] ?: return null
        val tmpls = templates[id].orEmpty().map { t ->
            TemplateWithExercises(t, exercises[t.id].orEmpty())
        }
        return WeekWithDays(w, tmpls)
    }

    override suspend fun replaceWeek(
        week: TrainingWeekEntity,
        templates: List<WorkoutTemplateEntity>,
        exercises: List<TemplateExerciseEntity>,
    ) {
        if (week.active) {
            clearActive()
        }
        upsertWeek(week)
        deleteTemplatesForWeek(week.id)
        insertTemplates(templates)
        insertExercises(exercises)
    }

    override suspend fun setActive(id: String) {
        clearActive()
        setWeekActive(id)
    }

    override suspend fun upsertWeek(week: TrainingWeekEntity) {
        weeks[week.id] = week
        refresh()
    }

    override suspend fun insertTemplates(rows: List<WorkoutTemplateEntity>) {
        rows.forEach { r ->
            val list = templates.getOrPut(r.weekId ?: "") { mutableListOf() }
            list.removeAll { it.id == r.id }
            list += r
        }
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

    override suspend fun deleteTemplatesForWeek(weekId: String) {
        templates[weekId]?.forEach { exercises.remove(it.id) }
        templates.remove(weekId)
        refresh()
    }

    override suspend fun clearActive() {
        weeks.replaceAll { _, v -> v.copy(active = false) }
        refresh()
    }

    override suspend fun setWeekActive(id: String) {
        weeks[id]?.let { weeks[id] = it.copy(active = true) }
        refresh()
    }

    override suspend fun delete(id: String) {
        deleteTemplatesForWeek(id)
        weeks.remove(id)
        refresh()
    }

    override suspend fun deleteAll() {
        weeks.clear()
        templates.clear()
        exercises.clear()
        refresh()
    }

    override suspend fun count(): Int = weeks.size
}
