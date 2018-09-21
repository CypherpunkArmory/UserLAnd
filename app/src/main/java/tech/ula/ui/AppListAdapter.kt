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
import tech.ula.model.entities.Session
import tech.ula.utils.LocalFileLocator

class AppListAdapter(
        private val activity: Activity,
        private val apps: List<App>,
        private val activeSessions: List<Session>) : BaseAdapter() {
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

        val app = apps[position]

        val activeAppSessions = activeSessions.filter { it.name == app.name }
        val appIsActive = activeAppSessions.isNotEmpty()
        if (appIsActive) {
            view?.setBackgroundResource(R.color.colorAccent)
        } else {
            view?.setBackgroundResource(R.color.colorPrimaryDark)
        }

        val localFileLocator = LocalFileLocator(activity.filesDir.path, activity.resources)
        viewHolder.imageView.setImageURI(localFileLocator.findIconUri(app.name))
        viewHolder.appName.text = app.name

        return view as View
    }

    override fun getItem(position: Int): App {
        return apps[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return apps.size
    }
}