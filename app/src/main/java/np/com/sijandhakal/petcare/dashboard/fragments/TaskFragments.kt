package np.com.sijandhakal.petcare.dashboard.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.firestore.ListenerRegistration
import np.com.sijandhakal.petcare.R
import np.com.sijandhakal.petcare.dashboard.DashboardActivity
import np.com.sijandhakal.petcare.dashboard.data.CareCategory
import np.com.sijandhakal.petcare.dashboard.data.CareTask
import np.com.sijandhakal.petcare.dashboard.data.PetCareRepository
import np.com.sijandhakal.petcare.databinding.FragmentTaskFragmentsBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val ARG_TASK_ID = "task_id"
private const val STATE_HOUR = "hour"
private const val STATE_MINUTE = "minute"
private const val STATE_START_DATE = "start_date"
private const val STATE_PET_ID = "pet_id"

/** "Routines" tab: create a new care routine, or edit/delete an existing one. */
class TaskFragments : Fragment() {

    private var _binding: FragmentTaskFragmentsBinding? = null
    private val binding get() = _binding!!

    private var editingTask: CareTask? = null
    private var selectedPetId: String? = null
    private var hour = 8
    private var minute = 0
    private var startDateMillis: Long? = MaterialDatePicker.todayInUtcMilliseconds()
    private var petsListener: ListenerRegistration? = null

