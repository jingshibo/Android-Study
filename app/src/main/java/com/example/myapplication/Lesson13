package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random


/**
 * Class definition
 */
// Measurement represents one measurement row (measurement values and metadata)
data class Measurement(
    val sampleId: String,
    val repetition: Int,
    val value: Double,
    val timestamp: Long,
    val status: MeasurementStatus
)

enum class MeasurementStatus { // Its items are enum constants, but can be displayed directly as strings
    LOW,
    NORMAL,
    HIGH,
}

const val AUTOSAVE_FILENAME = "autosave_measurements.csv"

class MeasurementRepository {

    fun createSimulatedMeasurement(
        sampleId: String,
        repetition: Int,
    ): Measurement {
        val value = Random.nextDouble(0.0, 5.0)

        val status = when {
            value > 4.0 -> MeasurementStatus.HIGH
            value < 1.0 -> MeasurementStatus.LOW
            else -> MeasurementStatus.NORMAL
        }

        return Measurement(
            sampleId = sampleId,
            repetition = repetition,
            value = value,
            timestamp = System.currentTimeMillis(),
            status = status
        )
    }

    /**
     * Internal file writing and loading
     */
    suspend fun saveMeasurementsToInternal(
        context: Context,
        measurements: List<Measurement>
    ) {
        withContext(Dispatchers.IO) {
            val csvText = measurementListToCsv(measurements)

            context.openFileOutput(
                AUTOSAVE_FILENAME,
                Context.MODE_PRIVATE
            ).use { outputStream ->
                outputStream.write(csvText.toByteArray())
            }
        }
    }

    suspend fun loadMeasurementsFromInternal(
        context: Context
    ): List<Measurement> {
        return withContext(Dispatchers.IO) {
            val csvText = context
                .openFileInput(AUTOSAVE_FILENAME)
                .bufferedReader()
                .use { reader ->
                    reader.readText()
                }

            csvToMeasurementList(csvText)
        }
    }
}

/**
CSC text processing functions
 */
// Convert measurements to Strings that can be saved in CSV files
// We write this as a separate function instead of putting it into viewModels so we can reuse it
fun measurementListToCsv(
    measurementList: List<Measurement>
): String {
    val header = "sample_id,repetition,value,timestamp,status"
    val rows =
        measurementList.joinToString(separator = "\n") { measurement -> // The Transformation Lambda
            val sampleId = escapeCsv(measurement.sampleId)
            "$sampleId," +
                    "${measurement.repetition}," +
                    "${measurement.value}," +
                    "${measurement.timestamp}," +
                    "${measurement.status}"
        }
    return "$header\n$rows"
}

fun csvToMeasurementList(
    csvText: String
): List<Measurement> {

    val lines = csvText
        .lines()
        .filter { it.isNotBlank() }

    if (lines.size <= 1) {
        return emptyList()
    }

    return lines
        .drop(1)
        .mapNotNull { line ->

            val parts = line.split(",")

            if (parts.size < 5) {
                return@mapNotNull null
            }

            val sampleId = parts[0]
            val repetition = parts[1].toIntOrNull()
            val value = parts[2].toDoubleOrNull()
            val timestamp = parts[3].toLongOrNull()
            // Convert the String from the CSV back into the Enum type. Returns null if it doesn't find a match.
            val status = MeasurementStatus.entries.find { it.name == parts[4] }

            if (
                repetition == null ||
                value == null ||
                timestamp == null ||
                status == null
            ) {
                return@mapNotNull null
            }

            Measurement(
                sampleId = sampleId,
                repetition = repetition,
                value = value,
                timestamp = timestamp,
                status = status
            )
        }
}

// Escape special characters in a string if it contains a comma, quote, or newline for saving.
fun escapeCsv(value: String): String {
    val needsEscaping =
        value.contains(",") ||
                value.contains("\"") ||
                value.contains("\n")
    return if (needsEscaping) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
}

