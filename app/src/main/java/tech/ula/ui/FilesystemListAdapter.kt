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
import tech.ula.model.entities.Filesystem
import tech.ula.utils.LocalFileLocator

class FilesystemListAdapter(private var activity: Activity, private var items: List<Filesystem>) : BaseAdapter() {
    private class ViewHolder(row: View) {
        var imageViewType: ImageView = row.findViewById(R.id.image_list_item_filesystem_type)
        var textViewName: TextView = row.findViewById(R.id.text_filesystem_name)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View?
        val viewHolder: ViewHolder
        if (convertView == null) {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.list_item_filesystem, parent, false)
            viewHolder = ViewHolder(view)
            view?.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val filesystem = items[position]

        val fileLocator = LocalFileLocator(activity.filesDir.path, activity.resources)
        viewHolder.imageViewType.setImageURI(fileLocator.findIconUri(filesystem.distributionType))
        viewHolder.textViewName.text = filesystem.name

        return view as View
    }

    override fun getItem(position: Int): Filesystem {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return items.size
    }
}