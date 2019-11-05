package tech.ula.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import kotlinx.android.synthetic.main.frag_account.*
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
