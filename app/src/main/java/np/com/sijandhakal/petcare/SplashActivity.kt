package np.com.sijandhakal.petcare

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import np.com.sijandhakal.petcare.dashboard.DashboardActivity
import np.com.sijandhakal.petcare.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Signed-in users go straight to their dashboard; everyone else signs in first.
        val next = if (FirebaseAuth.getInstance().currentUser != null) {
            DashboardActivity::class.java
        } else {
            LoginActivity::class.java
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@SplashActivity, next))
            finish()
        }, 5000)
    }
}
