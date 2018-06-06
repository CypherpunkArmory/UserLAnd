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
import tech.ula.database.models.Session

class SessionListAdapter(private var activity: Activity, private var items: List<Session>) : BaseAdapter() {
    private class ViewHolder(row: View) {
        var imageViewActive: ImageView = row.findViewById(R.id.image_list_item_active)
        var textViewServiceType: TextView = row.findViewById(R.id.text_list_item_service_type)
        var textViewSessionName: TextView = row.findViewById(R.id.text_list_item_session_name)
        var textViewFilesystemName: TextView = row.findViewById(R.id.text_list_item_filesystem_name)
    }

    private var activeSession: Session? = null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View?
        val viewHolder: ViewHolder
        if (convertView == null) {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.list_item_session, null)
            viewHolder = ViewHolder(view)
            view?.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val session = items[position]

        if(session.active) {
            viewHolder.imageViewActive.setImageResource(R.drawable.ic_check_circle_green_24dp)
        }
        else {
            viewHolder.imageViewActive.setImageResource(R.drawable.ic_block_red_24dp)
        }

        viewHolder.textViewServiceType.text = session.serviceType
        viewHolder.textViewSessionName.text = session.name
        viewHolder.textViewFilesystemName.text = session.filesystemName

        return view as View
    }

    override fun isEnabled(position: Int): Boolean {
        
        return super.isEnabled(position)
    }

    override fun getItem(position: Int): Session {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return items.size
    }
}