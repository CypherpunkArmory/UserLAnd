package tech.ula.ui

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import tech.ula.R
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.utils.LocalFileLocator

class SessionListAdapter(
    private var activity: Activity,
    private val sessions: List<Session>,
    private val filesystems: List<Filesystem>
) : BaseAdapter() {

    private class ViewHolder(row: View) {
        var textViewServiceType: TextView? = row.findViewById(R.id.text_list_item_service_type)
        var textViewSessionName: TextView? = row.findViewById(R.id.text_list_item_session_name)
        var textViewFilesystemName: TextView? = row.findViewById(R.id.text_list_item_filesystem_name)
        var imageViewFilesystemIcon: ImageView? = row.findViewById(R.id.image_list_item_filesystem_icon)
        var separatorText: TextView? = row.findViewById(R.id.list_item_separator_text)
    }

    private val ITEM_VIEW_TYPE_SESSION = 0
    private val ITEM_VIEW_TYPE_SEPARATOR = 1
    private val ITEM_VIEW_TYPE_COUNT = 2

    private val sessionsAndSeparators: List<SessionListItem> by lazy {
        val listBuilder = arrayListOf<SessionListItem>()
        val separatorsAndSessions = HashMap<String, ArrayList<Session>>()

        for (session in sessions) {
            if (session.filesystemName == "apps") {
                separatorsAndSessions.getOrPut("apps") { arrayListOf() }.add(session)
            } else {
                separatorsAndSessions.getOrPut("custom") { arrayListOf() }.add(session)
            }
        }

        separatorsAndSessions.forEach {
            (separatorText, sectionSessions) ->
            listBuilder.add(SessionSeparatorItem(separatorText))
            sectionSessions.forEach { listBuilder.add(SessionItem(it)) }
        }
        listBuilder.toList()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View?
        val viewHolder: ViewHolder

        val item = sessionsAndSeparators[position]

        if (convertView == null) {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = when (item) {
                is SessionItem -> inflater.inflate(R.layout.list_item_session, parent, false)
                is SessionSeparatorItem -> inflater.inflate(R.layout.list_item_separator, parent, false)
            }
            viewHolder = ViewHolder(view)
            view?.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        when (item) {
            is SessionSeparatorItem -> {
                viewHolder.separatorText?.text = item.separatorText
            }
            is SessionItem -> {
                val session = item.session
                val filesystem = filesystems.find { it.id == session.filesystemId }!!

                if (session.active) {
                    view?.setBackgroundResource(R.color.colorAccent)
                } else {
                    view?.setBackgroundResource(R.color.colorPrimaryDark)
                }

                val localFileLocator = LocalFileLocator(activity.filesDir.path, activity.resources)
                viewHolder.textViewServiceType?.text = session.serviceType
                viewHolder.textViewSessionName?.text = session.name
                viewHolder.textViewFilesystemName?.text = session.filesystemName
                viewHolder.imageViewFilesystemIcon?.setImageURI(localFileLocator.findIconUri(filesystem.distributionType))
            }
        }

        return view as View
    }

    override fun getItem(position: Int): SessionListItem {
        return sessionsAndSeparators[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return sessionsAndSeparators.size
    }

    override fun getViewTypeCount(): Int {
        return ITEM_VIEW_TYPE_COUNT
    }

    override fun getItemViewType(position: Int): Int {
        return when (sessionsAndSeparators[position]) {
            is SessionItem -> ITEM_VIEW_TYPE_SESSION
            is SessionSeparatorItem -> ITEM_VIEW_TYPE_SEPARATOR
        }
    }

    override fun isEnabled(position: Int): Boolean {
        return when (sessionsAndSeparators[position]) {
            is SessionItem -> true
            is SessionSeparatorItem -> false
        }
    }
}

sealed class SessionListItem
data class SessionItem(val session: Session) : SessionListItem()
data class SessionSeparatorItem(val separatorText: String) : SessionListItem()