package np.com.sijandhakal.petcare

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import np.com.sijandhakal.petcare.dashboard.DashboardActivity
import np.com.sijandhakal.petcare.databinding.ActivityLoginBinding
import java.util.regex.Pattern

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        // Navigate to RegisterActivity screen
        binding.btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin() {
        val email = binding.tietEmail.text.toString().trim()
        val password = binding.tietPassword.text.toString().trim()

        // Email validation
        if (email.isEmpty()) {
            Toast.makeText(this, "Email is empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
            return
        }

        // Password validation
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

        // Successful validation: sign in with Firebase Auth
        setLoading(true)
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, e.localizedMessage ?: "Sign in failed", Toast.LENGTH_LONG).show()
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnRegister.isEnabled = !loading
        binding.btnLogin.setText(if (loading) R.string.signing_in else R.string.action_sign_in)
    }

    private fun isValidPassword(password: String): Boolean {
        val passwordPattern = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,30}$"
        val pattern = Pattern.compile(passwordPattern)
        return pattern.matcher(password).matches()
    }
}
