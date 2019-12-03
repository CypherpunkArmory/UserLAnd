package tech.ula.model.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import android.os.Parcelable
import androidx.room.TypeConverters
import kotlinx.android.parcel.Parcelize

@Parcelize
@Entity(tableName = "apps", indices = [(Index(value = ["name"], unique = true))])
@TypeConverters(ServiceTypeConverter::class, ServiceLocationConverter::class)
data class App(
        @PrimaryKey
        val name: String,
        var category: String = "",
        var filesystemRequired: String = "",
        var supportsCli: Boolean = false,
        var supportsGui: Boolean = false,
        var supportsLocal: Boolean = false,
        var supportsRemote: Boolean = false,
        var serviceType: ServiceType = ServiceType.Unselected,
        var serviceLocation: ServiceLocation = ServiceLocation.Unselected,
        var isPaidApp: Boolean = false,
        var version: Long = 0
) : Parcelable