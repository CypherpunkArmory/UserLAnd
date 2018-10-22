package tech.ula.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.StrictMode
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import kotlinx.android.synthetic.main.frag_account.*
import kotlinx.android.synthetic.main.frag_account_info.*
import kotlinx.android.synthetic.main.frag_account_login.*
import kotlinx.android.synthetic.main.frag_account_register.*
import tech.ula.R
import tech.ula.utils.DumontUtility

class AccountFragment : Fragment() {

    private val currentUserPref = "currentUser"
    private val bearerTokenPref = "bearerToken"
    private lateinit var activityContext: Activity
    private lateinit var prefs: SharedPreferences
    private val dumontUtil = DumontUtility()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        return inflater.inflate(R.layout.frag_account, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = activity!!
    }

    override fun onStart() {
        super.onStart()
        prefs = activityContext.getSharedPreferences("account", Context.MODE_PRIVATE)
        val currentUser = prefs.getString(currentUserPref, "") ?: ""
        if (currentUser.isEmpty()) {
            inflateViewStubWithNewFragment(R.layout.frag_account_register)
        } else {
            inflateViewStubWithNewFragment(R.layout.frag_account_info)
        }
    }

    private fun inflateViewStubWithNewFragment(newFragmentId: Int) {
        frag_account_layout.removeAllViews()
        val inflater = LayoutInflater.from(activityContext)
        val newView = inflater.inflate(newFragmentId, frag_account_layout, false)
        frag_account_layout.addView(newView)

        when (newFragmentId) {
            R.layout.frag_account_login -> setupLoginPage()
            R.layout.frag_account_register -> setupRegisterPage()
            R.layout.frag_account_info -> setupAccountInfoPage()
        }
    }

    private fun setupRegisterPage() {
        activityContext.login_prompt.setOnClickListener {
            inflateViewStubWithNewFragment(R.layout.frag_account_login)
        }
    }

    private fun setupLoginPage() {
        activityContext.register_prompt.setOnClickListener {
            inflateViewStubWithNewFragment(R.layout.frag_account_register)
        }

        activityContext.btn_login.setOnClickListener {
            if (login()) inflateViewStubWithNewFragment(R.layout.frag_account_info)
        }
    }

    private fun setupAccountInfoPage() {
        activityContext.btn_logout.setOnClickListener {
            inflateViewStubWithNewFragment(R.layout.frag_account_login)
            logoutCurrentUser()
        }
    }

    private fun logoutCurrentUser() {
        with(prefs.edit()) {
            putString(currentUserPref, "")
            putString(bearerTokenPref, "")
            apply()
        }
    }

    private fun login(): Boolean {
        val email = activityContext.findViewById<EditText>(R.id.input_account_email).text.toString()
        val password = activityContext.findViewById<EditText>(R.id.input_account_password).text.toString()

        if (email.isNotEmpty() && password.isNotEmpty()) {
            val bearerToken = dumontUtil.loginAndGetBearerToken(email, password)
            return validateAndSaveUserCredentials(email, bearerToken)
        }

        return false
    }

    private fun validateAndSaveUserCredentials(email: String, bearerToken: String): Boolean {
        val bearerTokenCharactersCount = 277
        if (bearerToken.isNotEmpty() && bearerToken.count() == bearerTokenCharactersCount) {
            with(prefs.edit()) {
                putString(currentUserPref, email)
                putString(bearerTokenPref, bearerToken)
                apply()
            }
            Toast.makeText(activityContext, bearerToken, Toast.LENGTH_LONG).show()
            return true
        }

        Toast.makeText(activityContext, "Login credentials incorrect.", Toast.LENGTH_SHORT).show()
        return false
    }
}