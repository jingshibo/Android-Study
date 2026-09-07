package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.room.Index
import androidx.room.ForeignKey
import androidx.room.Update
import androidx.room.OnConflictStrategy


const val AUTOSAVE_FILENAME = "autosave_measurements.csv"

class MeasurementRepository(
    private val context: Context
) {

    /*
       Database Instance & DAOs
     */
    private val database = ResearchDatabase.getDatabase(context)

    private val patientDao = database.patientDao()
    private val sessionDao = database.sessionDao()
    private val measurementDao = database.measurementDao()
    private val resultDao = database.resultDao()

    // ------------------------------------------------------------------------
    // Patient Operations
    // ------------------------------------------------------------------------

    /** Inserts a new patient record into the database. */
    suspend fun insertPatient(patient: PatientEntity): Long = patientDao.insertPatient(patient)

    /** Retrieves a patient record matching the exact patient code, or null if not found. */
    suspend fun getPatientByCode(patientCode: String): PatientEntity? = patientDao.getPatientByCode(patientCode)

    /** Retrieves an existing patient by patient_code, or creates a new one if not found. */
    suspend fun getOrCreatePatient(patient: PatientEntity): Long {
        val existing = patientDao.getPatientByCode(patient.patient_code)
        if (existing != null) {
            return existing.patient_id
        }
        return patientDao.insertPatient(patient)
    }

    /** Helper overload to retrieve or create a patient by patientCode string. */
    suspend fun getOrCreatePatient(patientCode: String): Long {
        return getOrCreatePatient(PatientEntity(patient_code = patientCode))
    }

    /** search patients by flexible criteria.
     * With all parameters null, searchPatients() returns all patients. */
    suspend fun searchPatients(
        patientCodeQuery: String? = null,
        sex: PatientSex? = null,
        minAge: Int? = null,
        maxAge: Int? = null,
        status: PatientStatus? = null,
        location: String? = null
    ): List<PatientEntity> = patientDao.searchPatients(
        patientCodeQuery = patientCodeQuery,
        sex = sex?.name,
        minAge = minAge,
        maxAge = maxAge,
        status = status?.name,
        location = location
    )

    /** Updates an existing patient record. */
    suspend fun updatePatient(patient: PatientEntity) = patientDao.updatePatient(patient)

    // ------------------------------------------------------------------------
    // Session Operations
    // ------------------------------------------------------------------------

    /** Retrieves an existing session by patient and recording day, or creates a new one if not found. */
    suspend fun getOrCreateSession(session: SessionEntity): Long {
        val existing = sessionDao.getSessionByPatientDay(
            patientId = session.patient_id,
            recordingDay = session.recording_day
        )
        if (existing != null) {
            return existing.session_id
        }
        return sessionDao.insertSession(session)
    }

    /** Inserts a SessionEntity object directly, leveraging SessionEntity's constructor default values. */
    suspend fun createSession(session: SessionEntity): Long = sessionDao.insertSession(session)

    /** Searches and filters sessions using flexible optional parameters. */
    suspend fun searchSessions(
        patientId: Long? = null,
        deviceId: Long? = null,
        recordingDay: String? = null,
        arm: Arm? = null,
        sessionStatus: SessionStatus? = null,
        signalQuality: SignalQuality? = null,
        recordingInterval: Int? = null,
        recordingRepeats: Int? = null,
        frequencyStartGhz: Double? = null,
        frequencyEndGhz: Double? = null
    ): List<SessionEntity> = sessionDao.searchSessions(
        patientId = patientId,
        deviceId = deviceId,
        recordingDay = recordingDay,
        arm = arm?.name,
        sessionStatus = sessionStatus?.name,
        signalQuality = signalQuality?.name,
        recordingInterval = recordingInterval,
        recordingRepeats = recordingRepeats,
        frequencyStartGhz = frequencyStartGhz,
        frequencyEndGhz = frequencyEndGhz
    )

    /** Retrieves a session matching a specific patient and recording day. */
    suspend fun getSessionByPatientDay(
        patientId: Long,
        recordingDay: String
    ): SessionEntity? = sessionDao.getSessionByPatientDay(patientId, recordingDay)

    /** Retrieves a session matching a specific device ID and recording day. */
    suspend fun getSessionByDeviceAndDay(
        deviceId: Long,
        recordingDay: String
    ): SessionEntity? = sessionDao.getSessionByDeviceAndDay(deviceId, recordingDay)

    /** Retrieves all sessions matching a specific patient ID and device ID. */
    suspend fun getSessionsByPatientAndDevice(
        patientId: Long,
        deviceId: Long
    ): List<SessionEntity> = sessionDao.getSessionsByPatientAndDevice(patientId, deviceId)

    /** Retrieves a session matching a specific patient and recording day combination. */
    suspend fun getSessionByPatientDeviceDay(
        patientId: Long,
        deviceId: Long = 1L,
        recordingDay: String
    ): SessionEntity? = sessionDao.getSessionByPatientDay(patientId, recordingDay)

    /** Updates an existing measurement session record. */
    suspend fun updateSession(session: SessionEntity) = sessionDao.updateSession(session)

    /** Updates the status of a specific session. */
    suspend fun updateSessionStatus(sessionId: Long, status: SessionStatus?) =
        sessionDao.updateSessionStatus(sessionId, status)

    /** Deletes a session and all its associated measurements and results. */
    suspend fun deleteSession(sessionId: Long): Int = sessionDao.deleteSessionById(sessionId)

    // ------------------------------------------------------------------------
    // Measurement File Operations
    // ------------------------------------------------------------------------

    /** Inserts a single measurement file record into the database. */

    /** Inserts a single measurement file record into the database. */
    suspend fun insertMeasurement(measurementEntity: MeasurementEntity): Long =
        measurementDao.insertMeasurement(measurementEntity)

    /** Inserts a batch list of measurement file records. */
    suspend fun insertMeasurement(measurements: List<MeasurementEntity>): List<Long> =
        measurementDao.insertMeasurement(measurements)

    /** Retrieves all measurement file records for a session. */
    suspend fun getMeasurementsForSession(sessionId: Long): List<MeasurementEntity> =
        searchMeasurements(sessionId = sessionId)

    /** Searches and filters measurement file records using flexible optional criteria. */
    suspend fun searchMeasurements(
        sessionId: Long? = null,
        recordingIndex: Int? = null,
        repeatIndex: Int? = null,
        transferStatus: TransferStatus? = null,
        sensorFileName: String? = null,
        tabletFileName: String? = null,
        minFileSizeBytes: Long? = null,
        maxFileSizeBytes: Long? = null,
        fromTransferredAt: Long? = null,
        toTransferredAt: Long? = null
    ): List<MeasurementEntity> = measurementDao.searchMeasurements(
        sessionId = sessionId,
        recordingIndex = recordingIndex,
        repeatIndex = repeatIndex,
        transferStatus = transferStatus?.name,
        sensorFileName = sensorFileName,
        tabletFileName = tabletFileName,
        minFileSizeBytes = minFileSizeBytes,
        maxFileSizeBytes = maxFileSizeBytes,
        fromTransferredAt = fromTransferredAt,
        toTransferredAt = toTransferredAt
    )

    /** Retrieves a measurement file by session ID and sensor file name. */
    suspend fun getMeasurementByFileName(sessionId: Long, sensorFileName: String): MeasurementEntity? =
        measurementDao.getMeasurementByFileName(sessionId, sensorFileName)

    /** Returns the total count of measurement files in a session. */
    suspend fun getMeasurementCount(sessionId: Long): Int =
        measurementDao.getMeasurementCount(sessionId)

    /** Updates the transfer status and details for a measurement file. */
    suspend fun updateTransferDetails(
        fileId: Long,
        status: TransferStatus?,
        transferredAt: Long? = System.currentTimeMillis(),
        checksum: ChecksumStatus? = null,
        fileSizeBytes: Long? = null
    ) = measurementDao.updateTransferDetails(fileId, status, transferredAt, checksum, fileSizeBytes)

    /** Deletes all measurement records for a specific session. */
    suspend fun deleteMeasurementsForSession(sessionId: Long) =
        measurementDao.deleteMeasurementsBySession(sessionId)

    /** Deletes a single measurement file record by file ID. */
    suspend fun deleteMeasurement(fileId: Long): Int =
        measurementDao.deleteMeasurementById(fileId)

    // ------------------------------------------------------------------------
    // Result / Prediction Operations
    // ------------------------------------------------------------------------

    /** Inserts an AI model prediction result for a session. */
    suspend fun insertResult(result: ResultEntity): Long = resultDao.insertResult(result)

    /** Updates an existing prediction result record. */
    suspend fun updateResult(result: ResultEntity): Int = resultDao.updateResult(result)

    /** Retrieves all prediction results for a given session. */
    suspend fun getResultsForSession(sessionId: Long): List<ResultEntity> =
        searchResults(sessionId = sessionId)

    /** Searches and filters prediction results based on flexible optional criteria. */
    suspend fun searchResults(
        sessionId: Long? = null,
        predictionLabel: PredictionLabel? = null,
        modelName: String? = null,
        modelVersion: String? = null,
        minConfidence: Double? = null,
        maxConfidence: Double? = null,
        fromPredictedAt: Long? = null,
        toPredictedAt: Long? = null
    ): List<ResultEntity> = resultDao.searchResults(
        sessionId = sessionId,
        predictionLabel = predictionLabel?.name,
        modelName = modelName,
        modelVersion = modelVersion,
        minConfidence = minConfidence,
        maxConfidence = maxConfidence,
        fromPredictedAt = fromPredictedAt,
        toPredictedAt = toPredictedAt
    )

    /** Retrieves the most recent prediction result for a session. */
    suspend fun getLatestResultForSession(sessionId: Long): ResultEntity? =
        resultDao.getLatestResultBySession(sessionId)

    /** Checks if a prediction result exists for a specific session, model name, and model version. */
    suspend fun getResultByModel(
        sessionId: Long,
        modelName: String,
        modelVersion: String
    ): ResultEntity? = resultDao.getResultByModel(sessionId, modelName, modelVersion)

    /** Deletes a specific prediction result record by its prediction ID. */
    suspend fun deleteResult(predictionId: Long): Int = resultDao.deleteResultById(predictionId)

    /** Deletes all prediction results associated with a session. */
    suspend fun deleteResultsForSession(sessionId: Long) = resultDao.deleteResultsBySession(sessionId)

    // ------------------------------------------------------------------------
    // Internal File Writing & CSV Measurement Metadata Import/Export Operations
    // ------------------------------------------------------------------------

    /** Exports measurement file catalog metadata for a specific session as CSV text
     *  (file names, paths, timestamps, transfer status, etc.). */
    suspend fun exportMeasurementMetadataToCsv(sessionId: Long): String {
        val measurements = getMeasurementsForSession(sessionId)
        return MeasurementFileUtil.measurementListToCsv(measurements)
    }

    /** Imports measurement file catalog metadata from CSV text into the database. */
    suspend fun importMeasurementMetadataFromCsv(csvText: String): List<Long> {
        val measurements = MeasurementFileUtil.csvToMeasurementList(csvText)
        return if (measurements.isNotEmpty()) {
            insertMeasurement(measurements)
        } else {
            emptyList()
        }
    }

    /** Autosaves a list of measurement file metadata records as CSV to internal app storage. */
    suspend fun saveMeasurementMetadataToInternal(measurementEntities: List<MeasurementEntity>) {
        withContext(Dispatchers.IO) {
            val csvText = MeasurementFileUtil.measurementListToCsv(measurementEntities)
            context.openFileOutput(AUTOSAVE_FILENAME, Context.MODE_PRIVATE).use { outputStream ->
                outputStream.write(csvText.toByteArray())
            }
        }
    }

    /** Loads autosaved measurement file metadata records from internal CSV storage. */
    suspend fun loadMeasurementMetadataFromInternal(): List<MeasurementEntity> {
        return withContext(Dispatchers.IO) {
            val csvText = context
                .openFileInput(AUTOSAVE_FILENAME)
                .bufferedReader()
                .use { reader -> reader.readText() }
            MeasurementFileUtil.csvToMeasurementList(csvText)
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
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ResearchDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun sessionDao(): SessionDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun resultDao(): ResultDao

    companion object {
        @Volatile
        private var INSTANCE: ResearchDatabase? = null

        fun getDatabase(context: Context): ResearchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ResearchDatabase::class.java,
                    "research_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}


class Converters {
    @TypeConverter
    fun fromChecksumStatus(status: ChecksumStatus?): String? = status?.name

    @TypeConverter
    fun toChecksumStatus(value: String?): ChecksumStatus? {
        return value?.let {
            try { ChecksumStatus.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromPatientSex(sex: PatientSex?): String? = sex?.name

    @TypeConverter
    fun toPatientSex(value: String?): PatientSex? {
        return value?.let {
            try { PatientSex.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromArm(arm: Arm?): String? = arm?.name

    @TypeConverter
    fun toArm(value: String?): Arm? {
        return value?.let {
            try { Arm.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromPatientConditionStatus(status: PatientStatus?): String? = status?.name

    @TypeConverter
    fun toPatientConditionStatus(value: String?): PatientStatus? {
        return value?.let {
            try { PatientStatus.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromSessionStatus(status: SessionStatus?): String? = status?.name

    @TypeConverter
    fun toSessionStatus(value: String?): SessionStatus? {
        return value?.let {
            try { SessionStatus.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromTransferStatus(status: TransferStatus?): String? = status?.name

    @TypeConverter
    fun toTransferStatus(value: String?): TransferStatus? {
        return value?.let {
            try { TransferStatus.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromSignalQuality(quality: SignalQuality?): String? = quality?.name

    @TypeConverter
    fun toSignalQuality(value: String?): SignalQuality? {
        return value?.let {
            try { SignalQuality.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromPredictionLabel(label: PredictionLabel): String = label.name

    @TypeConverter
    fun toPredictionLabel(value: String): PredictionLabel {
        return try {
            PredictionLabel.valueOf(value)
        } catch (e: Exception) {
            PredictionLabel.UNCERTAIN
        }
    }
}


/**
 * Database Entity (Table) Definition
 */
@Entity(
    tableName = "patients",
    indices = [Index(value = ["patient_code"], unique = true)]
)
data class PatientEntity(
    @PrimaryKey(autoGenerate = true)
    val patient_id: Long = 0,

    val patient_code: String,
    val sex: PatientSex? = null,
    val age: Int? = null,
    val status: PatientStatus? = null,
    val location: String? = null,
    val created_at: Long = System.currentTimeMillis(),
    val notes: String? = null
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patient_id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patient_id", "recording_day"], unique = true),
        Index(value = ["device_id", "recording_day"], unique = true)
    ]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val session_id: Long = 0,

    val device_id: Long = 1,
    val patient_id: Long,
    val recording_day: String,
    val start_time: Long? = null,
    val end_time: Long? = null,
    val arm: Arm? = null,
    val recording_interval: Int = 10,
    val recording_repeats: Int = 5,
    val frequency_start_ghz: Double = 1.0,
    val frequency_end_ghz: Double = 6.0,
    val session_status: SessionStatus? = SessionStatus.ON_SENSOR,
    val signal_quality: SignalQuality? = null,
    val notes: String? = null
)

// Room Entity: Measurement represents one measurement row
@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id", "sensor_file_name"], unique = true),
        Index(value = ["session_id", "recording_index"]),
        Index(value = ["session_id", "repeat_index"])
    ]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val file_id: Long = 0,

    val session_id: Long,
    val sensor_file_name: String,
    val tablet_file_name: String,
    val tablet_file_path: String,
    val recording_index: Int,
    val repeat_index: Int,
    val file_index: Int? = null,
    val recorded_at: Long? = null,
    val transferred_at: Long? = null,
    val transfer_status: TransferStatus? = TransferStatus.ON_SENSOR,
    val signal_quality: SignalQuality? = null,
    val checksum: ChecksumStatus? = null,
    val file_size_bytes: Long? = null,
    val notes: String? = null
)

@Entity(
    tableName = "results",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id", "model_name", "model_version"], unique = true)
    ]
)
data class ResultEntity(
    @PrimaryKey(autoGenerate = true)
    val prediction_id: Long = 0,

    val session_id: Long,
    val model_name: String,
    val model_version: String,
    val prediction_label: PredictionLabel,
    val probability_json: String? = null,
    val confidence: Double? = null,
    val input_file_count: Int? = null,
    val predicted_at: Long = System.currentTimeMillis(),
    val notes: String? = null
)


/**
 * Database DAO Definition
 */
@Dao
interface PatientDao {

    /** Inserts a new patient record into the database and returns the generated patient ID. */
    @Insert
    suspend fun insertPatient(patient: PatientEntity): Long

    /** Updates an existing patient record in the database. */
    @Update
    suspend fun updatePatient(patient: PatientEntity)

    /** Retrieves all patient records ordered alphabetically by patient code. */
    @Query("SELECT * FROM patients ORDER BY patient_code ASC")
    suspend fun getAllPatients(): List<PatientEntity>

    /** Retrieves a single patient matching the exact patient code. */
    @Query("SELECT * FROM patients WHERE patient_code = :patientCode LIMIT 1")
    suspend fun getPatientByCode(patientCode: String): PatientEntity?

    /** Finds patients whose patient code contains the given partial search query. */
    @Query(
        """
    SELECT * FROM patients
    WHERE patient_code LIKE '%' || :query || '%'
    ORDER BY patient_code ASC
    """
    )
    suspend fun getPatientByPartialCode(query: String): List<PatientEntity>

    /** Searches and filters patient records using multiple optional criteria. */
    @Query(
        """
    SELECT * FROM patients
    WHERE (:patientCodeQuery IS NULL OR patient_code LIKE '%' || :patientCodeQuery || '%')
      AND (:sex IS NULL OR sex = :sex)
      AND (:minAge IS NULL OR age >= :minAge)
      AND (:maxAge IS NULL OR age <= :maxAge)
      AND (:status IS NULL OR status = :status)
      AND (:location IS NULL OR location = :location)
    ORDER BY patient_code ASC
    """
    )
    suspend fun searchPatients(
        patientCodeQuery: String?,
        sex: String?,
        minAge: Int?,
        maxAge: Int?,
        status: String?,
        location: String?
    ): List<PatientEntity>
}

@Dao
interface SessionDao {

    /** Inserts a new measurement session and returns its generated session ID. */
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    /** Updates an existing measurement session record. */
    @Update
    suspend fun updateSession(session: SessionEntity)

    /** Retrieves a session matching a specific patient and recording day. */
    @Query(
        """
    SELECT * FROM sessions
    WHERE patient_id = :patientId 
      AND recording_day = :recordingDay
    LIMIT 1
    """
    )
    suspend fun getSessionByPatientDay(
        patientId: Long,
        recordingDay: String
    ): SessionEntity?

    /** Retrieves a session matching a specific device ID and recording day. */
    @Query(
        """
    SELECT * FROM sessions
    WHERE device_id = :deviceId 
      AND recording_day = :recordingDay
    LIMIT 1
    """
    )
    suspend fun getSessionByDeviceAndDay(
        deviceId: Long,
        recordingDay: String
    ): SessionEntity?

    /** Retrieves all sessions matching a specific patient ID and device ID. */
    @Query(
        """
    SELECT * FROM sessions
    WHERE patient_id = :patientId 
      AND device_id = :deviceId
    ORDER BY recording_day DESC
    """
    )
    suspend fun getSessionsByPatientAndDevice(
        patientId: Long,
        deviceId: Long
    ): List<SessionEntity>

    /** Updates the status of a specific measurement session. */
    @Query("UPDATE sessions SET session_status = :status WHERE session_id = :sessionId")
    suspend fun updateSessionStatus(sessionId: Long, status: SessionStatus?)

    /** Deletes a session by its ID, cascading deletion to associated measurements and results. */
    @Query("DELETE FROM sessions WHERE session_id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long): Int

    /** Searches and filters sessions using multiple optional parameters. */
    @Query(
        """
    SELECT * FROM sessions
    WHERE (:patientId IS NULL OR patient_id = :patientId)
      AND (:deviceId IS NULL OR device_id = :deviceId)
      AND (:recordingDay IS NULL OR recording_day = :recordingDay)
      AND (:arm IS NULL OR arm = :arm)
      AND (:sessionStatus IS NULL OR session_status = :sessionStatus)
      AND (:signalQuality IS NULL OR signal_quality = :signalQuality)
      AND (:recordingInterval IS NULL OR recording_interval = :recordingInterval)
      AND (:recordingRepeats IS NULL OR recording_repeats = :recordingRepeats)
      AND (:frequencyStartGhz IS NULL OR frequency_start_ghz = :frequencyStartGhz)
      AND (:frequencyEndGhz IS NULL OR frequency_end_ghz = :frequencyEndGhz)
    ORDER BY recording_day DESC, session_id DESC
    """
    )
    suspend fun searchSessions(
        patientId: Long?,
        deviceId: Long?,
        recordingDay: String?,
        arm: String?,
        sessionStatus: String?,
        signalQuality: String?,
        recordingInterval: Int?,
        recordingRepeats: Int?,
        frequencyStartGhz: Double?,
        frequencyEndGhz: Double?
    ): List<SessionEntity>
}

@Dao
interface MeasurementDao {

    /** Inserts a single measurement file record and returns its generated file ID. */
    @Insert
    suspend fun insertMeasurement(measurement: MeasurementEntity): Long

    /** Inserts a batch list of measurement file records and returns their generated file IDs. */
    @Insert
    suspend fun insertMeasurement(measurements: List<MeasurementEntity>): List<Long>

    /** Updates an existing measurement file record. */
    @Update
    suspend fun updateMeasurement(measurement: MeasurementEntity): Int

    /** Retrieves all measurement files belonging to a specific session. */
    @Query("SELECT * FROM measurements WHERE session_id = :sessionId ORDER BY file_id ASC")
    suspend fun getMeasurementsBySession(sessionId: Long): List<MeasurementEntity>

    /** Retrieves a measurement file record by session ID and sensor file name. */
    @Query("SELECT * FROM measurements WHERE session_id = :sessionId AND sensor_file_name = :sensorFileName LIMIT 1")
    suspend fun getMeasurementByFileName(sessionId: Long, sensorFileName: String): MeasurementEntity?

    /** Returns the total count of measurement files recorded for a session. */
    @Query("SELECT COUNT(*) FROM measurements WHERE session_id = :sessionId")
    suspend fun getMeasurementCount(sessionId: Long): Int

    /** Updates the transfer status for a specific measurement file. */
    @Query("UPDATE measurements SET transfer_status = :status WHERE file_id = :measurementId")
    suspend fun updateTransferStatus(measurementId: Long, status: String): Int

    /** Updates the transfer status and details for a specific measurement file. */
    @Query("UPDATE measurements SET transfer_status = :status, transferred_at = :transferredAt, checksum = :checksum, file_size_bytes = :fileSizeBytes WHERE file_id = :fileId")
    suspend fun updateTransferDetails(
        fileId: Long,
        status: TransferStatus?,
        transferredAt: Long?,
        checksum: ChecksumStatus?,
        fileSizeBytes: Long?
    )

    /** Deletes a single measurement file record by its file ID. */
    @Query("DELETE FROM measurements WHERE file_id = :measurementId")
    suspend fun deleteMeasurementById(measurementId: Long): Int

    /** Deletes all measurement file records belonging to a session. */
    @Query("DELETE FROM measurements WHERE session_id = :sessionId")
    suspend fun deleteMeasurementsBySession(sessionId: Long)

    /** Searches and filters measurement file records using flexible optional criteria. */
    @Query(
        """
    SELECT * FROM measurements
    WHERE (:sessionId IS NULL OR session_id = :sessionId)
      AND (:recordingIndex IS NULL OR recording_index = :recordingIndex)
      AND (:repeatIndex IS NULL OR repeat_index = :repeatIndex)
      AND (:transferStatus IS NULL OR transfer_status = :transferStatus)
      AND (:sensorFileName IS NULL OR sensor_file_name LIKE '%' || :sensorFileName || '%')
      AND (:tabletFileName IS NULL OR tablet_file_name LIKE '%' || :tabletFileName || '%')
      AND (:minFileSizeBytes IS NULL OR file_size_bytes >= :minFileSizeBytes)
      AND (:maxFileSizeBytes IS NULL OR file_size_bytes <= :maxFileSizeBytes)
      AND (:fromTransferredAt IS NULL OR transferred_at >= :fromTransferredAt)
      AND (:toTransferredAt IS NULL OR transferred_at <= :toTransferredAt)
    ORDER BY session_id DESC, recording_index ASC, repeat_index ASC
    """
    )
    suspend fun searchMeasurements(
        sessionId: Long?,
        recordingIndex: Int?,
        repeatIndex: Int?,
        transferStatus: String?,
        sensorFileName: String?,
        tabletFileName: String?,
        minFileSizeBytes: Long?,
        maxFileSizeBytes: Long?,
        fromTransferredAt: Long?,
        toTransferredAt: Long?
    ): List<MeasurementEntity>
}

@Dao
interface ResultDao {

    /** Inserts a new prediction result record and returns its generated prediction ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ResultEntity): Long

    /** Updates an existing prediction result record. */
    @Update
    suspend fun updateResult(result: ResultEntity): Int

    /** Retrieves all prediction results for a given session sorted by prediction timestamp. */
    @Query("SELECT * FROM results WHERE session_id = :sessionId ORDER BY predicted_at DESC")
    suspend fun getResultsBySession(sessionId: Long): List<ResultEntity>

    /** Retrieves the most recent prediction result for a session. */
    @Query("SELECT * FROM results WHERE session_id = :sessionId ORDER BY predicted_at DESC LIMIT 1")
    suspend fun getLatestResultBySession(sessionId: Long): ResultEntity?

    /** Retrieves a prediction result matching exact session ID, model name, and model version. */
    @Query("SELECT * FROM results WHERE session_id = :sessionId AND model_name = :modelName AND model_version = :modelVersion LIMIT 1")
    suspend fun getResultByModel(sessionId: Long, modelName: String, modelVersion: String): ResultEntity?

    /** Deletes a specific prediction result record by its ID. */
    @Query("DELETE FROM results WHERE prediction_id = :resultId")
    suspend fun deleteResultById(resultId: Long): Int

    /** Deletes all prediction result records associated with a session. */
    @Query("DELETE FROM results WHERE session_id = :sessionId")
    suspend fun deleteResultsBySession(sessionId: Long)

    /** Searches and filters prediction results based on optional criteria. */
    @Query(
        """
    SELECT * FROM results
    WHERE (:sessionId IS NULL OR session_id = :sessionId)
      AND (:predictionLabel IS NULL OR prediction_label = :predictionLabel)
      AND (:modelName IS NULL OR model_name = :modelName)
      AND (:modelVersion IS NULL OR model_version = :modelVersion)
      AND (:minConfidence IS NULL OR confidence >= :minConfidence)
      AND (:maxConfidence IS NULL OR confidence <= :maxConfidence)
      AND (:fromPredictedAt IS NULL OR predicted_at >= :fromPredictedAt)
      AND (:toPredictedAt IS NULL OR predicted_at <= :toPredictedAt)
    ORDER BY predicted_at DESC
    """
    )
    suspend fun searchResults(
        sessionId: Long?,
        predictionLabel: String?,
        modelName: String?,
        modelVersion: String?,
        minConfidence: Double?,
        maxConfidence: Double?,
        fromPredictedAt: Long?,
        toPredictedAt: Long?
    ): List<ResultEntity>
}


/**
 * Class definition & Enums
 */
enum class PatientSex {
    MALE,
    FEMALE,
    OTHER,
    UNKNOWN
}

enum class Arm {
    LEFT,
    RIGHT,
    UNKNOWN
}

enum class PatientStatus {
    MF_POSITIVE,
    MF_NEGATIVE,
    UNCERTAIN
}

enum class SessionStatus {
    ON_SENSOR,
    TRANSFERRED,
    ANALYSED,
    CORRUPTED
}

enum class TransferStatus {
    ON_SENSOR, /** File exists on the sensor hardware, pending transfer to the tablet. */
    TRANSFERRED, /** File was successfully transferred to tablet storage and verified. */
    MISSING, /** File was expected but not found on the sensor hardware. */
    CORRUPTED /** File was transferred, but data is damaged or incomplete. */

}

enum class SignalQuality {
    GOOD,
    ACCEPTABLE,
    BAD,
    ERROR,
    UNKNOWN
}

enum class ChecksumStatus {
    PASS,
    FAIL
}

enum class PredictionLabel {
    MF_POSITIVE,
    MF_NEGATIVE,
    UNCERTAIN
}
