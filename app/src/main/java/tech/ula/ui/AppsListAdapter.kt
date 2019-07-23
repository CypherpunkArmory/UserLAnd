package tech.ula.ui

import android.app.Activity
import android.content.Context
import android.view.*
import androidx.recyclerview.widget.RecyclerView
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
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
            notifyItemChanged(index + 1)
        }
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

    private fun getActiveAppsDiff(newActiveApps: List<App>): List<App> {
        val diffNewOld = newActiveApps.minus(activeApps)
        return if (diffNewOld.isNotEmpty()) diffNewOld
        else activeApps.minus(newActiveApps)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_app, parent, false))
    }

    private fun App.hasBeenAnimated(): Boolean {
        val prefs = activity.getSharedPreferences("apps", Context.MODE_PRIVATE)
        return prefs.getBoolean("${this.name}HasBeenAnimated", false)
    }

    private fun App.displayedAnimation() {
        val prefs = activity.getSharedPreferences("apps", Context.MODE_PRIVATE)
        val app = this
        with (prefs.edit()) {
            putBoolean("${app.name}HasBeenAnimated", true)
            apply()
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val app = apps[position]
        val appDetails = AppDetails(activity.filesDir.path, activity.resources)
        viewHolder.appName?.text = app.name.capitalize()
        viewHolder.separatorText?.text = app.category.capitalize()
        viewHolder.imageView?.setImageURI(appDetails.findIconUri(app.name))

        val appIsActive = activeApps.contains(app)
        val backgroundColor = if (appIsActive)  {
            R.color.colorAccent
        } else {
            R.color.colorPrimaryDark
        }
        viewHolder.appDetails?.setBackgroundResource(backgroundColor)

        viewHolder.itemView.setOnClickListener {
            clickHandler.onClick(app)
        }
        viewHolder.itemView.setOnCreateContextMenuListener { menu, _, _ ->
            contextMenuItem = app
            clickHandler.createContextMenu(menu)
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
        app.displayedAnimation()
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
}