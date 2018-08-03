package tech.ula.utils

data class Asset(
    val name: String,
    val type: String,
    val remoteTimestamp: Long,
    val qualifedName: String = "$type:$name"
)