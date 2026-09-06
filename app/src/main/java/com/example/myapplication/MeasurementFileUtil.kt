package com.example.myapplication

/**
 * Utility functions for generating standardized tablet file names, storage paths, and CSV metadata serialization.
 */
object MeasurementFileUtil {

    /** Generates the tablet file name based on deviceCode + patientCode + recordingDay + sensorFileName. */
    fun generateTabletFileName(
        deviceCode: String,
        patientCode: String,
        recordingDay: String,
        sensorFileName: String
    ): String {
        val cleanDay = recordingDay.replace("_", "")
        val safePatient = safeFilename(patientCode)
        val safeDevice = safeFilename(deviceCode)
        return "${safeDevice}_${safePatient}_${cleanDay}_${sensorFileName}.csv"
    }

    /** Generates the relative tablet file path inside app storage/export folder. */
    fun generateTabletFilePath(
        recordingDay: String,
        deviceCode: String,
        patientCode: String,
        tabletFileName: String
    ): String {
        val cleanDay = recordingDay.replace("_", "")
        val safePatient = safeFilename(patientCode)
        val safeDevice = safeFilename(deviceCode)
        val folderName = "${safeDevice}_${safePatient}_$cleanDay"
        return "$cleanDay/$folderName/$tabletFileName"
    }

    /** Cleans a string to make it safe for use as a file name. */
    fun safeFilename(text: String): String {
        return text
            .trim()
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    /** Converts a list of MeasurementEntity metadata records into a CSV string. */
    fun measurementListToCsv(
        measurementEntityList: List<MeasurementEntity>
    ): String {
        val header =
            "file_id,session_id,sensor_file_name,tablet_file_name,tablet_file_path,recording_index,repeat_index,file_index,recorded_at,transferred_at,transfer_status,signal_quality,checksum,file_size_bytes,notes"
        val rows =
            measurementEntityList.joinToString(separator = "\n") { m ->
                "${m.file_id}," +
                        "${m.session_id}," +
                        "${escapeCsv(m.sensor_file_name)}," +
                        "${escapeCsv(m.tablet_file_name)}," +
                        "${escapeCsv(m.tablet_file_path)}," +
                        "${m.recording_index}," +
                        "${m.repeat_index}," +
                        "${m.file_index ?: ""}," +
                        "${m.recorded_at ?: ""}," +
                        "${m.transferred_at ?: ""}," +
                        "${escapeCsv(m.transfer_status?.name ?: "")}," +
                        "${escapeCsv(m.signal_quality?.name ?: "")}," +
                        "${escapeCsv(m.checksum?.name ?: "")}," +
                        "${m.file_size_bytes ?: ""}," +
                        "${escapeCsv(m.notes ?: "")}"
            }
        return "$header\n$rows"
    }

    /** Parses a CSV string into a list of MeasurementEntity metadata records. */
    fun csvToMeasurementList(
        csvText: String
    ): List<MeasurementEntity> {
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

                if (parts.size < 7) {
                    return@mapNotNull null
                }

                val file_id = parts[0].toLongOrNull() ?: 0L
                val session_id = parts[1].toLongOrNull() ?: return@mapNotNull null
                val sensor_file_name = parts[2]
                val tablet_file_name = parts[3]
                val tablet_file_path = parts[4]
                val recording_index = parts[5].toIntOrNull() ?: return@mapNotNull null
                val repeat_index = parts[6].toIntOrNull() ?: return@mapNotNull null
                val file_index = parts.getOrNull(7)?.toIntOrNull()
                val recorded_at = parts.getOrNull(8)?.toLongOrNull()
                val transferred_at = parts.getOrNull(9)?.toLongOrNull()
                val transfer_status = parts.getOrNull(10)?.let {
                    try { TransferStatus.valueOf(it) } catch (e: Exception) { null }
                }
                val signal_quality = parts.getOrNull(11)?.let {
                    try { SignalQuality.valueOf(it) } catch (e: Exception) { null }
                }
                val checksum = parts.getOrNull(12)?.let {
                    try { ChecksumStatus.valueOf(it) } catch (e: Exception) { null }
                }
                val file_size_bytes = parts.getOrNull(13)?.toLongOrNull()
                val notes = parts.getOrNull(14)?.ifBlank { null }

                MeasurementEntity(
                    file_id = file_id,
                    session_id = session_id,
                    sensor_file_name = sensor_file_name,
                    tablet_file_name = tablet_file_name,
                    tablet_file_path = tablet_file_path,
                    recording_index = recording_index,
                    repeat_index = repeat_index,
                    file_index = file_index,
                    recorded_at = recorded_at,
                    transferred_at = transferred_at,
                    transfer_status = transfer_status,
                    signal_quality = signal_quality,
                    checksum = checksum,
                    file_size_bytes = file_size_bytes,
                    notes = notes
                )
            }
    }

    /** Escapes special characters in a string if it contains a comma, quote, or newline for saving in CSV. */
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
}
