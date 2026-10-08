package np.com.sijandhakal.petcare.dashboard.fragments

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ListenerRegistration
import np.com.sijandhakal.petcare.R
import np.com.sijandhakal.petcare.dashboard.DashboardActivity
import np.com.sijandhakal.petcare.dashboard.data.CareCategory
import np.com.sijandhakal.petcare.dashboard.data.CareTask
import np.com.sijandhakal.petcare.dashboard.data.Pet
import np.com.sijandhakal.petcare.dashboard.data.PetCareRepository
import np.com.sijandhakal.petcare.dashboard.data.PetPhotos
import np.com.sijandhakal.petcare.dashboard.data.Species
import np.com.sijandhakal.petcare.databinding.DialogPetFormBinding
import np.com.sijandhakal.petcare.databinding.FragmentPetsFragmentsBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val STATE_PET_ID = "pet_id"

/** "Pets & Share" tab: the user's pet profiles plus SMS delegation of a pet's care routine to a sitter. */
class PetsFragments : Fragment() {

    private var _binding: FragmentPetsFragmentsBinding? = null
    private val binding get() = _binding!!

    private var pets: List<Pet> = emptyList()
    private var tasks: List<CareTask> = emptyList()
    private var selectedPetId: String? = null
    private val listeners = mutableListOf<ListenerRegistration>()

    private val selectedPet: Pet? get() = pets.firstOrNull { it.id == selectedPetId }

