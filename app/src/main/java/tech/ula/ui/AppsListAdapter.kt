package tech.ula.ui

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import tech.ula.R
import tech.ula.model.entities.App
import tech.ula.utils.AppDetails

class AppsListAdapter(
    private val activity: Activity,
    private val clickHandler: AppsClickHandler
) : RecyclerView.Adapter<AppsListAdapter.ViewHolder>() {

    private val activeApps = arrayListOf<App>()
    private val apps = mutableListOf<App>()
    private val firstDisplayCategory = "distribution"
    private val unselectedApp = App(name = "unselected")

    interface AppsClickHandler {
        fun onClick(app: App)
        fun createContextMenu(menu: Menu)
    }

    class ViewHolder(row: View) : RecyclerView.ViewHolder(row) {
        var separator: ConstraintLayout? = row.findViewById(R.id.app_list_separator)
        var separatorText: TextView? = row.findViewById(R.id.list_item_separator_text)
        var appDetails: ConstraintLayout? = row.findViewById(R.id.layout_app_details)
        var imageView: ImageView? = row.findViewById(R.id.apps_icon)
        var appName: TextView? = row.findViewById(R.id.apps_name)
    }

    var contextMenuItem: App = unselectedApp

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_app, parent, false))
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val app = apps[position]
        setSeparator(app, position, viewHolder)
        setItemDetails(app, viewHolder)
        setAppActivity(app, viewHolder)
        setItemListeners(app, viewHolder)
        setItemAnimation(app, position, viewHolder)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.itemView.clearAnimation()
    }

    fun updateApps(newApps: List<App>) {
        val diff = newApps.minus(apps)
        for (app in diff) insertAppIntoView(app)
    }

    fun updateActiveApps(newActiveApps: List<App>) {
        val diff = getActiveAppsDiff(newActiveApps)
        for (app in diff) {
            val index = apps.indexOf(app)
            notifyItemChanged(index)
        }
        activeApps.clear()
        activeApps.addAll(newActiveApps)
    }

    private fun setSeparator(app: App, position: Int, viewHolder: ViewHolder) {
        if (position > 0 && apps[position - 1].category == app.category) {
            viewHolder.separator?.visibility = View.GONE
        } else {
            viewHolder.separator?.visibility = View.VISIBLE
        }
    }

    private fun setItemDetails(app: App, viewHolder: ViewHolder) {
        val appDetails = AppDetails(activity.filesDir.path, activity.resources)
        viewHolder.appName?.text = app.name.capitalize()
        viewHolder.separatorText?.text = app.category.capitalize()
        viewHolder.imageView?.setImageURI(appDetails.findIconUri(app.name))
    }

    private fun setAppActivity(app: App, viewHolder: ViewHolder) {
        val appIsActive = activeApps.contains(app)
        val backgroundColor = if (appIsActive) {
            R.color.colorAccent
        } else {
            R.color.colorPrimaryDark
        }
        viewHolder.appDetails?.setBackgroundResource(backgroundColor)
    }

    private fun setItemListeners(app: App, viewHolder: ViewHolder) {
        viewHolder.itemView.setOnClickListener {
            clickHandler.onClick(app)
        }
        viewHolder.itemView.setOnCreateContextMenuListener { menu, _, _ ->
            contextMenuItem = app
            clickHandler.createContextMenu(menu)
        }
    }

    private fun setItemAnimation(app: App, position: Int, viewHolder: ViewHolder) {
        if (app.hasBeenAnimated()) return
        val animation = AnimationUtils.loadAnimation(activity, R.anim.item_animation_from_right)
        animation.startOffset = position * 5L
        viewHolder.itemView.animation = animation
        app.displayedAnimation()
    }

    private fun insertAppIntoView(app: App) {
        val foundIndex = apps.binarySearch(app, compareBy(
                // Sort the list by three criteria of descending importance
                { it.category != firstDisplayCategory },
                { it.category },
                { it.name }
        ))
        // Binary search returns [- (position to insert)] - 1 if an element is not already present
        val index = -(foundIndex) - 1
        apps.add(index, app)
        notifyItemInserted(index)
        notifyItemChanged(index + 1)
    }

    private fun getActiveAppsDiff(newActiveApps: List<App>): List<App> {
        val diffNewOld = newActiveApps.minus(activeApps)
        return if (diffNewOld.isNotEmpty()) diffNewOld
        else activeApps.minus(newActiveApps)
    }

    private fun App.hasBeenAnimated(): Boolean {
        val prefs = activity.getSharedPreferences("apps", Context.MODE_PRIVATE)
        return prefs.getBoolean("${this.name}HasBeenAnimated", false)
    }

    private fun App.displayedAnimation() {
        val prefs = activity.getSharedPreferences("apps", Context.MODE_PRIVATE)
        val app = this
        with(prefs.edit()) {
            putBoolean("${app.name}HasBeenAnimated", true)
            apply()
        }
    }
}