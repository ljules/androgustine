package fr.augustine.androgustine.data.imports

object SimAugustineImportRepository {
    private var currentImport: SimAugustineImport? = null
    private var currentCircuit: SimAugustineImportedCircuit? = null

    fun save(sessionImport: SimAugustineImport, circuit: SimAugustineImportedCircuit) {
        currentImport = sessionImport
        currentCircuit = circuit
    }

    fun getCurrent(): SimAugustineImport? = currentImport

    fun getCurrentCircuit(): SimAugustineImportedCircuit? = currentCircuit
}
