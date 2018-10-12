package tech.ula.ui

import android.app.Activity
import android.support.constraint.ConstraintLayout
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.model.entities.Session
import tech.ula.utils.LocalFileLocator
import kotlin.collections.ArrayList

class AppListAdapter(
    private val activity: Activity,
    private val onAppsItemClicked: OnAppsItemClicked,
    private val onAppsCreateContextMenu: OnAppsCreateContextMenu
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private lateinit var lastSelectedAppListItem: AppsListItem
    private val apps: ArrayList<App> = arrayListOf()
    private val activeSessions: ArrayList<Session> = arrayListOf()

    interface OnAppsItemClicked {
        fun onAppsItemClicked(appsItemClicked: AppsListItem)
    }

    interface OnAppsCreateContextMenu {
        fun onAppsCreateContextMenu(menu: ContextMenu, v: View, selectedListItem: AppsListItem)
    }

    class ViewHolder(row: View) : RecyclerView.ViewHolder(row) {
        var imageView: ImageView? = row.findViewById(R.id.apps_icon)
        var appName: TextView? = row.findViewById(R.id.apps_name)
        var separatorText: TextView? = row.findViewById(R.id.list_item_separator_text)
    }

    private val ITEM_VIEW_TYPE_APP = 0
    private val ITEM_VIEW_TYPE_SEPARATOR = 1

    private var lastPosition = -1

    private val firstDisplayCategory = "distribution"
    private val freeAnnotation = activity.resources.getString(R.string.free_annotation)
    private val paidAnnotation = activity.resources.getString(R.string.paid_annotation)

    private val appsAndSeparators: ArrayList<AppsListItem> = arrayListOf()

    fun createAppsItemListWithSeparators(newApps: List<App>): List<AppsListItem> {
        val listBuilder = arrayListOf<AppsListItem>()
        val categoriesAndApps = HashMap<String, ArrayList<App>>()

        for (app in newApps) {
            categoriesAndApps.getOrPut(app.category) { arrayListOf() }.add(app)
        }

        val categoriesAndAppsWithDistributionsFirst = LinkedHashMap<String, List<App>>()

        if (categoriesAndApps.containsKey(firstDisplayCategory)) {
            categoriesAndAppsWithDistributionsFirst[firstDisplayCategory] =
                    categoriesAndApps.remove(firstDisplayCategory)!!
        }

        val sortedCategoriesAndApps = categoriesAndApps.toSortedMap()
        categoriesAndAppsWithDistributionsFirst.putAll(sortedCategoriesAndApps)

        categoriesAndAppsWithDistributionsFirst.forEach {
            (category, categoryApps) ->
            val categoryWithPaymentInformation = category + " " +
                    if (category == firstDisplayCategory) freeAnnotation else paidAnnotation
            listBuilder.add(AppSeparatorItem(categoryWithPaymentInformation.capitalize()))

            val sortedCategoryApps = categoryApps.sortedWith(compareBy { it.name })
            sortedCategoryApps.forEach { listBuilder.add(AppItem(it)) }
        }

        return listBuilder
    }

    fun updateAppsAndSessions(newApps: List<App>, newActiveSessions: List<Session>) {

        val newAppsListItems = createAppsItemListWithSeparators(newApps)

        val diffCallback = AppsItemDiffCallBack(appsAndSeparators, newAppsListItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        apps.clear()
        apps.addAll(newApps)
        activeSessions.clear()
        activeSessions.addAll(newActiveSessions)
        appsAndSeparators.clear()
        appsAndSeparators.addAll(newAppsListItems)

        diffResult.dispatchUpdatesTo(this)
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

        bindOnClick(viewHolder, item, onAppsItemClicked, position)
        bindOnCreateContextMenu(viewHolder, onAppsCreateContextMenu, item)

        when (item) {
            is AppSeparatorItem -> {
                viewHolder.separatorText?.text = item.category
            }
            is AppItem -> {
                viewHolder.itemView.isLongClickable = true
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
                setAnimation(viewHolder.itemView, position)
            }
        }
    }

    private fun setAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            val animationDelay = 200L
            val animation = AnimationUtils.loadAnimation(activity, R.anim.item_animation_from_right)
            viewToAnimate.startAnimation(animation)
            animation.startOffset = position * animationDelay
            lastPosition = position
        }
    }

    private fun bindOnClick(viewHolder: ViewHolder, selectedListItem: AppsListItem, onAppsItemClicked: OnAppsItemClicked, position: Int) {
        viewHolder.itemView.setOnClickListener {
            onAppsItemClicked.onAppsItemClicked(selectedListItem)
        }
    }

    private fun bindOnCreateContextMenu(viewHolder: ViewHolder, onAppsCreateContextMenu: OnAppsCreateContextMenu, selectedListItem: AppsListItem) {
        viewHolder.itemView.setOnCreateContextMenuListener { menu, v, _ ->
            onAppsCreateContextMenu.onAppsCreateContextMenu(menu, v, selectedListItem)
        }
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

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.itemView.clearAnimation()
    }

    fun getLastSelectedAppsListItem(): AppsListItem {
        return lastSelectedAppListItem
    }

    fun setLastSelectedAppsListItem(appsListItem: AppsListItem) {
        lastSelectedAppListItem = appsListItem
    }
}

sealed class AppsListItem
data class AppItem(val app: App) : AppsListItem()
data class AppSeparatorItem(val category: String) : AppsListItem()

class AppsItemDiffCallBack(private var oldAppsItemList: List<AppsListItem>, private var newAppsItemList: List<AppsListItem>) : DiffUtil.Callback() {
    override fun getOldListSize(): Int {
        return oldAppsItemList.size
    }

    override fun getNewListSize(): Int {
        return newAppsItemList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldApp = oldAppsItemList[oldItemPosition]
        val newApp = newAppsItemList[newItemPosition]

        if (oldApp is AppSeparatorItem && newApp is AppSeparatorItem) {
            if (oldApp.category == newApp.category) {
                return true
            }
        } else if (oldApp is AppItem && newApp is AppItem) {
            if (oldApp.app.name == newApp.app.name) {
                return true
            }
        }

        return false
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldApp = oldAppsItemList[oldItemPosition]
        val newApp = newAppsItemList[newItemPosition]

        if (oldApp is AppSeparatorItem && newApp is AppSeparatorItem) {
            if (oldApp.category == newApp.category) {
                return true
            }
        } else if (oldApp is AppItem && newApp is AppItem) {
            if (oldApp.app.category == newApp.app.category &&
                    oldApp.app.name == newApp.app.name &&
                    oldApp.app.version == newApp.app.version &&
                    oldApp.app.filesystemRequired == newApp.app.filesystemRequired &&
                    oldApp.app.isPaidApp == newApp.app.isPaidApp &&
                    oldApp.app.supportsCli == newApp.app.supportsCli &&
                    oldApp.app.supportsGui == newApp.app.supportsGui) {
                return true
            }
        }
        return false
    }
}