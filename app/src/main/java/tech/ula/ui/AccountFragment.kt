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
import kotlinx.android.synthetic.main.frag_account.*
import kotlinx.android.synthetic.main.frag_account_info.*
import kotlinx.android.synthetic.main.frag_account_login.*
import kotlinx.android.synthetic.main.frag_account_register.*
import tech.ula.R

class AccountFragment : Fragment() {

    private val currentUser = "currentUser"
    private lateinit var activityContext: Activity
    private lateinit var prefs: SharedPreferences

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
        val currentUser = prefs.getString(currentUser, "") ?: ""
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
            R.layout.frag_account_login -> {
                activityContext.register_prompt.setOnClickListener {
                    inflateViewStubWithNewFragment(R.layout.frag_account_register)
                }

                activityContext.btn_login.setOnClickListener {
                    inflateViewStubWithNewFragment(R.layout.frag_account_info)
                }
            }
            R.layout.frag_account_register -> {
                activityContext.login_prompt.setOnClickListener {
                    inflateViewStubWithNewFragment(R.layout.frag_account_login)
                    setLoggedInUser("Thomas")
                }
            }
            R.layout.frag_account_info -> {
                activityContext.btn_logout.setOnClickListener {
                    inflateViewStubWithNewFragment(R.layout.frag_account_login)
                    logoutCurrentUser()
                }
            }
        }
    }

    private fun logoutCurrentUser() {
        with(prefs.edit()) {
            putString(currentUser, "")
            apply()
        }
    }

    private fun setLoggedInUser(username: String): Boolean {
        with(prefs.edit()) {
            putString(currentUser, username)
            apply()
        }

        return true
    }
}