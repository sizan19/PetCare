package np.com.sijandhakal.petcare.dashboard.fragments

import android.view.LayoutInflater
import android.view.View
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import np.com.sijandhakal.petcare.R
import np.com.sijandhakal.petcare.dashboard.data.Pet
import np.com.sijandhakal.petcare.dashboard.data.PetPhotos

/**
 * Replaces the pet chips in [group] (keeping any chips declared in XML, such as "All pets")
 * and re-checks the chip for [selectedPetId] when that pet still exists.
 * Each pet chip's tag is its pet id, and it shows the pet's photo when there is one.
 */
fun ChipGroup.showPetChips(pets: List<Pet>, selectedPetId: String?, label: (Pet) -> String = { it.name }) {
    for (i in childCount - 1 downTo 0) {
        if (getChildAt(i).tag is String) removeViewAt(i)
    }
    val inflater = LayoutInflater.from(context)
    pets.forEach { pet ->
        val chip = inflater.inflate(R.layout.view_filter_chip, this, false) as Chip
        chip.id = View.generateViewId()
        chip.tag = pet.id
        chip.text = label(pet)
        PetPhotos.decode(pet.photoBase64)?.let { photo ->
            chip.chipIcon = RoundedBitmapDrawableFactory.create(resources, photo).apply { isCircular = true }
            chip.chipIconTint = null
            chip.isChipIconVisible = true
        }
        addView(chip)
        if (pet.id == selectedPetId) check(chip.id)
    }
}

/** The pet id of the checked chip, or null when a non-pet chip (like "All pets") or nothing is checked. */
fun ChipGroup.checkedPetId(): String? = findViewById<Chip>(checkedChipId)?.tag as? String
