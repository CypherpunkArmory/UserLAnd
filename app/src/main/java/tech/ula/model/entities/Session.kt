package tech.ula.model.entities

import android.os.Parcelable
import androidx.room.* // ktlint-disable no-wildcard-imports
import kotlinx.android.parcel.Parcelize

fun String.toServiceType(): ServiceType {
    return when (this) {
        "ssh" -> ServiceType.Ssh
        "vnc" -> ServiceType.Vnc
        "xsdl" -> ServiceType.Xsdl
        else -> ServiceType.Unselected
    }
}

sealed class ServiceType : Parcelable {
    @Parcelize
    object Unselected : ServiceType() {
        override fun toString(): String {
            return "unselected"
        }
    }

    @Parcelize
    object Ssh : ServiceType() {
        override fun toString(): String {
            return "ssh"
        }
    }

    @Parcelize
    object Vnc : ServiceType() {
        override fun toString(): String {
            return "vnc"
        }
    }

    @Parcelize
    object Xsdl : ServiceType() {
        override fun toString(): String {
            return "xsdl"
        }
    }
}

class ServiceTypeConverter {
    @TypeConverter
    fun fromString(value: String): ServiceType {
        return value.toServiceType()
    }

    @TypeConverter
    fun fromServiceType(value: ServiceType): String {
        return value.toString()
    }
}

fun String.toServiceLocation(): ServiceLocation {
    return when (this) {
        "local" -> ServiceLocation.Local
        "remote" -> ServiceLocation.Remote
        else -> ServiceLocation.Unselected
    }
}

sealed class ServiceLocation : Parcelable {
    @Parcelize
    object Unselected : ServiceLocation() {
        override fun toString(): String {
            return "unselected"
        }
    }

    @Parcelize
    object Local : ServiceLocation() {
        override fun toString(): String {
            return "local"
        }
    }

    @Parcelize
    object Remote : ServiceLocation() {
        override fun toString(): String {
            return "remote"
        }
    }
}

class ServiceLocationConverter {
    @TypeConverter
    fun fromString(value: String): ServiceLocation {
        return value.toServiceLocation()
    }

    @TypeConverter
    fun fromServiceLocation(value: ServiceLocation): String {
        return value.toString()
    }
}

@Parcelize
@Entity(tableName = "session",
        foreignKeys = [ForeignKey(
                entity = Filesystem::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("filesystemId"),
                onDelete = ForeignKey.CASCADE)],
        indices = [
            Index(value = ["filesystemId"])
        ])
@TypeConverters(ServiceTypeConverter::class, ServiceLocationConverter::class)
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    var name: String = "",
    var filesystemId: Long,
    var filesystemName: String = "",
    var active: Boolean = false,
    var username: String = "",
    var password: String = "",
    var vncPassword: String = "",
    var serviceType: ServiceType = ServiceType.Unselected,
    var serviceLocation: ServiceLocation = ServiceLocation.Unselected,
    var port: Long = 2022, // TODO This can be removed. Any eventual port managing should be done at a high     er abstraction.
    var pid: Long = 0,
    var geometry: String = "",
    val isAppsSession: Boolean = false
) : Parcelable {
    override fun toString(): String {
        return "Session(id=$id, name=$name, filesystemId=$filesystemId, filesystemName=" +
                "$filesystemName, active=$active, serviceType=$serviceType, port=$port, pid=" +
                "$pid, isAppsSession=$isAppsSession)"
    }
}