package tech.ula.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import android.widget.Toast
import tech.ula.R
import tech.ula.model.entities.Session

class SessionEditFragment : Fragment() {
    var editExisting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_session_edit, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_edit, menu)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = arguments?.getParcelable("session") as Session
        editExisting = arguments?.getBoolean("editExisting") ?: false
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if(editExisting) Toast.makeText(activity!!, "editExisting", Toast.LENGTH_LONG).show()
        else Toast.makeText(activity!!, "not existing", Toast.LENGTH_LONG).show()
    }
}