package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Room
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Class definition
 */
enum class MeasurementStatus { // Its items are enum constants, but can be displayed directly as strings
    LOW,
    NORMAL,
    HIGH,
}

const val AUTOSAVE_FILENAME = "autosave_measurements.csv"

class MeasurementRepository (
    private val context: Context
){

    fun createSimulatedMeasurement(
        sessionId: Long,
        repetition: Int,
    ): MeasurementEntity {
        val value = Random.nextDouble(0.0, 5.0)

        val status = when {
            value > 4.0 -> MeasurementStatus.HIGH
            value < 1.0 -> MeasurementStatus.LOW
            else -> MeasurementStatus.NORMAL
        }

        return MeasurementEntity(
            sessionId = sessionId,
            repetition = repetition,
            value = value,
            timestamp = System.currentTimeMillis(),
            status = status
        )
    }

    /*
       database variables
     */
    private val database = Room.databaseBuilder(
        context.applicationContext,
        ResearchDatabase::class.java,
        "research_database"
    ).build()

    private val measurementDao = database.measurementDao()
    private val patientDao = database.patientDao()
    private val sessionDao = database.sessionDao()
    private val resultDao = database.resultDao()

    // insert new patient and session
    suspend fun createPatientAndSession(
        patientCode: String,
        sessionName: String
    ): Long {
        val patientId = patientDao.insertPatient(
            PatientEntity(
                patientCode = patientCode
            )
        )

        val sessionId = sessionDao.insertSession(
            SessionEntity(
                patientId = patientId,
                sessionName = sessionName
            )
        )

        return sessionId
    }

    suspend fun insertMeasurement(
        measurementEntity: MeasurementEntity
    ) {
        measurementDao.insertMeasurement(measurementEntity)
    }

    suspend fun getMeasurementsForSession(sessionId: Long): List<MeasurementEntity> {
        return measurementDao.getMeasurementsForSession(sessionId)
    }

    suspend fun deleteMeasurementsForSession(sessionId: Long) {
        measurementDao.deleteMeasurementsForSession(sessionId)
    }

    /**
     * Internal file writing and loading
     */
    suspend fun saveMeasurementsToInternal(
        measurementEntities: List<MeasurementEntity>
    ) {
        withContext(Dispatchers.IO) {
            val csvText = measurementListToCsv(measurementEntities)

            context.openFileOutput(
                AUTOSAVE_FILENAME,
                Context.MODE_PRIVATE
            ).use { outputStream ->
                outputStream.write(csvText.toByteArray())
            }
        }
    }

    suspend fun loadMeasurementsFromInternal(
    ): List<MeasurementEntity> {
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
 * Room database definition
 */
// Room Database
@Database(
    entities = [
        PatientEntity::class,
        SessionEntity::class,
        MeasurementEntity::class,
        ResultEntity::class
    ],
    version = 1
)
@TypeConverters(Converters::class)
abstract class ResearchDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun sessionDao(): SessionDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun resultDao(): ResultDao
}

/**
 * Database Entity (Table) Definition
 */
@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val patientCode: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val patientId: Long,
    val sessionName: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val notes: String = ""
)

// Room Entity: Measurement represents one measurement row
@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,
    val repetition: Int,
    val value: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MeasurementStatus
)

@Entity(tableName = "results")
data class ResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,
    val label: String,
    val confidence: Double,
    val createdAt: Long = System.currentTimeMillis()
)


/**
 * Database DAO Definition
 */
@Dao
interface PatientDao {

    @Insert
    suspend fun insertPatient(
        patient: PatientEntity
    ): Long

    @Query("SELECT * FROM patients ORDER BY createdAt DESC")
    suspend fun getAllPatients(): List<PatientEntity>
}

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(
        session: SessionEntity
    ): Long

    @Query("SELECT * FROM sessions WHERE patientId = :patientId ORDER BY startedAt DESC")
    suspend fun getSessionsForPatient(
        patientId: Long
    ): List<SessionEntity>

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :sessionId")
    suspend fun endSession(
        sessionId: Long,
        endedAt: Long
    )
}

@Dao
interface MeasurementDao {

    @Insert
    suspend fun insertMeasurement(
        measurement: MeasurementEntity
    ): Long

    @Query("SELECT * FROM measurements WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMeasurementsForSession(
        sessionId: Long
    ): List<MeasurementEntity>

    @Query("DELETE FROM measurements WHERE sessionId = :sessionId")
    suspend fun deleteMeasurementsForSession(
        sessionId: Long
    )
}

@Dao
interface ResultDao {

    @Insert
    suspend fun insertResult(
        result: ResultEntity
    ): Long

    @Query("SELECT * FROM results WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getResultsForSession(
        sessionId: Long
    ): List<ResultEntity>
}


/**
CSC text processing functions
 */
// Convert measurements to Strings that can be saved in CSV files
// We write this as a separate function instead of putting it into viewModels so we can reuse it
fun measurementListToCsv(
    measurementEntityList: List<MeasurementEntity>
): String {
    val header = "session_id,repetition,value,timestamp,status"
    val rows =
        measurementEntityList.joinToString(separator = "\n") { measurement ->
            "${measurement.sessionId}," +
                    "${measurement.repetition}," +
                    "${measurement.value}," +
                    "${measurement.timestamp}," +
                    "${measurement.status}"
        }
    return "$header\n$rows"
}

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

            if (parts.size < 5) {
                return@mapNotNull null
            }

            val sessionId = parts[0].toLongOrNull()
            val repetition = parts[1].toIntOrNull()
            val value = parts[2].toDoubleOrNull()
            val timestamp = parts[3].toLongOrNull()
            val status = MeasurementStatus.entries.find { it.name == parts[4] }

            if (
                sessionId == null ||
                repetition == null ||
                value == null ||
                timestamp == null ||
                status == null
            ) {
                return@mapNotNull null
            }

            MeasurementEntity(
                sessionId = sessionId,
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

class Converters {
    @TypeConverter
    fun fromStatus(status: MeasurementStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): MeasurementStatus {
        return try {
            MeasurementStatus.valueOf(value)
        } catch (e: Exception) {
            MeasurementStatus.NORMAL
        }
    }
}
