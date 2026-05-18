package omnimesh.command1.disaster

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DisasterType(val displayName: String) {
    NORMAL("General Emergency"),
    EARTHQUAKE("Earthquake"),
    FLOOD("Flood"),
    CYCLONE("Cyclone"),
    INDUSTRIAL("Industrial Incident"),
}

class DisasterStateManager {
    private val _currentDisasterType = MutableStateFlow(DisasterType.NORMAL)
    val currentDisasterType: StateFlow<DisasterType> = _currentDisasterType.asStateFlow()

    fun setDisasterType(type: DisasterType) {
        _currentDisasterType.value = type
    }
}
