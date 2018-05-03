package tech.userland.userland

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.list_item.view.*
/*
class ListAdapter(val items : ArrayList<String>,
                  //val onLongClickListener: () -> Unit,
                  val context: Context)
    : RecyclerView.Adapter<ViewHolder>() {

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(context).inflate(R.layout.list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.listItem.text = items[position]
        /*
        holder.listItem.setOnLongClickListener {
            onLongClickListener()
            true
        }
        */
        /*
        holder.listItem.setOnClickListener {
            val popupMenu = PopupMenu(context, it)
            popupMenu.setOnMenuItemClickListener {
                popupMenuCallBack()
                true
            }
            popupMenu.inflate(R.menu.context_menu_sessions)
            try {
                val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
                fieldMPopup.isAccessible = true
                val mPopup = fieldMPopup.get(popupMenu)
                mPopup.javaClass
                        .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                        .invoke(mPopup, true)
            } catch (e: Exception){
                Log.e("Main", "Error showing menu icons.", e)
            } finally {
                popupMenu.show()
            }
        }
        */

        //holder.listItem.setOnCreateContextMenuListener()
    }
}

class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
    val listItem: TextVie
    w = view.text_stub
}*/