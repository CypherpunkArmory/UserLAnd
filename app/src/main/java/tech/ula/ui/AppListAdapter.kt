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
    private val activeSessions: List<Session>
) : BaseAdapter() {

    private class ViewHolder(row: View) {
        var imageView: ImageView? = row.findViewById(R.id.apps_icon)
        var appName: TextView? = row.findViewById(R.id.apps_name)
        var separatorText: TextView? = row.findViewById(R.id.list_item_separator_text)
    }

    private val ITEM_VIEW_TYPE_APP = 0
    private val ITEM_VIEW_TYPE_SEPARATOR = 1
    private val ITEM_VIEW_TYPE_COUNT = 2

    private val topListElement = "distribution"
    private val freeAnnotation = activity.resources.getString(R.string.free_annotation)
    private val paidAnnotation = activity.resources.getString(R.string.paid_annotation)

    private val appsAndSeparators: List<AppsListItem> by lazy {
        val listBuilder = arrayListOf<AppsListItem>()
        val categoriesAndApps = HashMap<String, ArrayList<App>>()

        for (app in apps) {
            categoriesAndApps.getOrPut(app.category) { arrayListOf() }.add(app)
        }

        val categoriesAndAppsWithDistributionsFirst = categoriesAndApps.toSortedMap(Comparator {
            first, second ->
            when {
                first == topListElement -> -1
                second == topListElement -> 1
                else -> 0
            }
        })

        categoriesAndAppsWithDistributionsFirst.forEach {
            (category, categoryApps) ->
            val categoryWithPaymentInformation = category + " " +
                    if (category == topListElement) freeAnnotation else paidAnnotation
            listBuilder.add(AppSeparatorItem(categoryWithPaymentInformation.capitalize()))
            categoryApps.forEach { listBuilder.add(AppItem(it)) }
        }
        listBuilder.toList()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View?
        val viewHolder: ViewHolder

        val item = appsAndSeparators[position]

        if (convertView == null) {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = when (item) {
                is AppSeparatorItem -> inflater.inflate(R.layout.list_item_separator, parent, false)
                is AppItem -> inflater.inflate(R.layout.list_item_app, parent, false)
            }

            viewHolder = ViewHolder(view)
            view?.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        when (item) {
            is AppSeparatorItem -> {
                viewHolder.separatorText?.text = item.category
            }
            is AppItem -> {
                val app = item.app
                val activeAppSessions = activeSessions.filter { it.name == app.name }
                val appIsActive = activeAppSessions.isNotEmpty()
                if (appIsActive) {
                    view?.setBackgroundResource(R.color.colorAccent)
                } else {
                    view?.setBackgroundResource(R.color.colorPrimaryDark)
                }

                val localFileLocator = LocalFileLocator(activity.filesDir.path, activity.resources)
                viewHolder.imageView?.setImageURI(localFileLocator.findIconUri(app.name))
                viewHolder.appName?.text = app.name
            }
        }

        return view as View
    }

    override fun getItem(position: Int): AppsListItem {
        return appsAndSeparators[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return appsAndSeparators.size
    }

    override fun getViewTypeCount(): Int {
        return ITEM_VIEW_TYPE_COUNT
    }

    override fun getItemViewType(position: Int): Int {
        return when (appsAndSeparators[position]) {
            is AppItem -> ITEM_VIEW_TYPE_APP
            is AppSeparatorItem -> ITEM_VIEW_TYPE_SEPARATOR
        }
    }

    override fun isEnabled(position: Int): Boolean {
        return when (appsAndSeparators[position]) {
            is AppItem -> true
            is AppSeparatorItem -> false
        }
    }
}

sealed class AppsListItem
data class AppItem(val app: App) : AppsListItem()
data class AppSeparatorItem(val category: String) : AppsListItem()
