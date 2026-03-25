package ani.dantotsu.connections.simkl

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.startMainActivity
import ani.dantotsu.toast
import kotlinx.coroutines.launch

class Login : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        setContentView(ProgressBar(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        if (intent == null || intent.data == null) {
            toast("Simkl Error: Login intent was null.")
            startMainActivity(this@Login)
            finish()
            return
        }

        val data = intent.data
        val code = data?.getQueryParameter("code")

        if (code.isNullOrEmpty()) {
            toast("Simkl Error: Failed to get authorization code from redirect.")
            startMainActivity(this@Login)
            finish()
            return
        }

        lifecycleScope.launch {
            val token = Simkl.getInstance().auth.exchangeCodeForToken(code)
            if (token != null) {
                Simkl.getInstance().accessToken = token

                // 🔥 FETCH USER DATA ONLY ONCE AT LOGIN
                Simkl.getInstance().fetchAndSaveUser()

                toast("Successfully logged in to Simkl!")
            } else {
                toast("Simkl Error: Failed to exchange code for access token.")
            }
            startMainActivity(this@Login)
            finish()
        }
    }
}
