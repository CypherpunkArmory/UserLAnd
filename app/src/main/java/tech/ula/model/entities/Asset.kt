package tech.ula.model.entities

data class Asset(
    val name: String,
    val distributionType: String, // Overloaded: Could be "all" or distribution type
    val architectureType: String, // Overloaded: Could be "all" or architecture type
    val remoteTimestamp: Long,
    val concatenatedName: String = "$distributionType:$name",
    val pathName: String = "$distributionType/$name",
    val isLarge: Boolean = name.contains("rootfs.tar.gz")
)