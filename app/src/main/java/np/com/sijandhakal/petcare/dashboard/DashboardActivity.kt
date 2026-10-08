package np.com.sijandhakal.petcare.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import np.com.sijandhakal.petcare.LoginActivity
import np.com.sijandhakal.petcare.R
import np.com.sijandhakal.petcare.dashboard.fragments.HomeFragment
import np.com.sijandhakal.petcare.dashboard.fragments.PetsFragments
import np.com.sijandhakal.petcare.dashboard.fragments.PlacesFragment
import np.com.sijandhakal.petcare.dashboard.fragments.TaskFragments
import np.com.sijandhakal.petcare.databinding.ActivityDashboardBinding
import np.com.sijandhakal.petcare.databinding.ViewTopBarBinding

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    // Task to open in the Routines tab the next time it is selected; null opens an empty form.
    private var pendingEditTaskId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Everything on the dashboard belongs to a Firebase user, so bounce back to sign-in without one.
        if (FirebaseAuth.getInstance().currentUser == null) {
            openLogin()
            return
        }

        enableEdgeToEdge()
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The bottom navigation pads itself for the gesture bar, so only the top and sides go on the root.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_routines -> TaskFragments.newInstance(pendingEditTaskId).also { pendingEditTaskId = null }
                R.id.nav_pets -> PetsFragments()
                R.id.nav_places -> PlacesFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_today
        }
    }

    /** Switches to the Routines tab, editing [taskId] or creating a new routine when it is null. */
    fun openRoutineEditor(taskId: String?) {
        pendingEditTaskId = taskId
        binding.bottomNav.selectedItemId = R.id.nav_routines
    }

    fun showToday() {
        binding.bottomNav.selectedItemId = R.id.nav_today
    }

    fun showPets() {
        binding.bottomNav.selectedItemId = R.id.nav_pets
    }

    /** Fills in the shared header each tab includes: its subtitle and the sign-out button. */
    fun bindTopBar(topBar: ViewTopBarBinding, @StringRes subtitle: Int) {
        topBar.tvTopBarSubtitle.setText(subtitle)
        topBar.btnSignOut.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sign_out_title)
                .setMessage(R.string.sign_out_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_sign_out) { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    openLogin()
                }
                .show()
        }
    }

    private fun openLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
