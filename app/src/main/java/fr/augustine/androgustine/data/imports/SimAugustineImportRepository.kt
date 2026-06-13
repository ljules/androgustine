package fr.augustine.androgustine.data.imports

object SimAugustineImportRepository {
    private var currentImport: SimAugustineImport? = null

    fun save(sessionImport: SimAugustineImport) {
        currentImport = sessionImport
    }

    fun getCurrent(): SimAugustineImport? = currentImport
}
