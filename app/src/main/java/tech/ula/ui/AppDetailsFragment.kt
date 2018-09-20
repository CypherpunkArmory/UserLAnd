package tech.ula.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.frag_app_details.*
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.utils.LocalFileLocator

class AppDetailsFragment : Fragment() {

    private val app: App by lazy {
        arguments?.getParcelable("app") as App
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_app_details, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activityContext = activity!!
        val localFileLocator = LocalFileLocator(activityContext.filesDir.path, activityContext.resources)

        apps_icon.setImageURI(localFileLocator.findIconUri(app.name))
        apps_title.text = app.name
        apps_description.text = (localFileLocator.findAppDescription(app.name))
    }
}