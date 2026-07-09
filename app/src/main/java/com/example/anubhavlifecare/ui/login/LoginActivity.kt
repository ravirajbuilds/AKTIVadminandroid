package com.example.anubhavlifecare.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.anubhavlifecare.MainActivity
import com.example.anubhavlifecare.R
import com.example.anubhavlifecare.data.repository.AktivRepository
import com.example.anubhavlifecare.utils.SessionManager
import com.example.anubhavlifecare.utils.toUserMessage
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private val aktivRepository = AktivRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SessionManager.isLoggedIn(this)) {
            openMain()
            return
        }

        setContentView(R.layout.activity_login)

        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val progress = findViewById<ProgressBar>(R.id.progressLogin)

        btnLogin.setOnClickListener {
            val userid = etUsername.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString().orEmpty()
            if (userid.isBlank() || password.isBlank()) {
                Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progress.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            lifecycleScope.launch {
                val result = aktivRepository.login(userid, password)
                progress.visibility = View.GONE
                btnLogin.isEnabled = true

                result.fold(
                    onSuccess = { response ->
                        if (response.success) {
                            SessionManager.save(
                                this@LoginActivity,
                                response.userKey,
                                response.userid,
                                response.username,
                                response.role,
                                response.permissions,
                            )
                            openMain()
                        } else {
                            Toast.makeText(
                                this@LoginActivity,
                                R.string.login_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onFailure = { err ->
                        Toast.makeText(
                            this@LoginActivity,
                            err.toUserMessage(),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
