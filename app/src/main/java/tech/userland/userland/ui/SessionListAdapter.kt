package tech.userland.userland.ui

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import tech.userland.userland.R
import tech.userland.userland.database.models.Session

class SessionListAdapter(private var activity: Activity, private var items: ArrayList<Session>) : BaseAdapter() {
    private class ViewHolder(row: View) {
        var imageViewActive: ImageView = row.findViewById(R.id.image_list_item_active)
        var textViewType: TextView = row.findViewById(R.id.text_list_item_type)
        var textViewName: TextView = row.findViewById(R.id.text_list_item_name)
    }

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
            viewHolder.imageViewActive.setImageResource(R.drawable.ic_check_white_24dp)
        }
        else {
            viewHolder.imageViewActive.setImageResource(R.drawable.ic_block_white_24dp)
        }

        viewHolder.textViewType.text = session.type
        viewHolder.textViewName.text = session.name

        return view as View
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