    private val editingTaskId: String? get() = arguments?.getString(ARG_TASK_ID)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskFragmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? DashboardActivity)?.bindTopBar(binding.topBar, R.string.nav_routines)

        CareCategory.entries.forEach { category ->
            val chip = layoutInflater.inflate(R.layout.view_filter_chip, binding.chipGroupCategory, false) as Chip
            chip.id = View.generateViewId()
            chip.tag = category
            chip.setText(category.label)
            chip.setChipIconResource(category.icon)
            chip.isChipIconVisible = true
            binding.chipGroupCategory.addView(chip)
        }

        binding.actvFrequency.setSimpleItems(resources.getStringArray(R.array.routine_frequencies))
        binding.tietTime.setOnClickListener { showTimePicker() }
        binding.tietStartDate.setOnClickListener { showDatePicker() }
        binding.tietTaskName.doAfterTextChanged { binding.tilTaskName.error = null }
        binding.btnSave.setOnClickListener { save() }
        binding.btnAddPet.setOnClickListener { (activity as? DashboardActivity)?.showPets() }
        binding.chipGroupPet.setOnCheckedStateChangeListener { group, _ ->
            group.checkedPetId()?.let { selectedPetId = it }
        }

        configureModeUi()
        if (savedInstanceState != null) {
            // Text fields restore themselves; picker values and the pet live in fields.
            hour = savedInstanceState.getInt(STATE_HOUR, hour)
            minute = savedInstanceState.getInt(STATE_MINUTE, minute)
            startDateMillis = savedInstanceState.getLong(STATE_START_DATE, -1L).takeIf { it >= 0 }
            selectedPetId = savedInstanceState.getString(STATE_PET_ID)
            updateTimeAndDateFields()
            listenForPets()
            editingTaskId?.let { taskId ->
                PetCareRepository.getTask(taskId).addOnSuccessListener { task ->
                    if (_binding == null) return@addOnSuccessListener
                    editingTask = task
                    configureModeUi()
                }
            }
        } else {
            val taskId = editingTaskId
            if (taskId == null) {
                populate(null)
                listenForPets()
            } else {
                binding.btnSave.isEnabled = false
                PetCareRepository.getTask(taskId)
                    .addOnSuccessListener { task ->
                        if (_binding == null) return@addOnSuccessListener
                        editingTask = task
                        configureModeUi()
                        populate(task)
                        listenForPets()
                    }
                    .addOnFailureListener(::showError)
            }
        }
    }

    private fun listenForPets() {
        petsListener = PetCareRepository.listenPets(::showError) { pets ->
            val b = _binding ?: return@listenPets
            if (selectedPetId == null || pets.none { it.id == selectedPetId }) {
                selectedPetId = pets.firstOrNull()?.id
            }
            b.chipGroupPet.showPetChips(pets, selectedPetId) { pet ->
                if (pet.breed.isBlank()) pet.name else getString(R.string.task_meta_format, pet.name, pet.breed)
            }
            b.chipGroupPet.isVisible = pets.isNotEmpty()
            b.llNoPets.isVisible = pets.isEmpty()
            b.btnSave.isEnabled = pets.isNotEmpty()
        }
    }

    private fun populate(task: CareTask?) {
        task?.petId?.let { selectedPetId = it }
        selectCategory(task?.category ?: CareCategory.FEEDING)
        binding.tietTaskName.setText(task?.title.orEmpty())
        binding.actvFrequency.setText(
            task?.frequency?.takeIf { it.isNotBlank() }
                ?: resources.getStringArray(R.array.routine_frequencies).first(),
            false
        )
        binding.tietSupplies.setText(task?.supplies.orEmpty())
        binding.tietNotes.setText(task?.notes.orEmpty())
        binding.switchSitter.isChecked = task?.includeInSitterChecklist ?: true
        hour = task?.hour ?: 8
        minute = task?.minute ?: 0
        startDateMillis = if (task != null) task.startDateMillis else MaterialDatePicker.todayInUtcMilliseconds()
        updateTimeAndDateFields()
    }

    private fun configureModeUi() {
        if (editingTask != null) {
            binding.tvModeTitle.setText(R.string.routine_title_edit)
            binding.btnSecondary.setText(R.string.action_delete_routine)
            binding.btnSecondary.setIconResource(R.drawable.ic_delete_24)
            binding.btnSecondary.setOnClickListener { confirmDelete() }
        } else {
            binding.tvModeTitle.setText(R.string.routine_title_create)
            binding.btnSecondary.setText(R.string.action_clear_form)
            binding.btnSecondary.icon = null
            binding.btnSecondary.setOnClickListener {
                populate(null)
                Toast.makeText(requireContext(), R.string.routine_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectCategory(category: CareCategory) {
        for (i in 0 until binding.chipGroupCategory.childCount) {
            val chip = binding.chipGroupCategory.getChildAt(i) as Chip
            if (chip.tag == category) binding.chipGroupCategory.check(chip.id)
        }
    }

    private fun selectedCategory(): CareCategory {
        val chip = binding.chipGroupCategory.findViewById<Chip>(binding.chipGroupCategory.checkedChipId)
        return chip?.tag as? CareCategory ?: CareCategory.FEEDING
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(R.string.routine_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            hour = picker.hour
            minute = picker.minute
            updateTimeAndDateFields()
        }
        picker.show(childFragmentManager, "time_picker")
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.routine_start_date)
            .setSelection(startDateMillis ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            startDateMillis = selection
            updateTimeAndDateFields()
        }
        picker.show(childFragmentManager, "date_picker")
    }

    private fun updateTimeAndDateFields() {
        binding.tietTime.setText(CareTask(hour = hour, minute = minute).timeLabel())
        binding.tietStartDate.setText(startDateMillis?.let { millis ->
            // MaterialDatePicker selections are UTC midnight, so format in UTC to avoid an off-by-one day.
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(millis)
        }.orEmpty())
    }

    private fun save() {
        val petId = selectedPetId ?: return
        val title = binding.tietTaskName.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.tilTaskName.error = getString(R.string.routine_name_required)
            binding.tietTaskName.requestFocus()
            return
        }
        val task = CareTask(
            id = editingTask?.id.orEmpty(),
            petId = petId,
            title = title,
            category = selectedCategory(),
            hour = hour,
            minute = minute,
            startDateMillis = startDateMillis,
            frequency = binding.actvFrequency.text?.toString().orEmpty(),
            supplies = binding.tietSupplies.text?.toString()?.trim().orEmpty(),
            notes = binding.tietNotes.text?.toString()?.trim().orEmpty(),
            includeInSitterChecklist = binding.switchSitter.isChecked,
            doneOn = editingTask?.doneOn
        )
        // Firestore applies to its local cache straight away, so there's no need to wait for the server.
        PetCareRepository.saveTask(task).addOnFailureListener(::showError)
        Toast.makeText(requireContext(), getString(R.string.routine_saved, task.title), Toast.LENGTH_SHORT).show()
        (activity as? DashboardActivity)?.showToday()
    }

    private fun confirmDelete() {
        val task = editingTask ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete_24)
            .setTitle(R.string.delete_routine_title)
            .setMessage(getString(R.string.delete_routine_message, task.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                PetCareRepository.deleteTask(task.id).addOnFailureListener(::showError)
                Toast.makeText(requireContext(), getString(R.string.task_deleted, task.title), Toast.LENGTH_SHORT).show()
                (activity as? DashboardActivity)?.showToday()
            }
            .show()
    }

    private fun showError(e: Exception) {
        val b = _binding ?: return
        Snackbar.make(b.root, getString(R.string.error_firebase, e.localizedMessage), Snackbar.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_HOUR, hour)
        outState.putInt(STATE_MINUTE, minute)
        outState.putLong(STATE_START_DATE, startDateMillis ?: -1L)
        outState.putString(STATE_PET_ID, selectedPetId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        petsListener?.remove()
        petsListener = null
        _binding = null
    }

    companion object {
        /** @param taskId the routine to edit, or null to create a new one. */
        @JvmStatic
        fun newInstance(taskId: String?) = TaskFragments().apply {
            if (taskId != null) arguments = bundleOf(ARG_TASK_ID to taskId)
        }
    }
}
