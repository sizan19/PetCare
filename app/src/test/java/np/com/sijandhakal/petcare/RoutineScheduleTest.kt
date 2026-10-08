package np.com.sijandhakal.petcare

import np.com.sijandhakal.petcare.dashboard.data.CareTask
import np.com.sijandhakal.petcare.dashboard.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Checks which day a routine is due, so only today's care shows in the Today checklist. */
class RoutineScheduleTest {

    private fun routine(frequency: String, start: LocalDate?) = CareTask(
        frequency = frequency,
        startDateMillis = start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )

    private val start = LocalDate.of(2026, 1, 31)

    @Test
    fun frequencyLabelsMapToRepeats() {
        assertEquals(Repeat.DAILY, Repeat.from("Daily"))
        assertEquals(Repeat.DAILY, Repeat.from("Twice daily (morning & night)"))
        assertEquals(Repeat.WEEKLY, Repeat.from("Weekly"))
        assertEquals(Repeat.BIWEEKLY, Repeat.from("Bi-weekly (every 2 weeks)"))
        assertEquals(Repeat.MONTHLY, Repeat.from("Monthly"))
        assertEquals(Repeat.MONTHLY, Repeat.from("Monthly (every 4 weeks)"))
        assertEquals(Repeat.QUARTERLY, Repeat.from("Quarterly (every 3 months)"))
        assertEquals(Repeat.YEARLY, Repeat.from("Yearly"))
        assertEquals(Repeat.DAILY, Repeat.from("Something else"))
    }

    @Test
    fun dailyRoutineIsDueEveryDayFromItsStart() {
        val task = routine("Daily", start)
        assertFalse(task.isDueOn(start.minusDays(1)))
        assertTrue(task.isDueOn(start))
        assertTrue(task.isDueOn(start.plusDays(17)))
    }

    @Test
    fun routineWithoutStartDateIsDueToday() {
        val today = LocalDate.of(2026, 10, 6)
        assertTrue(routine("Monthly", null).isDueOn(today))
    }

    @Test
    fun weeklyAndBiweeklyRepeatOnTheSameWeekday() {
        val weekly = routine("Weekly", start)
        assertTrue(weekly.isDueOn(start.plusDays(7)))
        assertFalse(weekly.isDueOn(start.plusDays(8)))
        assertEquals(start.plusDays(14), weekly.nextDueOnOrAfter(start.plusDays(8)))

        val biweekly = routine("Bi-weekly (every 2 weeks)", start)
        assertFalse(biweekly.isDueOn(start.plusDays(7)))
        assertTrue(biweekly.isDueOn(start.plusDays(14)))
    }

    @Test
    fun monthlyClampsToShortMonthsAndQuarterlySkipsTwoMonths() {
        val monthly = routine("Monthly", start) // Jan 31
        assertEquals(LocalDate.of(2026, 2, 28), monthly.nextDueOnOrAfter(LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), monthly.nextDueOnOrAfter(LocalDate.of(2026, 3, 1)))

        val quarterly = routine("Quarterly (every 3 months)", start)
        assertFalse(quarterly.isDueOn(LocalDate.of(2026, 2, 28)))
        assertEquals(LocalDate.of(2026, 4, 30), quarterly.nextDueOnOrAfter(LocalDate.of(2026, 2, 1)))
    }

    @Test
    fun yearlyRoutineIsDueOnTheAnniversary() {
        val yearly = routine("Yearly", LocalDate.of(2026, 10, 20))
        assertEquals(LocalDate.of(2026, 10, 20), yearly.nextDueOnOrAfter(LocalDate.of(2026, 10, 6)))
        assertEquals(LocalDate.of(2027, 10, 20), yearly.nextDueOnOrAfter(LocalDate.of(2026, 10, 21)))
    }
}
