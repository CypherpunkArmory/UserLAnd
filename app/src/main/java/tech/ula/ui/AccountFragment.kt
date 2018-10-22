package tech.ula.ui

import android.app.Activity
import android.os.Bundle
import android.os.StrictMode
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.frag_account.*
import kotlinx.android.synthetic.main.frag_account_login.*
import kotlinx.android.synthetic.main.frag_account_register.*
import tech.ula.R

class AccountFragment : Fragment() {

    private lateinit var activityContext: Activity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        return inflater.inflate(R.layout.frag_account, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activityContext = activity!!
        inflateViewStubWithNewFragment(R.layout.frag_account_login)
    }

    fun inflateViewStubWithNewFragment(newFragmentId: Int) {
        frag_account_layout.removeAllViews()

        val newView = LayoutInflater.from(activityContext).inflate(newFragmentId, frag_account_layout, false)
        frag_account_layout.addView(newView)

        when (newFragmentId) {
            R.layout.frag_account_login -> {
                activityContext.register_prompt.setOnClickListener {
                    inflateViewStubWithNewFragment(R.layout.frag_account_register)
                }
            }
            R.layout.frag_account_register -> {
                activityContext.login_prompt.setOnClickListener {
                    inflateViewStubWithNewFragment(R.layout.frag_account_login)
                }
            }
        }
    }
}