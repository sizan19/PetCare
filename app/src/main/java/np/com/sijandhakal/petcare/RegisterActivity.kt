package np.com.sijandhakal.petcare

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import np.com.sijandhakal.petcare.dashboard.DashboardActivity
import np.com.sijandhakal.petcare.dashboard.data.PetCareRepository
import np.com.sijandhakal.petcare.databinding.ActivityRegisterBinding
import java.util.regex.Pattern

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            performRegistration()
        }

        // Back to the sign-in screen that opened this one
        binding.btnSignIn.setOnClickListener {
            finish()
        }
    }

    private fun performRegistration() {
        val fullName = binding.tietFullName.text.toString().trim()
        val email = binding.tietEmail.text.toString().trim()
        val password = binding.tietPassword.text.toString().trim()
        val confirmPassword = binding.tietConfirmPassword.text.toString().trim()

        // Full Name Validation
        if (fullName.isEmpty()) {
            Toast.makeText(this, "Full name is empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Email Validation
        if (email.isEmpty()) {
            Toast.makeText(this, "Email is empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
            return
        }

        // Password Validation
        if (password.isEmpty()) {
            Toast.makeText(this, "Password is empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isValidPassword(password)) {
            Toast.makeText(
                this,
                "Password must be 8-30 chars, with 1 uppercase, 1 number, and 1 special char",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Confirm Password Validation
        if (confirmPassword.isEmpty()) {
            Toast.makeText(this, "Confirm password is empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Successful validation: create the Firebase account, store the display name, then open the dashboard
        setLoading(true)
        val auth = FirebaseAuth.getInstance()
        auth.createUserWithEmailAndPassword(email, password)
            .continueWithTask { created ->
                val user = created.result.user!!
                user.updateProfile(userProfileChangeRequest { displayName = fullName })
            }
            .continueWithTask { PetCareRepository.saveUserProfile(fullName, email) }
            .addOnSuccessListener {
                val intent = Intent(this, DashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, e.localizedMessage ?: "Registration failed", Toast.LENGTH_LONG).show()
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnSignIn.isEnabled = !loading
        binding.btnRegister.setText(if (loading) R.string.creating_account else R.string.action_get_started)
    }

    private fun isValidPassword(password: String): Boolean {
        // Policy: 8-30 chars, 1 uppercase, 1 number, 1 special character
        val passwordPattern = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,30}$"
        val pattern = Pattern.compile(passwordPattern)
        return pattern.matcher(password).matches()
    }
}
