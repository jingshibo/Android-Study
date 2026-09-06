package com.example.myapplication

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Mock data generator for simulating frequency response signal files and measurement metadata.
 */
object MeasurementSimulator {

    /**
     * Step 1: Generates a simulated CSV string containing frequency response points (e.g. 1.0 GHz to 6.0 GHz).
     */
    fun generateSimulatedSignalCsv(
        startFreqGhz: Double = 1.0,
        endFreqGhz: Double = 6.0,
        points: Int = 101
    ): String {
        val header = "Frequency_GHz,S21_Magnitude_dB,Phase_Deg"
        val step = (endFreqGhz - startFreqGhz) / (points - 1)

        val rows = (0 until points).joinToString(separator = "\n") { i ->
            val freq = startFreqGhz + (i * step)
            // Generate realistic curve with small random noise
            val magnitude = -10.0 - (freq * 2.5) + Random.nextDouble(-0.2, 0.2)
            val phase = 50.0 - (freq * 30.0) + Random.nextDouble(-0.5, 0.5)

            "%.3f,%.2f,%.2f".format(Locale.US, freq, magnitude, phase)
        }

        return "$header\n$rows"
    }

    /**
     * Step 2: Saves any signal CSV text string to a physical file on tablet disk storage.
     */
    fun writeSignalFileToDisk(
        context: Context,
        relativePath: String,
        content: String
    ): File {
        val targetFile = File(context.filesDir, relativePath)
        targetFile.parentFile?.mkdirs() // Create directory structure if needed
        targetFile.writeText(content)
        return targetFile
    }

    /**
     * Creates a simulated measurement entity metadata object representing a file sitting on the sensor before transfer.
     * Before transfer: transfer_status = ON_SENSOR, transferred_at = null, signal_quality = null, checksum = null, file_size_bytes = null.
     */
    fun createSimulatedMeasurementMetadata(
        sessionId: Long,
        patientCode: String = "P001",
        deviceCode: String = "D001",
        recordingDay: String = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date()),
        recordingIndex: Int = 1,
        repeatIndex: Int = 1,
        fileIndex: Int? = null
    ): MeasurementEntity {
        val now = System.currentTimeMillis()
        val sensorFileName = "R${"%03d".format(recordingIndex)}_Rep${"%02d".format(repeatIndex)}_$now"
        val tabletFileName = MeasurementFileUtil.generateTabletFileName(deviceCode, patientCode, recordingDay, sensorFileName)
        val tabletFilePath = MeasurementFileUtil.generateTabletFilePath(recordingDay, deviceCode, patientCode, tabletFileName)

        return MeasurementEntity(
            session_id = sessionId,
            sensor_file_name = sensorFileName,
            tablet_file_name = tabletFileName,
            tablet_file_path = tabletFilePath,
            recording_index = recordingIndex,
            repeat_index = repeatIndex,
            file_index = fileIndex ?: recordingIndex,
            recorded_at = now,
            transferred_at = null,
            transfer_status = TransferStatus.ON_SENSOR,
            signal_quality = null,
            checksum = null,
            file_size_bytes = null,
            notes = "Sensor measurement file"
        )
    }

    /**
     * Simulates downloading/transferring a file from sensor hardware to tablet storage:
     * 1. Generates signal CSV content.
     * 2. Saves the generated CSV content string to disk via writeSignalFileToDisk.
     * 3. Sets transfer_status = TRANSFERRED, transferred_at, signal_quality = GOOD, checksum = PASS,
     *    and file_size_bytes ONLY AFTER the file is successfully written.
     */
    fun createSimulatedMeasurementAndSave(
        context: Context,
        sessionId: Long,
        patientCode: String = "P001",
        deviceCode: String = "D001",
        recordingDay: String = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date()),
        recordingIndex: Int = 1,
        repeatIndex: Int = 1,
        fileIndex: Int? = null
    ): MeasurementEntity {
        // Build untransferred sensor metadata
        val baseMetadata = createSimulatedMeasurementMetadata(
            sessionId = sessionId,
            patientCode = patientCode,
            deviceCode = deviceCode,
            recordingDay = recordingDay,
            recordingIndex = recordingIndex,
            repeatIndex = repeatIndex,
            fileIndex = fileIndex
        )

        return try {
            // Step 1: Generate signal CSV data string
            val signalCsvText = generateSimulatedSignalCsv()

            // Step 2: Pass generated data to file writer for saving
            val file = writeSignalFileToDisk(
                context = context,
                relativePath = baseMetadata.tablet_file_path,
                content = signalCsvText
            )

            // Step 3: Mark as TRANSFERRED ONLY AFTER successful write!
            baseMetadata.copy(
                transferred_at = System.currentTimeMillis(),
                transfer_status = TransferStatus.TRANSFERRED,
                signal_quality = SignalQuality.GOOD,
                checksum = ChecksumStatus.PASS,
                file_size_bytes = file.length()
            )
        } catch (e: Exception) {
            // Fallback to CORRUPTED if file writing failed
            baseMetadata.copy(
                transferred_at = null,
                transfer_status = TransferStatus.CORRUPTED,
                signal_quality = null,
                checksum = ChecksumStatus.FAIL,
                file_size_bytes = null,
                notes = "Failed to write file: ${e.message}"
            )
        }
    }

    /**
     * Simulates transferring 100 measurement files (25 time points x 4 repeats)
     * for ONE single session when that patient's sensor connects to the tablet over Bluetooth.
     * Includes a delay between each file to simulate real wireless transmission time.
     */
    suspend fun simulateSessionTransferFromSensor(
        context: Context,
        repository: MeasurementRepository,
        sessionId: Long,
        patientCode: String = "P001",
        deviceCode: String = "D001",
        recordingDay: String = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date()),
        delayMsPerFile: Long = 100L,
        onProgressUpdate: (suspend (transferredCount: Int, totalCount: Int, latestMeasurement: MeasurementEntity) -> Unit)? = null
    ): List<MeasurementEntity> {
        val measurementMetaDataBatch = mutableListOf<MeasurementEntity>()
        var fileSequence = 1
        val totalFiles = 25 * 4

        // 25 recording time points x 4 repeats = 100 files for this session
        for (recIdx in 1..25) {
            for (repIdx in 1..4) {
                // Simulate Bluetooth wireless transmission delay (500ms per file)
                if (delayMsPerFile > 0) {
                    delay(delayMsPerFile)
                }

                val measurementMetaData = createSimulatedMeasurementAndSave(
                    context = context,
                    sessionId = sessionId,
                    patientCode = patientCode,
                    deviceCode = deviceCode,
                    recordingDay = recordingDay,
                    recordingIndex = recIdx,
                    repeatIndex = repIdx,
                    fileIndex = fileSequence++
                )

                // Save each measurement to Room DB upon transfer
                repository.insertMeasurement(measurementMetaData)
                measurementMetaDataBatch.add(measurementMetaData)

                // Report transfer progress to UI
                onProgressUpdate?.invoke(measurementMetaDataBatch.size,
                    totalFiles, measurementMetaData)
            }
        }

        return measurementMetaDataBatch
    }
}