    // The pet form that is open right now, and the photo chosen in it (Base64; "" = no photo).
    private var openForm: DialogPetFormBinding? = null
    private var formPhotoBase64 = ""

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val form = openForm ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        form.progressPhoto.isVisible = true
        PetPhotos.encode(requireContext(), uri) { encoded ->
            if (openForm !== form) return@encode
            form.progressPhoto.isVisible = false
            if (encoded == null) {
                Toast.makeText(requireContext(), R.string.photo_read_failed, Toast.LENGTH_SHORT).show()
            } else {
                setFormPhoto(form, encoded)
            }
        }
    }

    // Camera thumbnail; no CAMERA permission needed because the system camera app takes the picture.
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val form = openForm ?: return@registerForActivityResult
        if (bitmap != null) setFormPhoto(form, PetPhotos.encode(bitmap))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPetsFragmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? DashboardActivity)?.bindTopBar(binding.topBar, R.string.nav_pets)
        selectedPetId = savedInstanceState?.getString(STATE_PET_ID)

        binding.chipGroupPet.setOnCheckedStateChangeListener { group, _ ->
            group.checkedPetId()?.let {
                selectedPetId = it
                bindPet()
            }
        }
        binding.btnAddPet.setOnClickListener { showPetForm(null) }
        binding.btnEditPet.setOnClickListener { selectedPet?.let(::showPetForm) }
        binding.btnDeletePet.setOnClickListener { selectedPet?.let(::confirmDeletePet) }

        listOf(binding.cbFeeding, binding.cbWalks, binding.cbMeds, binding.cbVet).forEach { checkBox ->
            checkBox.setOnCheckedChangeListener { _, _ -> updatePreview() }
        }
        binding.tietSitterName.doAfterTextChanged { updatePreview() }
        binding.tietSitterPhone.doAfterTextChanged { binding.tilSitterPhone.error = null }

        binding.btnSendSms.setOnClickListener { sendSms() }
        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService<ClipboardManager>()
            clipboard?.setPrimaryClip(ClipData.newPlainText(getString(R.string.delegation_preview), buildMessage()))
            Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
        }

        listeners += PetCareRepository.listenPets(::showError) { result ->
            val b = _binding ?: return@listenPets
            pets = result
            if (selectedPet == null) selectedPetId = pets.firstOrNull()?.id
            b.chipGroupPet.showPetChips(pets, selectedPetId)
            bindPet()
        }
        listeners += PetCareRepository.listenTasks(::showError) { result ->
            tasks = result
            updatePreview()
        }
    }

    private fun bindPet() {
        val b = _binding ?: return
        val pet = selectedPet
        b.cardEmpty.isVisible = pet == null
        b.contentPet.isVisible = pet != null
        if (pet == null) return

        b.tvPetAvatar.text = pet.name.take(1).uppercase()
        val photo = PetPhotos.decode(pet.photoBase64)
        b.ivPetPhoto.setImageBitmap(photo)
        b.ivPetPhoto.isVisible = photo != null
        b.tvPetName.text = pet.name
        b.tvPetBreed.text = listOf(getString(pet.species.label), pet.breed)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val stats = listOf(pet.age, pet.weight).filter { it.isNotBlank() }
        b.tvPetStats.isVisible = stats.isNotEmpty()
        b.tvPetStats.text = stats.joinToString(" • ")
        b.tvMicrochip.text = labelled(R.string.pet_microchip, pet.microchip)
        b.tvDiet.text = labelled(R.string.pet_diet, pet.diet)
        b.tvAllergies.text = labelled(R.string.pet_allergies, pet.allergies)
        val vet = listOf(pet.vetName, pet.vetPhone).filter { it.isNotBlank() }.joinToString(" · ")
        b.tvVet.text = labelled(R.string.pet_vet, vet)
        b.tvDelegationBody.text = getString(R.string.delegation_body, pet.name)
        updatePreview()
    }

    private fun labelled(label: Int, value: String) =
        getString(R.string.label_value_format, getString(label), value.ifBlank { getString(R.string.not_set) })

    private fun updatePreview() {
        val b = _binding ?: return
        val checkBoxes = listOf(b.cbFeeding, b.cbWalks, b.cbMeds, b.cbVet)
        b.tvItemsCount.text =
            getString(R.string.delegation_items_count, checkBoxes.count { it.isChecked }, checkBoxes.size)
        b.tvSmsPreview.text = buildMessage()
    }

    /** Builds the SMS from this pet's routines flagged "Include in pet sitter checklist" on the Routines tab. */
    private fun buildMessage(): String {
        val pet = selectedPet ?: return ""
        val sitter = binding.tietSitterName.text?.toString()?.trim().orEmpty()
        val petTasks = tasks.filter { it.petId == pet.id && it.includeInSitterChecklist }

        fun section(emoji: String, label: String, category: CareCategory): String? {
            val items = petTasks.filter { it.category == category }
            if (items.isEmpty()) return null
            val details = items.joinToString("; ") { task ->
                buildString {
                    append(task.timeLabel()).append(" ").append(task.title)
                    if (task.supplies.isNotBlank()) append(" (").append(task.supplies).append(")")
                    if (!task.repeat.isDaily) {
                        val next = task.nextDueOnOrAfter(LocalDate.now())
                        append(" [").append(task.frequency.substringBefore(" ("))
                            .append(", next ").append(next.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())))
                            .append("]")
                    }
                }
            }
            return "$emoji $label: $details"
        }

        val greeting = if (sitter.isEmpty()) "Hi!" else "Hi $sitter!"
        val lines = mutableListOf("$greeting Here is ${pet.name}'s PetCare routine:")
        if (binding.cbFeeding.isChecked) section("🍽", "Feeding", CareCategory.FEEDING)?.let(lines::add)
        if (binding.cbWalks.isChecked) section("🦮", "Walks", CareCategory.EXERCISE)?.let(lines::add)
        if (binding.cbMeds.isChecked) section("💊", "Meds", CareCategory.MEDICATION)?.let(lines::add)
        if (binding.cbVet.isChecked && (pet.vetName.isNotBlank() || pet.vetPhone.isNotBlank())) {
            val vet = listOf(pet.vetName, pet.vetPhone).filter { it.isNotBlank() }.joinToString(", ")
            lines += "🏥 Vet: $vet"
        }
        if (pet.allergies.isNotBlank()) lines += "⚠ Allergies: ${pet.allergies}"
        lines += "Thanks for looking after ${pet.name}!"
        return lines.joinToString("\n")
    }

    private fun sendSms() {
        val phone = binding.tietSitterPhone.text?.toString()?.trim().orEmpty()
        if (phone.isEmpty()) {
            binding.tilSitterPhone.error = getString(R.string.sms_phone_required)
            binding.tietSitterPhone.requestFocus()
            return
        }
        val anySelected = listOf(binding.cbFeeding, binding.cbWalks, binding.cbMeds, binding.cbVet).any { it.isChecked }
        if (!anySelected) {
            Toast.makeText(requireContext(), R.string.sms_nothing_selected, Toast.LENGTH_SHORT).show()
            return
        }
        // ACTION_SENDTO hands the message to the user's SMS app, so no SEND_SMS permission is needed.
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)))
            .putExtra("sms_body", buildMessage())
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_sms_app, Toast.LENGTH_SHORT).show()
        }
    }

    /** Add (when [pet] is null) or edit a pet in a dialog. */
    private fun showPetForm(pet: Pet?) {
        val form = DialogPetFormBinding.inflate(layoutInflater)
        Species.entries.forEach { species ->
            val chip = layoutInflater.inflate(R.layout.view_filter_chip, form.chipGroupSpecies, false) as Chip
            chip.id = View.generateViewId()
            chip.tag = species
            chip.setText(species.label)
            form.chipGroupSpecies.addView(chip)
            if (species == (pet?.species ?: Species.DOG)) form.chipGroupSpecies.check(chip.id)
        }
        form.tietName.setText(pet?.name.orEmpty())
        form.tietBreed.setText(pet?.breed.orEmpty())
        form.tietAge.setText(pet?.age.orEmpty())
        form.tietWeight.setText(pet?.weight.orEmpty())
        form.tietMicrochip.setText(pet?.microchip.orEmpty())
        form.tietDiet.setText(pet?.diet.orEmpty())
        form.tietAllergies.setText(pet?.allergies.orEmpty())
        form.tietVetName.setText(pet?.vetName.orEmpty())
        form.tietVetPhone.setText(pet?.vetPhone.orEmpty())
        form.tietName.doAfterTextChanged { form.tilName.error = null }

        openForm = form
        setFormPhoto(form, pet?.photoBase64.orEmpty())
        form.btnGallery.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        form.btnCamera.setOnClickListener {
            try {
                takePhoto.launch(null)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.no_camera_app, Toast.LENGTH_SHORT).show()
            }
        }
        form.btnRemovePhoto.setOnClickListener { setFormPhoto(form, "") }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (pet == null) R.string.pet_form_title_add else R.string.pet_form_title_edit)
            .setView(form.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .setOnDismissListener { if (openForm === form) openForm = null }
            .show()

        // Validate before closing, so a missing name keeps the dialog open.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = form.tietName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                form.tilName.error = getString(R.string.pet_name_required)
                return@setOnClickListener
            }
            val species = form.chipGroupSpecies.findViewById<Chip>(form.chipGroupSpecies.checkedChipId)
                ?.tag as? Species ?: Species.OTHER
            fun text(v: com.google.android.material.textfield.TextInputEditText) = v.text?.toString()?.trim().orEmpty()
            val saved = Pet(
                id = pet?.id ?: PetCareRepository.newPetId(),
                name = name,
                species = species,
                breed = text(form.tietBreed),
                age = text(form.tietAge),
                weight = text(form.tietWeight),
                microchip = text(form.tietMicrochip),
                diet = text(form.tietDiet),
                allergies = text(form.tietAllergies),
                vetName = text(form.tietVetName),
                vetPhone = text(form.tietVetPhone),
                photoBase64 = formPhotoBase64
            )
            selectedPetId = saved.id
            PetCareRepository.savePet(saved).addOnFailureListener(::showError)
            Toast.makeText(requireContext(), getString(R.string.pet_saved, saved.name), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun setFormPhoto(form: DialogPetFormBinding, base64: String) {
        formPhotoBase64 = base64
        val photo = PetPhotos.decode(base64)
        form.ivPhoto.setImageBitmap(photo)
        form.ivPhoto.isVisible = photo != null
        form.btnRemovePhoto.isVisible = photo != null
        form.btnGallery.setText(if (photo == null) R.string.action_choose_photo else R.string.action_change_photo)
    }

    private fun confirmDeletePet(pet: Pet) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete_24)
            .setTitle(getString(R.string.delete_pet_title, pet.name))
            .setMessage(getString(R.string.delete_pet_message, pet.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                selectedPetId = null
                PetCareRepository.deletePet(pet.id)
                    .addOnSuccessListener {
                        context?.let { Toast.makeText(it, getString(R.string.task_deleted, pet.name), Toast.LENGTH_SHORT).show() }
                    }
                    .addOnFailureListener(::showError)
            }
            .show()
    }

    private fun showError(e: Exception) {
        val b = _binding ?: return
        Snackbar.make(b.root, getString(R.string.error_firebase, e.localizedMessage), Snackbar.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PET_ID, selectedPetId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listeners.forEach { it.remove() }
        listeners.clear()
        _binding = null
    }
}
