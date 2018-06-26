package tech.ula.ui

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import tech.ula.R

class HelpFragment : Fragment() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.frag_help)
//        setSupportActionBar(toolbar)
//
//
//        help_toc_button.setOnClickListener { navigateToToc() }
//        github_logo.setOnClickListener {
//            val intent = Intent("android.intent.action.VIEW", Uri.parse("https://github.com/CypherpunkArmory/UserLAnd/issues"))
//            startActivity(intent)
//        }
//    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // TODO make buttons functional
        return inflater.inflate(R.layout.frag_help, container, false)
    }

//    fun navigateToToc(): Boolean {
//        val intent = Intent(this, TermsAndConditionsActivity::class.java)
//        startActivity(intent)
//        return true
//    }
}