package tech.ula.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import tech.ula.R
import tech.ula.model.entities.App

class AppDetailsFragment : Fragment() {

    private val app: App by lazy {
        arguments?.getParcelable("app") as App
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appsTitle = view.findViewById<TextView>(R.id.apps_title)
        val appsIcon = view.findViewById<ImageView>(R.id.apps_icon)
        appsTitle.text = app.name
        appsIcon.setImageResource(R.drawable.octave)
    }
}