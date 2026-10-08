package np.com.sijandhakal.petcare.dashboard.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import np.com.sijandhakal.petcare.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class Species(@StringRes val label: Int) {
    DOG(R.string.species_dog),
    CAT(R.string.species_cat),
    OTHER(R.string.species_other)
}

data class Pet(
    val id: String = "",
    val name: String = "",
    val species: Species = Species.DOG,
    val breed: String = "",
    val age: String = "",
    val weight: String = "",
    val microchip: String = "",
    val diet: String = "",
    val allergies: String = "",
    val vetName: String = "",
    val vetPhone: String = "",
    /** JPEG photo as Base64 (see [PetPhotos]); empty when the pet has no photo. */
    val photoBase64: String = ""
)

enum class CareCategory(@StringRes val label: Int, @DrawableRes val icon: Int) {
    MEDICATION(R.string.cat_medication, R.drawable.ic_medication_24),
    FEEDING(R.string.cat_feeding, R.drawable.ic_restaurant_24),
    EXERCISE(R.string.cat_exercise, R.drawable.ic_directions_walk_24),
    GROOMING(R.string.cat_grooming, R.drawable.ic_content_cut_24),
    VET(R.string.cat_vet, R.drawable.ic_medical_services_24)
}

data class CareTask(
    val id: String = "",
    val petId: String = "",
    val title: String = "",
    val category: CareCategory = CareCategory.FEEDING,
    val hour: Int = 8,
    val minute: Int = 0,
    val startDateMillis: Long? = null,
    val frequency: String = "",
    val supplies: String = "",
    val notes: String = "",
    val includeInSitterChecklist: Boolean = true,
    /** Day (yyyy-MM-dd) the task was last ticked off, so the checklist starts fresh each day. */
    val doneOn: String? = null
) {
    val done: Boolean get() = doneOn == today()
    val isMorning: Boolean get() = hour < 12
    val repeat: Repeat get() = Repeat.from(frequency)

    /** First day the routine applies (the picker stores UTC midnight); null means "from today". */
    private fun startDate(): LocalDate? =
        startDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    /** The first day on or after [day] when this routine is due. */
    fun nextDueOnOrAfter(day: LocalDate): LocalDate {
        val start = startDate() ?: return day
        if (!day.isAfter(start)) return start
        repeat.everyDays?.let { period ->
            val offset = ChronoUnit.DAYS.between(start, day) % period
            return if (offset == 0L) day else day.plusDays(period - offset)
        }
        // Calendar-based repeats (monthly, quarterly, yearly) land on the start's day of the month.
        val months = repeat.everyMonths!!
        var steps = ChronoUnit.MONTHS.between(start, day) / months * months
        var due = start.plusMonths(steps)
        while (due.isBefore(day)) {
            steps += months
            due = start.plusMonths(steps)
        }
        return due
    }

    fun isDueOn(day: LocalDate): Boolean = nextDueOnOrAfter(day) == day

    fun timeLabel(): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return String.format(Locale.getDefault(), "%d:%02d %s", h12, minute, amPm)
    }

    companion object {
        fun today(): String = LocalDate.now().toString()
    }
}

/**
 * How often a routine repeats. Routines store the frequency label shown in the form, so this
 * parses that text (older labels such as "Monthly (every 4 weeks)" still map correctly).
 */
enum class Repeat(val everyDays: Long? = null, val everyMonths: Long? = null) {
    DAILY(everyDays = 1),
    WEEKLY(everyDays = 7),
    BIWEEKLY(everyDays = 14),
    MONTHLY(everyMonths = 1),
    QUARTERLY(everyMonths = 3),
    YEARLY(everyMonths = 12);

    val isDaily: Boolean get() = this == DAILY

    companion object {
        fun from(label: String): Repeat {
            val text = label.lowercase(Locale.ROOT)
            return when {
                text.startsWith("bi-weekly") || text.startsWith("biweekly") || text.contains("2 weeks") -> BIWEEKLY
                text.startsWith("weekly") -> WEEKLY
                text.startsWith("monthly") -> MONTHLY
                text.startsWith("quarterly") || text.contains("3 months") -> QUARTERLY
                text.startsWith("yearly") || text.startsWith("annual") -> YEARLY
                else -> DAILY // "Daily", "Twice daily (…)" and anything unrecognised
            }
        }
    }
}

enum class PlaceCategory(@StringRes val label: Int, @DrawableRes val icon: Int) {
    VET(R.string.chip_vets, R.drawable.ic_local_hospital_24),
    PARK(R.string.chip_parks, R.drawable.ic_park_24),
    GROOMING(R.string.chip_grooming, R.drawable.ic_content_cut_24),
    STORE(R.string.chip_supplies, R.drawable.ic_shopping_bag_24)
}

data class Place(
    val id: String = "",
    val name: String = "",
    val category: PlaceCategory = PlaceCategory.VET,
    val address: String = "",
    val notes: String = "",
    /** Map position; null when the address couldn't be located. */
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}
