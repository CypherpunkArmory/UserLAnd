package tech.ula.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_account.*
import kotlinx.android.synthetic.main.frag_session_edit.*
import tech.ula.R

class AccountFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_edit, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.menu_item_add) saveAccountInfo()
        else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        button_account_register.setOnClickListener {
            val intent = Intent("android.intent.action.VIEW", Uri.parse("https://userland.tech/signup"))
            startActivity(intent)
        }

        input_account_email.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                val accountPrefs = getActivity()?.getSharedPreferences("account", Context.MODE_PRIVATE) ?: return
                with (accountPrefs.edit()) {
                    putString("account_email", p0.toString())
                    commit()
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        input_account_password.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                val accountPrefs = getActivity()?.getSharedPreferences("account", Context.MODE_PRIVATE) ?: return
                with (accountPrefs.edit()) {
                    putString("account_password", p0.toString())
                    commit()
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        val accountPrefs = this.getActivity()?.getSharedPreferences("account", Context.MODE_PRIVATE)
        val accountEmail = accountPrefs?.getString("account_email", "")
        val accountPass = accountPrefs?.getString("account_password", "")
        input_account_email.setText(accountEmail)
        input_account_password.setText(accountPass)
    }

    private fun saveAccountInfo(): Boolean {
        val navController = NavHostFragment.findNavController(this)
        val accountPrefs = this.getActivity()?.getSharedPreferences("account", Context.MODE_PRIVATE) ?: return true
        with (accountPrefs.edit()) {
            putString("account_email", input_account_email.text.toString())
            putString("account_password", input_account_password.text.toString())
            commit()
        }
        navController.popBackStack()
        return true
    }

}
