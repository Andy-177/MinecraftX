package paper.plugin.lugin.module

data class Manifest(
    val name: String,
    val version: String,
    val main: String,
    val description: String?,
    val author: String,
    val depends: DependsSpec?,
    val conflicts: ConflictsSpec?,
    val provides: List<String>?,
    val expose: Boolean
)

data class DependsSpec(
    val required: List<String>?,
    val optional: List<String>?
)

data class ConflictsSpec(
    val `break`: List<String>?,
    val incompatible: List<String>?
)

data class DependencyRef(
    val packageName: String,
    val description: String?
)
