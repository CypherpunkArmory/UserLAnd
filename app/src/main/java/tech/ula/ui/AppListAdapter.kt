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
import tech.ula.model.entities.App
import tech.ula.utils.IconLocator

class AppListAdapter(private var activity: Activity, private var items: List<App>) : BaseAdapter() {
    private class ViewHolder(row: View) {
        var imageView: ImageView = row.findViewById(R.id.apps_icon)
        var appName: TextView = row.findViewById(R.id.apps_name)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View?
        val viewHolder: ViewHolder
        if (convertView == null) {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.list_item_app, parent, false)

            viewHolder = ViewHolder(view)
            view?.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val app = items[position]

        val iconLocator = IconLocator(activity.filesDir.path, activity.resources)
        viewHolder.imageView.setImageURI(iconLocator.findFilesystemIconUri(app.filesystemRequired))
        viewHolder.appName.text = app.name

        return view as View
    }

    override fun getItem(position: Int): App {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return items.size
    }
}