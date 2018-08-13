package tech.ula.model.entities

data class Asset(
    val name: String,
    val type: String,
    val remoteTimestamp: Long,
    val concatenatedName: String = "$type:$name",
    val pathName: String = "$type/$name"
)