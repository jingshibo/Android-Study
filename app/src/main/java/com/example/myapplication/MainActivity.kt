package com.example.myapplication

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel


/**
Regular classes
 */

//  Create one class that represents the whole screen state, This is useful because the UI can receive one state object.
data class ResearchUiState(
    val sampleId: String = "",
    val patientCode: String = "",
    val sessionName: String = "",
    val currentPatientId: Long? = null,
    val currentSessionId: Long? = null,
    val deviceConnectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val acquisitionState: AcquisitionState = AcquisitionState.IDLE,
    val measurementEntities: List<MeasurementEntity> = emptyList(),
    val latestValue: Double? = null,
    val isLoading: Boolean = false,
    val exportMessage: String = "",
    val message: String = ""
)

enum class DeviceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class AcquisitionState {
    IDLE,
    RECORDING,
    STOPPED,
    ERROR
}

fun getDeviceStatusText(
    state: DeviceConnectionState
): String {
    return when (state) {
        DeviceConnectionState.DISCONNECTED -> "Device disconnected"
        DeviceConnectionState.CONNECTING -> "Connecting to device..."
        DeviceConnectionState.CONNECTED -> "Device connected"
        DeviceConnectionState.ERROR -> "Device error"
    }
}

fun getAcquisitionStatusText(
    state: AcquisitionState
): String {
    return when (state) {
        AcquisitionState.IDLE -> "Ready to start"
        AcquisitionState.RECORDING -> "Recording"
        AcquisitionState.STOPPED -> "Recording stopped"
        AcquisitionState.ERROR -> "Acquisition error"
    }
}

// Cleans a string to make it safe for use as a file name.
fun safeFilename(text: String): String {
    return text
        .trim()
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
}

/**
ViewModel class
 */
class ResearchViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val measurementRepository = MeasurementRepository(application)

    // There is no remember here because this state lives inside the ViewModel.
    // The by keyword lets us use uiState like a normal variable instead of writing uiState.value.
    var uiState by mutableStateOf(ResearchUiState())
        private set

    fun updateSampleId(newSampleId: String) {
        uiState = uiState.copy(
            sampleId = newSampleId,
            exportMessage = ""
        )
    }

    fun connectDevice() {
        // This prevents starting two acquisition loops at the same time.
        if (uiState.deviceConnectionState == DeviceConnectionState.CONNECTING) {
            return
        }

        uiState = uiState.copy(
            deviceConnectionState = DeviceConnectionState.CONNECTING,
            message = "Connecting to device..."
        )

        viewModelScope.launch {
            delay(1000)

            uiState = uiState.copy(
                deviceConnectionState = DeviceConnectionState.CONNECTED,
                message = "Device connected"
            )
        }
    }

    fun disconnectDevice() {
        if (uiState.acquisitionState == AcquisitionState.RECORDING) {
            stopAcquisition()
        }

        uiState = uiState.copy(
            deviceConnectionState = DeviceConnectionState.DISCONNECTED,
            acquisitionState = AcquisitionState.IDLE,
            message = "Device disconnected"
        )
    }

    fun startAcquisition() {

        if (uiState.deviceConnectionState != DeviceConnectionState.CONNECTED) {
            uiState = uiState.copy(
                message = "Connect device before starting acquisition"
            )
            return
        }

        // This prevents starting two acquisition loops at the same time.
        if (uiState.acquisitionState == AcquisitionState.RECORDING) {
            return
        }

        uiState = uiState.copy(
            acquisitionState = AcquisitionState.RECORDING,
            message = "Acquisition started"
        )

        viewModelScope.launch {
            while (uiState.acquisitionState == AcquisitionState.RECORDING) { // When `isAcquiring` becomes `false`, the loop finishes.
                addMeasurement()
                delay(1000)
            }
        }
    }

    fun stopAcquisition() {
        // If the app is already idle or stopped, pressing Stop should do nothing.
        if (uiState.acquisitionState != AcquisitionState.RECORDING) {
            return
        }

        uiState = uiState.copy(
            acquisitionState = AcquisitionState.STOPPED,
            message = "Acquisition stopped"
        )
    }

    private fun addMeasurement() {

        if (uiState.sampleId.isBlank() || uiState.deviceConnectionState == DeviceConnectionState.DISCONNECTED) {
            return
        }

        val currentSessionId = uiState.currentSessionId

        val repetitionForThisSample =
            uiState.measurementEntities.count {
                it.sessionId == currentSessionId
            } + 1

        val newMeasurement = measurementRepository.createSimulatedMeasurement(
            sessionId = currentSessionId,
            repetition = repetitionForThisSample
        )

        viewModelScope.launch {
            try {
                measurementRepository.insertMeasurement(newMeasurement)

                val updatedMeasurements =
                    measurementRepository.getMeasurementsForSession(currentSessionId)

                uiState = uiState.copy(
                    measurementEntities = updatedMeasurements,
                    latestValue = newMeasurement.value,
                    message = "Measurement saved to database",
                    exportMessage = ""
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    message = "Database save failed"
                )
            }
        }
    }

    fun removeLastMeasurement() {
        uiState = uiState.copy(
            measurementEntities = uiState.measurementEntities.dropLast(1),
            exportMessage = ""
        )
    }

    fun clearMeasurements() {
        viewModelScope.launch {
            try {
                measurementRepository.deleteMeasurementsForSession(uiState.currentSessionId)

                uiState = uiState.copy(
                    measurementEntities = emptyList(),
                    message = "All measurements deleted for current session"
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    message = "Could not delete measurements"
                )
            }
        }
    }

    fun loadSavedMeasurements() {
        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                message = "Loading saved measurements..."
            )

            try {
                val loadedMeasurements = measurementRepository.getMeasurementsForSession(uiState.currentSessionId)

                uiState = uiState.copy(
                    measurementEntities = loadedMeasurements,
                    isLoading = false,
                    message = "Loaded ${loadedMeasurements.size} saved measurements."
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    message = "Could not load saved measurements: ${e.message}"
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                // Create a viewModel object
                val viewModel: ResearchViewModel = viewModel()
                // Draw UI using the viewModel input
                ResearchScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}

/**
UI Drawing
 */

@Composable
fun ResearchScreen(
    viewModel: ResearchViewModel, modifier: Modifier = Modifier
) { // This function is just for connecting viewModel variable to UI drawing function.
    // It separates the UI drawing process from viewModel inputs, so the code for drawing is independent

    // Getting viewModel value.
    val uiState = viewModel.uiState

    // Plot UI using obtained model values and functions
    ResearchScreenContent(
        uiState = uiState,
        onSampleIdChange = viewModel::updateSampleId,
        onConnect = viewModel::connectDevice,
        onDisconnect = viewModel::disconnectDevice,
        startAcquisition = viewModel::startAcquisition,
        stopAcquisition = viewModel::stopAcquisition,
        onClear = viewModel::clearMeasurements,
        onRemove = viewModel::removeLastMeasurement,
        onLoadSavedMeasurements = viewModel::loadSavedMeasurements,
    )

}


@Composable
fun ResearchScreenContent(
    // specifically for drawing the UI with viewModel inputs
    uiState: ResearchUiState,
    onSampleIdChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    startAcquisition: () -> Unit,
    stopAcquisition: () -> Unit,
    onRemove: () -> Unit,
    onClear: () -> Unit,
    onLoadSavedMeasurements: () -> Unit,
) {
    /**
    Create new variables
     */
    val context = LocalContext.current

    var pendingCsvText by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) { // load saved data when app screen opens
        onLoadSavedMeasurements()
    }

    // Specifically for file saving dialog processing
    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri: Uri? ->
            if (uri != null) {
                // 2. Generate the CSV text right here when needed
                val csvText = measurementListToCsv(uiState.measurementEntities)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvText.toByteArray())
                }
                Toast.makeText(context, "CSV exported successfully.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "CSV export cancelled.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val values = uiState.measurementEntities.map {
        it.value
    }

    val meanText = if (values.isNotEmpty()) {
        "%.3f".format(values.average())
    } else {
        "--"
    }

    val minText = values.minOrNull()?.let {
        "%.3f".format(it)
    } ?: "--"

    val maxText = values.maxOrNull()?.let {
        "%.3f".format(it)
    } ?: "--"

    /**
    Drawing UI
     */
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Research Measurement App",
            fontSize = 26.sp
        )

        if (uiState.isLoading) {
            Text("Loading Saved Data...")
        }

        if (uiState.message.isNotBlank()) {
            Text(uiState.message)
        }

        Button(
            onClick = {
                // toggle the button function
                when {
                    uiState.deviceConnectionState == DeviceConnectionState.CONNECTED -> onDisconnect()
                    else -> onConnect()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.deviceConnectionState != DeviceConnectionState.CONNECTING,
        ) {
            Text(
                text = if (uiState.deviceConnectionState == DeviceConnectionState.CONNECTED) {
                    "Disconnect"
                } else {
                    "Connect"
                }
            )
        }

        Text(
            text = when (uiState.deviceConnectionState) {
                DeviceConnectionState.CONNECTED -> {
                    "Device status: Connected"
                }

                DeviceConnectionState.CONNECTING -> {
                    "Device status: Connecting"
                }

                else -> {
                    "Device status: Disconnected"
                }
            }
        )

        if (uiState.sampleId.isBlank()) {
            Text("Please enter a sample ID before measuring.")
        }

        OutlinedTextField(
            value = uiState.sampleId,
            onValueChange = onSampleIdChange,
            label = {
                Text("Sample ID")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Latest value: ")

                Text(
                    text = uiState.latestValue?.let {
                        "%.3f".format(it)
                    } ?: "--",
                    fontSize = 40.sp
                )

                val acquisitionStatus =
                    if (uiState.acquisitionState == AcquisitionState.RECORDING) {
                        "Recording"
                    } else {
                        "Stopped"
                    }

                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Acquisition Status: $acquisitionStatus")
                    Text("Measurement Count: ${uiState.measurementEntities.size}")
                    Text("Mean: $meanText")
                    Text("Min: $minText")
                    Text("Max: $maxText")
                }

            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    // toggle the button function
                    when {
                        uiState.acquisitionState == AcquisitionState.RECORDING -> stopAcquisition()
                        else -> startAcquisition()
                    }
                },
                enabled = uiState.sampleId.isNotBlank() && uiState.deviceConnectionState == DeviceConnectionState.CONNECTED,
                modifier = Modifier.weight(1f)
            ) {
                val buttonText =
                    if (uiState.acquisitionState == AcquisitionState.RECORDING) "Stop Acquisition" else "Start Acquisition"
                Text(buttonText)
            }
            Button(
                onClick = onRemove,
                enabled = uiState.measurementEntities.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Delete Last Measurement")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                enabled = uiState.acquisitionState != AcquisitionState.RECORDING
            ) {
                Text("Clear")
            }

            Button(
                onClick = {
                    val filename = if (uiState.sampleId.isNotBlank()) {
                        "${safeFilename(uiState.sampleId)}_measurements.csv"
                    } else {
                        "measurements.csv"
                    }
                    createCsvLauncher.launch(filename)
                },
                enabled = uiState.measurementEntities.isNotEmpty() && uiState.acquisitionState != AcquisitionState.RECORDING,
                modifier = Modifier.weight(1f)
            ) {
                Text("Export CSV")
            }
        }


        if (uiState.exportMessage.isNotBlank()) {
            Text(uiState.exportMessage)
        }

        Text(
            text = "Measurement History",
            fontSize = 20.sp
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.measurementEntities) { measurement ->
                MeasurementRow(measurement)
            }
        }
    }
}


@Composable
// for plotting one measurement data in a row
fun MeasurementRow(
    measurementEntity: MeasurementEntity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Session: ${measurementEntity.sessionId}")
            Text("Repetition: ${measurementEntity.repetition}")
            Text("Value: ${"%.3f".format(measurementEntity.value)}")
            Text("Status: ${measurementEntity.status}")
        }
    }
}


@Preview(showBackground = true)
@Composable
fun ResearchScreenPreview() {
    MaterialTheme {
        val viewModel: ResearchViewModel = viewModel()
        ResearchScreen(viewModel)
    }
}
