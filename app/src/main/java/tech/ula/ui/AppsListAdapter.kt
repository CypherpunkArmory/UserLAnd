package tech.ula.ui

import android.app.Activity
import android.content.Context
import android.os.BaseBundle
import android.os.Bundle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.utils.AppDetails
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

class AppsListAdapter(
    private val activity: Activity
//    private val onAppsItemClicked: OnAppsItemClicked,
//    private val onAppsCreateContextMenu: OnAppsCreateContextMenu
) : RecyclerView.Adapter<AppsListAdapter.ViewHolder>() {

    private lateinit var lastSelectedAppContextItem: ViewHolder
    private val activeApps = arrayListOf<App>()
    private val apps = mutableListOf<App>()

    interface OnAppsItemClicked {
        fun onAppsItemClicked(appsItemClicked: ViewHolder)
    }

    interface OnAppsCreateContextMenu {
        fun onAppsCreateContextMenu(menu: ContextMenu, v: View, selectedListItem: ViewHolder)
    }

    class ViewHolder(row: View) : RecyclerView.ViewHolder(row) {
        var separator: ConstraintLayout? = row.findViewById(R.id.app_list_separator)
        var separatorText: TextView? = row.findViewById(R.id.list_item_separator_text)
        var imageView: ImageView? = row.findViewById(R.id.apps_icon)
        var appName: TextView? = row.findViewById(R.id.apps_name)
    }

    private val firstDisplayCategory = "distribution"

    fun updateApps(newApps: List<App>) {
        val diff = newApps.minus(apps)
        for (app in diff) {
            val index = - apps.binarySearch(app, compareBy(
                { it.category != firstDisplayCategory },
                { it.category },
                { it.name }
            )) - 1
            apps.add(index, app)
            notifyItemInserted(index)
        }
    }

//    fun updateActiveApps(newActiveApps: List<App>) {
//        val diffCallBack = AppsListDiffCallBack(appsAndSeparators, oldActiveApps = activeApps, newActiveApps = newActiveApps)
//        val diffResult = DiffUtil.calculateDiff(diffCallBack)
//        activeApps.clear()
//        activeApps.addAll(newActiveApps)
//
//        diffResult.dispatchUpdatesTo(this)
//    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_app, parent, false))
    }

    private fun App.hasBeenAnimated(): Boolean {
        val prefs = activity.getSharedPreferences("apps", Context.MODE_PRIVATE)
        return prefs.getBoolean("${this.name}HasBeenAnimated", false)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val app = apps[position]
        val appDetails = AppDetails(activity.filesDir.path, activity.resources)
        viewHolder.appName?.text = app.name.capitalize()
        viewHolder.separatorText?.text = app.category.capitalize()
        viewHolder.imageView?.setImageURI(appDetails.findIconUri(app.name))

        val appIsActive = activeApps.contains(app)
        if (appIsActive)  {
            viewHolder.itemView.setBackgroundResource(R.color.colorAccent)
        } else {
            viewHolder.itemView.setBackgroundResource(R.color.colorPrimaryDark)
        }

        if (position > 0 && apps[position - 1].category == app.category) {
            viewHolder.separator?.visibility = View.GONE
        } else {
            viewHolder.separator?.visibility = View.VISIBLE
        }

        if (app.hasBeenAnimated()) return
        val animation = AnimationUtils.loadAnimation(activity, R.anim.item_animation_from_right)
        animation.startOffset = position * 5L
        viewHolder.itemView.animation = animation
    }

//    private fun handleBindViewHolder(viewHolder: ViewHolder, position: Int, changes: MutableList<Any>?) {
//        val item = appsAndSeparators[position]
//
//        bindOnClick(viewHolder, item, onAppsItemClicked)
//        bindOnCreateContextMenu(viewHolder, onAppsCreateContextMenu, item)
//
//        when (item) {
//            is AppSeparatorItem -> {
//                viewHolder.separatorText?.text = item.category
//            }
//            is AppItem -> {
//                viewHolder.itemView.isLongClickable = true
//                val app = item.app
//                val appIsActive = activeApps.contains(app)
//                if (appIsActive) {
//                    viewHolder.itemView.setBackgroundResource(R.color.colorAccent)
//                } else {
//                    viewHolder.itemView.setBackgroundResource(R.color.colorPrimaryDark)
//                }
//
//                val appDetailer = AppDetails(activity.filesDir.path, activity.resources)
//                viewHolder.imageView?.setImageURI(appDetailer.findIconUri(app.name))
//                viewHolder.appName?.text = app.name.capitalize()
//
//                handleFirstAnimationRun(viewHolder, position)
//
//                if (changes != null && changes.isNotEmpty()) {
//                    val bundle = changes.first() as BaseBundle
//                    if (bundle.get("activeStateChange") as Boolean) return
//                    if (bundle.getInt("changedItemPosition") == position) {
//                        setAnimation(viewHolder.itemView, position)
//                    }
//                }
//            }
//        }
//    }

