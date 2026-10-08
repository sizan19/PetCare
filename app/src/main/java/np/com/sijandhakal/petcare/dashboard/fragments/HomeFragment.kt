package np.com.sijandhakal.petcare.dashboard.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import np.com.sijandhakal.petcare.R
import np.com.sijandhakal.petcare.dashboard.DashboardActivity
import np.com.sijandhakal.petcare.dashboard.data.CareTask
import np.com.sijandhakal.petcare.dashboard.data.Pet
import np.com.sijandhakal.petcare.dashboard.data.PetCareRepository
import np.com.sijandhakal.petcare.databinding.FragmentHomeBinding
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** "Today" tab: daily progress plus the morning and evening care checklists for the signed-in user. */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var pets: List<Pet> = emptyList()
    private var tasks: List<CareTask> = emptyList()
    private var petFilter: String? = null
    private val listeners = mutableListOf<ListenerRegistration>()

    private val morningAdapter = CareTaskAdapter(::petName, ::onToggleDone, ::onEdit, ::onDelete)
    private val eveningAdapter = CareTaskAdapter(::petName, ::onToggleDone, ::onEdit, ::onDelete)
    private val upcomingAdapter = CareTaskAdapter(::petName, ::onToggleDone, ::onEdit, ::onDelete, upcoming = true)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? DashboardActivity)?.bindTopBar(binding.topBar, R.string.nav_today)
        binding.tvDate.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
        binding.tvGreeting.text = greeting()

        binding.rvMorning.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMorning.adapter = morningAdapter
        binding.rvEvening.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEvening.adapter = eveningAdapter
        binding.rvUpcoming.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUpcoming.adapter = upcomingAdapter

        binding.chipGroupPets.setOnCheckedStateChangeListener { group, _ ->
            petFilter = group.checkedPetId()
            render()
        }
        binding.fabNewRoutine.setOnClickListener {
            (activity as? DashboardActivity)?.openRoutineEditor(null)
        }

        listeners += PetCareRepository.listenPets(::showError) { result ->
            pets = result
            if (petFilter != null && pets.none { it.id == petFilter }) petFilter = null
            binding.chipGroupPets.showPetChips(pets, petFilter)
            if (petFilter == null) binding.chipGroupPets.check(R.id.chip_all)
            render()
        }
        listeners += PetCareRepository.listenTasks(::showError) { result ->
            tasks = result
            render()
        }
    }

    private fun greeting(): String {
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName?.takeIf { it.isNotBlank() }?.substringBefore(' ')
            ?: user?.email?.substringBefore('@')
            ?: ""
        val res = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
        return getString(res, name)
    }

    private fun petName(petId: String): String =
        pets.firstOrNull { it.id == petId }?.name ?: getString(R.string.unknown_pet)

    private fun render() {
        val b = _binding ?: return
        b.tvSubtitle.text = if (pets.isEmpty()) {
            getString(R.string.today_subtitle_no_pets)
        } else {
            resources.getQuantityString(R.plurals.pets_in_care, pets.size, pets.size)
        }

        val visible = tasks.filter { petFilter == null || it.petId == petFilter }

        // Only routines due today go in the checklist; weekly/monthly/yearly ones wait in "Coming up".
        val today = LocalDate.now()
        val (dueToday, notDue) = visible.partition { it.isDueOn(today) }
        val upcoming = notDue.sortedWith(compareBy({ it.nextDueOnOrAfter(today) }, { it.hour }, { it.minute }))
        val (morning, evening) = dueToday.partition { it.isMorning }
        morningAdapter.submitList(morning)
        eveningAdapter.submitList(evening)
        upcomingAdapter.submitList(upcoming)

        b.headerMorning.isVisible = morning.isNotEmpty()
        b.rvMorning.isVisible = morning.isNotEmpty()
        b.headerEvening.isVisible = evening.isNotEmpty()
        b.rvEvening.isVisible = evening.isNotEmpty()
        b.headerUpcoming.isVisible = upcoming.isNotEmpty()
        b.tvUpcomingHint.isVisible = upcoming.isNotEmpty()
        b.rvUpcoming.isVisible = upcoming.isNotEmpty()
        b.tvMorningCount.text = getString(R.string.section_done_count, morning.count { it.done }, morning.size)
        b.tvEveningCount.text = getString(R.string.section_done_count, evening.count { it.done }, evening.size)
        b.tvUpcomingCount.text = upcoming.size.toString()

        val done = dueToday.count { it.done }
        val percent = if (dueToday.isEmpty()) 0 else done * 100 / dueToday.size
        b.tvProgressTitle.text = if (dueToday.isEmpty() && upcoming.isNotEmpty()) {
            getString(R.string.today_nothing_due)
        } else {
            getString(R.string.daily_goal_progress, done, dueToday.size)
        }
        b.tvProgressPercent.text = getString(R.string.daily_goal_percent, percent)
        b.progressDaily.setProgressCompat(percent, true)

        // Empty state: first nudge towards adding a pet, then towards the first routine.
        b.cardEmpty.isVisible = visible.isEmpty()
        if (pets.isEmpty()) {
            b.tvEmptyTitle.setText(R.string.empty_no_pets_title)
            b.tvEmptyBody.setText(R.string.empty_no_pets_body)
            b.btnEmptyAction.setText(R.string.action_add_pet)
            b.btnEmptyAction.setOnClickListener { (activity as? DashboardActivity)?.showPets() }
        } else {
            b.tvEmptyTitle.setText(R.string.empty_no_tasks_title)
            b.tvEmptyBody.setText(R.string.empty_no_tasks_body)
            b.btnEmptyAction.setText(R.string.action_new_routine)
            b.btnEmptyAction.setOnClickListener { (activity as? DashboardActivity)?.openRoutineEditor(null) }
        }
    }

    private fun onToggleDone(task: CareTask, done: Boolean) {
        PetCareRepository.setTaskDone(task.id, done).addOnFailureListener(::showError)
    }

    private fun onEdit(task: CareTask) {
        (activity as? DashboardActivity)?.openRoutineEditor(task.id)
    }

    private fun onDelete(task: CareTask) {
        PetCareRepository.deleteTask(task.id).addOnFailureListener(::showError)
        val b = _binding ?: return
        Snackbar.make(b.root, getString(R.string.task_deleted, task.title), Snackbar.LENGTH_LONG)
            .setAnchorView(b.fabNewRoutine)
            .setAction(R.string.action_undo) { PetCareRepository.saveTask(task) }
            .show()
    }

    private fun showError(e: Exception) {
        val b = _binding ?: return
        Snackbar.make(b.root, getString(R.string.error_firebase, e.localizedMessage), Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listeners.forEach { it.remove() }
        listeners.clear()
        _binding = null
    }
}
