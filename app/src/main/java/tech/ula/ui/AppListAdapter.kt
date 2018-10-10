package tech.ula.ui

import android.app.Activity
import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.model.entities.Session
import tech.ula.utils.LocalFileLocator

class AppListAdapter(
    private val activity: Activity,
    private val onAppsItemClicked: OnAppsItemClicked,
    private val apps: List<App> = listOf(),
    private val activeSessions: List<Session> = listOf()
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    interface OnAppsItemClicked {
        fun onAppsItemClicked(appsItemClicked: AppsListItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = when (viewType) {
            ITEM_VIEW_TYPE_APP -> R.layout.list_item_app
            else -> R.layout.list_item_separator
        }
        val view = inflater.inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = appsAndSeparators[position]

        bind(viewHolder, item, onAppsItemClicked)

        when (item) {
            is AppSeparatorItem -> {
                viewHolder.separatorText?.text = item.category
            }
            is AppItem -> {
                val app = item.app
                val activeAppSessions = activeSessions.filter { it.name == app.name }
                val appIsActive = activeAppSessions.isNotEmpty()
                if (appIsActive) {
                    viewHolder.itemView.setBackgroundResource(R.color.colorAccent)
                } else {
                    viewHolder.itemView.setBackgroundResource(R.color.colorPrimaryDark)
                }

                val localFileLocator = LocalFileLocator(activity.filesDir.path, activity.resources)
                viewHolder.imageView?.setImageURI(localFileLocator.findIconUri(app.name))
                viewHolder.appName?.text = app.name.capitalize()
            }
        }
    }

    private fun bind(viewHolder: ViewHolder, appsListItem: AppsListItem, onAppsItemClicked: OnAppsItemClicked) {
        viewHolder.itemView.setOnClickListener {
                onAppsItemClicked.onAppsItemClicked(appsListItem)
        }
    }

    class ViewHolder(row: View) : RecyclerView.ViewHolder(row) {
        var imageView: ImageView? = row.findViewById(R.id.apps_icon)
        var appName: TextView? = row.findViewById(R.id.apps_name)
        var separatorText: TextView? = row.findViewById(R.id.list_item_separator_text)
    }

    private val ITEM_VIEW_TYPE_APP = 0
    private val ITEM_VIEW_TYPE_SEPARATOR = 1
    private val ITEM_VIEW_TYPE_COUNT = 2

    private val firstDisplayCategory = "distribution"
    private val freeAnnotation = activity.resources.getString(R.string.free_annotation)
    private val paidAnnotation = activity.resources.getString(R.string.paid_annotation)

    private val appsAndSeparators: List<AppsListItem> by lazy {
        val listBuilder = arrayListOf<AppsListItem>()
        val categoriesAndApps = HashMap<String, ArrayList<App>>()

        for (app in apps) {
            categoriesAndApps.getOrPut(app.category) { arrayListOf() }.add(app)
        }

        val categoriesAndAppsWithDistributionsFirst = LinkedHashMap<String, List<App>>()

        if (categoriesAndApps.containsKey(firstDisplayCategory)) {
            categoriesAndAppsWithDistributionsFirst[firstDisplayCategory] =
                    categoriesAndApps.remove(firstDisplayCategory)!!
        }
        categoriesAndAppsWithDistributionsFirst.putAll(categoriesAndApps)

        categoriesAndAppsWithDistributionsFirst.forEach {
            (category, categoryApps) ->
            val categoryWithPaymentInformation = category + " " +
                    if (category == firstDisplayCategory) freeAnnotation else paidAnnotation
            listBuilder.add(AppSeparatorItem(categoryWithPaymentInformation.capitalize()))
            categoryApps.forEach { listBuilder.add(AppItem(it)) }
        }
        listBuilder.toList()
    }

    fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
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
                viewHolder.appName?.text = app.name.capitalize()
            }
        }

        return view as View
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return appsAndSeparators.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (appsAndSeparators[position]) {
            is AppItem -> ITEM_VIEW_TYPE_APP
            is AppSeparatorItem -> ITEM_VIEW_TYPE_SEPARATOR
        }
    }

    fun isEnabled(position: Int): Boolean {
        return when (appsAndSeparators[position]) {
            is AppItem -> true
            is AppSeparatorItem -> false
        }
    }
}

sealed class AppsListItem
data class AppItem(val app: App) : AppsListItem()
data class AppSeparatorItem(val category: String) : AppsListItem()