//    private fun handleFirstAnimationRun(viewHolder: ViewHolder, position: Int) {
//        val prefs = activity.getSharedPreferences("apps", Context.MODE_PRIVATE)
//        val firstRun = prefs.getBoolean("firstRun", true)
//        val savedAppsCount = prefs.getInt("savedAppsCount", 2)
//
//        if (firstRun || savedAppsCount <= appsAndSeparators.count()) {
//            prefs.edit().putInt("savedAppsCount", savedAppsCount + 1).apply()
//            setAnimation(viewHolder.itemView, position, delayEffect = true)
//            if (savedAppsCount == appsAndSeparators.count()) {
//                prefs.edit().putBoolean("firstRun", false).apply()
//            }
//        }
//    }

//    private fun bindOnClick(viewHolder: ViewHolder, selectedListItem: AppsListItem, onAppsItemClicked: OnAppsItemClicked) {
//        viewHolder.itemView.setOnClickListener {
//            onAppsItemClicked.onAppsItemClicked(selectedListItem)
//        }
//    }
//
//    private fun setAnimation(viewToAnimate: View, position: Int, delayEffect: Boolean = false) {
//        val animation = AnimationUtils.loadAnimation(activity, R.anim.item_animation_from_right)
//        viewToAnimate.startAnimation(animation)
//        if (delayEffect) {
//            val animationDelay = 100L
//            animation.startOffset = position * animationDelay
//        }
//    }
//
//    private fun bindOnCreateContextMenu(viewHolder: ViewHolder, onAppsCreateContextMenu: OnAppsCreateContextMenu, selectedListItem: AppsListItem) {
//        viewHolder.itemView.setOnCreateContextMenuListener { menu, v, _ ->
//            onAppsCreateContextMenu.onAppsCreateContextMenu(menu, v, selectedListItem)
//        }
//    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.itemView.clearAnimation()
    }

//    fun getLastSelectedContextItem(): AppsListItem {
//        return lastSelectedAppContextItem
//    }
//
//    fun setLastSelectedContextItem(appsListItem: AppsListItem) {
//        lastSelectedAppContextItem = appsListItem
//    }
}

///**
// * Default parameters allow updates in terms of changes to both the list and app activity.
// * When changing one, the other will simply default to using the same thing for comparison.
// */
//class AppsListDiffCallBack(
//    private val oldAppsItemList: List<AppsListItem> = listOf(),
//    private val newAppsItemList: List<AppsListItem> = oldAppsItemList,
//    private val oldActiveApps: List<App> = listOf(),
//    private val newActiveApps: List<App> = oldActiveApps
//) : DiffUtil.Callback() {
//
//    private var activeStateChange = false
//
//    override fun getOldListSize(): Int {
//        return oldAppsItemList.size
//    }
//
//    override fun getNewListSize(): Int {
//        return newAppsItemList.size
//    }
//
//    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
//        val oldApp = oldAppsItemList[oldItemPosition]
//        val newApp = newAppsItemList[newItemPosition]
//
//        if (oldApp is AppSeparatorItem && newApp is AppSeparatorItem) {
//            if (oldApp.category == newApp.category) return true
//        } else if (oldApp is AppItem && newApp is AppItem) {
//            if (oldApp.app.name == newApp.app.name) {
//                return true
//            }
//        }
//
//        return false
//    }
//
//    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
//        return areAppsListItemsSame(oldItemPosition, newItemPosition)
//    }
//
//    private fun areAppsListItemsSame(oldAppsItemPosition: Int, newAppsItemPosition: Int): Boolean {
//        val oldAppsItem = oldAppsItemList[oldAppsItemPosition]
//        val newAppsItem = newAppsItemList[newAppsItemPosition]
//        if (oldAppsItem is AppSeparatorItem && newAppsItem is AppSeparatorItem) {
//            if (oldAppsItem.category == newAppsItem.category) {
//                return true
//            }
//        } else if (oldAppsItem is AppItem && newAppsItem is AppItem) {
//            val oldAppIsActive = oldActiveApps.contains(oldAppsItem.app)
//            val newAppIsActive = newActiveApps.contains(newAppsItem.app)
//
//            if (oldAppIsActive != newAppIsActive) {
//                activeStateChange = true
//            }
//
//            if (oldAppsItem.app.category == newAppsItem.app.category &&
//                    oldAppsItem.app.name == newAppsItem.app.name &&
//                    oldAppsItem.app.version == newAppsItem.app.version &&
//                    oldAppsItem.app.filesystemRequired == newAppsItem.app.filesystemRequired &&
//                    oldAppsItem.app.isPaidApp == newAppsItem.app.isPaidApp &&
//                    oldAppsItem.app.supportsCli == newAppsItem.app.supportsCli &&
//                    oldAppsItem.app.supportsGui == newAppsItem.app.supportsGui &&
//                    oldAppIsActive == newAppIsActive) {
//                return true
//            }
//        }
//        return false
//    }
//
//    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
//        val diffBundle = Bundle()
//
//        if (areAppsListItemsSame(oldItemPosition, newItemPosition)) {
//            diffBundle.putInt("changedItemPosition", newItemPosition)
//        }
//        if (activeStateChange) diffBundle.putBoolean("activeStateChange", activeStateChange)
//        return diffBundle
//    }
//}