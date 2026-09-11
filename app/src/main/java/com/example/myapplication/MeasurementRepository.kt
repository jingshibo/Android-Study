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
    private val deviceDao = database.deviceDao()
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
        sex: SexGender? = null,
        ageGroup: AgeGroup? = null,
        studyGroup: StudyGroup? = null,
        location: String? = null,
        validityStatus: RecordValidityStatus? = null
    ): List<PatientEntity> = patientDao.searchPatients(
        patientCodeQuery = patientCodeQuery,
        sex = sex?.name,
        ageGroup = ageGroup?.name,
        studyGroup = studyGroup?.name,
        location = location,
        validityStatus = validityStatus?.name
    )

    /** Updates an existing patient record. */
    suspend fun updatePatient(patient: PatientEntity) = patientDao.updatePatient(patient)

    // ------------------------------------------------------------------------
    // Device Operations
    // ------------------------------------------------------------------------

    /** Inserts a new device record into the database. */
    suspend fun insertDevice(device: DeviceEntity): Long = deviceDao.insertDevice(device)

    /** Retrieves a device record matching the exact device code, or null if not found. */
    suspend fun getDeviceByCode(deviceCode: String): DeviceEntity? = deviceDao.getDeviceByCode(deviceCode)

    /** Retrieves an existing device by device_code, or creates a new one if not found. */
    suspend fun getOrCreateDevice(device: DeviceEntity): Long {
        val existing = deviceDao.getDeviceByCode(device.device_code)
        if (existing != null) {
            return existing.device_id
        }
        return deviceDao.insertDevice(device)
    }

    /** Helper overload to retrieve or create a device by deviceCode string. */
    suspend fun getOrCreateDevice(deviceCode: String): Long {
        return getOrCreateDevice(DeviceEntity(device_code = deviceCode))
    }

    /** Searches devices by flexible criteria. */
    suspend fun searchDevices(
        deviceCodeQuery: String? = null,
        sensorStatus: SensorStatus? = null
    ): List<DeviceEntity> = deviceDao.searchDevices(
        deviceCodeQuery = deviceCodeQuery,
        sensorStatus = sensorStatus?.name
    )

    /** Updates an existing device record. */
    suspend fun updateDevice(device: DeviceEntity) = deviceDao.updateDevice(device)

    /** Deletes a device by ID. */
    suspend fun deleteDevice(deviceId: Long): Int = deviceDao.deleteDeviceById(deviceId)

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
        arm: ArmSide? = null,
        transferStatus: SessionTransferStatus? = null,
        completenessStatus: SessionCompletenessStatus? = null,
        validityStatus: RecordValidityStatus? = null,
        recordingInterval: Int? = null,
        recordingRepeats: Int? = null,
        frequencyStartGhz: Double? = null,
        frequencyEndGhz: Double? = null
    ): List<SessionEntity> = sessionDao.searchSessions(
        patientId = patientId,
        deviceId = deviceId,
        recordingDay = recordingDay,
        arm = arm?.name,
        transferStatus = transferStatus?.name,
        completenessStatus = completenessStatus?.name,
        validityStatus = validityStatus?.name,
        recordingInterval = recordingInterval,
        recordingRepeats = recordingRepeats,
        frequencyStartGhz = frequencyStartGhz,
        frequencyEndGhz = frequencyEndGhz
    )

    /** Updates the transfer status of a specific session. */
    suspend fun updateSessionTransferStatus(
        sessionId: Long,
        status: SessionTransferStatus,
        modifiedAt: Long = System.currentTimeMillis()
    ) = sessionDao.updateSessionTransferStatus(sessionId, status, modifiedAt)

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
        transferStatus: MeasurementTransferStatus? = null,
        fileCompletenessStatus: FileCompletenessStatus? = null,
        checksumStatus: ChecksumStatus? = null,
        sensorFileName: String? = null,
        tabletFileName: String? = null,
        minFileSizeBytes: Long? = null,
        maxFileSizeBytes: Long? = null,
        fromTransferredAt: Long? = null,
        toTransferredAt: Long? = null
    ): List<MeasurementEntity> = measurementDao.searchMeasurements(
        sessionId = sessionId,
        transferStatus = transferStatus?.name,
        fileCompletenessStatus = fileCompletenessStatus?.name,
        checksumStatus = checksumStatus?.name,
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
        status: MeasurementTransferStatus,
        transferredAt: Long? = System.currentTimeMillis(),
        checksum: String? = null,
        checksumStatus: ChecksumStatus = ChecksumStatus.NOT_CHECKED,
        fileSizeBytes: Long? = null
    ) = measurementDao.updateTransferDetails(fileId, status, transferredAt, checksum, checksumStatus, fileSizeBytes)

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
        analysisStatus: AnalysisStatus? = null,
        exportStatus: ExportStatus? = null,
        modelVersion: String? = null,
        preprocessingVersion: String? = null,
        minConfidence: Double? = null,
        maxConfidence: Double? = null,
        fromAnalyzedAt: Long? = null,
        toAnalyzedAt: Long? = null
    ): List<ResultEntity> = resultDao.searchResults(
        sessionId = sessionId,
        predictionLabel = predictionLabel?.name,
        analysisStatus = analysisStatus?.name,
        exportStatus = exportStatus?.name,
        modelVersion = modelVersion,
        preprocessingVersion = preprocessingVersion,
        minConfidence = minConfidence,
        maxConfidence = maxConfidence,
        fromAnalyzedAt = fromAnalyzedAt,
        toAnalyzedAt = toAnalyzedAt
    )

    /** Retrieves the most recent prediction result for a session. */
    suspend fun getLatestResultForSession(sessionId: Long): ResultEntity? =
        resultDao.getLatestResultBySession(sessionId)

    /** Checks if a prediction result exists for a specific session, model version, and preprocessing version. */
    suspend fun getResultByModelAndPreprocessing(
        sessionId: Long,
        modelVersion: String,
        preprocessingVersion: String? = null
    ): ResultEntity? = resultDao.getResultByModelAndPreprocessing(sessionId, modelVersion, preprocessingVersion)

    /** Deletes a specific prediction result record by its result ID. */
    suspend fun deleteResult(resultId: Long): Int = resultDao.deleteResultById(resultId)

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
        DeviceEntity::class,
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
    abstract fun deviceDao(): DeviceDao
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
    fun fromSexGender(sex: SexGender?): String? = sex?.name

    @TypeConverter
    fun toSexGender(value: String?): SexGender? {
        return value?.let {
            try { SexGender.valueOf(it) } catch (e: Exception) { null }
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
    fun fromAgeGroup(ageGroup: AgeGroup?): String? = ageGroup?.name

    @TypeConverter
    fun toAgeGroup(value: String?): AgeGroup? {
        return value?.let {
            try { AgeGroup.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromStudyGroup(studyGroup: StudyGroup?): String? = studyGroup?.name

    @TypeConverter
    fun toStudyGroup(value: String?): StudyGroup? {
        return value?.let {
            try { StudyGroup.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromRecordValidityStatus(status: RecordValidityStatus): String = status.name

    @TypeConverter
    fun toRecordValidityStatus(value: String): RecordValidityStatus {
        return try {
            RecordValidityStatus.valueOf(value)
        } catch (e: Exception) {
            RecordValidityStatus.VALID
        }
    }

    @TypeConverter
    fun fromSensorStatus(status: SensorStatus): String = status.name

    @TypeConverter
    fun toSensorStatus(value: String): SensorStatus {
        return try {
            SensorStatus.valueOf(value)
        } catch (e: Exception) {
            SensorStatus.NOT_CHECKED
        }
    }

    @TypeConverter
    fun fromSessionTransferStatus(status: SessionTransferStatus): String = status.name

    @TypeConverter
    fun toSessionTransferStatus(value: String): SessionTransferStatus {
        return try {
            SessionTransferStatus.valueOf(value)
        } catch (e: Exception) {
            SessionTransferStatus.NOT_TRANSFERRED
        }
    }

    @TypeConverter
    fun fromSessionCompletenessStatus(status: SessionCompletenessStatus): String = status.name

    @TypeConverter
    fun toSessionCompletenessStatus(value: String): SessionCompletenessStatus {
        return try {
            SessionCompletenessStatus.valueOf(value)
        } catch (e: Exception) {
            SessionCompletenessStatus.NOT_CHECKED
        }
    }

    @TypeConverter
    fun fromArmSide(arm: ArmSide): String = arm.name

    @TypeConverter
    fun toArmSide(value: String): ArmSide {
        return try {
            ArmSide.valueOf(value)
        } catch (e: Exception) {
            ArmSide.UNKNOWN
        }
    }

    @TypeConverter
    fun fromMeasurementTransferStatus(status: MeasurementTransferStatus): String = status.name

    @TypeConverter
    fun toMeasurementTransferStatus(value: String): MeasurementTransferStatus {
        return try {
            MeasurementTransferStatus.valueOf(value)
        } catch (e: Exception) {
            MeasurementTransferStatus.NOT_TRANSFERRED
        }
    }

    @TypeConverter
    fun fromFileCompletenessStatus(status: FileCompletenessStatus): String = status.name

    @TypeConverter
    fun toFileCompletenessStatus(value: String): FileCompletenessStatus {
        return try {
            FileCompletenessStatus.valueOf(value)
        } catch (e: Exception) {
            FileCompletenessStatus.NOT_CHECKED
        }
    }

    @TypeConverter
    fun fromChecksumStatus(status: ChecksumStatus): String = status.name

    @TypeConverter
    fun toChecksumStatus(value: String): ChecksumStatus {
        return try {
            ChecksumStatus.valueOf(value)
        } catch (e: Exception) {
            ChecksumStatus.NOT_CHECKED
        }
    }

    @TypeConverter
    fun fromPredictionLabel(label: PredictionLabel?): String? = label?.name

    @TypeConverter
    fun toPredictionLabel(value: String?): PredictionLabel? {
        return value?.let {
            try { PredictionLabel.valueOf(it) } catch (e: Exception) { null }
        }
    }

    @TypeConverter
    fun fromAnalysisStatus(status: AnalysisStatus): String = status.name

    @TypeConverter
    fun toAnalysisStatus(value: String): AnalysisStatus {
        return try {
            AnalysisStatus.valueOf(value)
        } catch (e: Exception) {
            AnalysisStatus.ANALYZED
        }
    }

    @TypeConverter
    fun fromExportStatus(status: ExportStatus): String = status.name

    @TypeConverter
    fun toExportStatus(value: String): ExportStatus {
        return try {
            ExportStatus.valueOf(value)
        } catch (e: Exception) {
            ExportStatus.NOT_EXPORTED
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
    val sex: SexGender? = null,
    val age: AgeGroup? = null,
    val study_group: StudyGroup? = null,
    val location: String? = null,
    val created_at: Long = System.currentTimeMillis(),
    val modified_at: Long? = null,
    val validity_status: RecordValidityStatus = RecordValidityStatus.VALID,
    val void_reason: String? = null,
    val voided_at: Long? = null,
    val notes: String? = null
)

/**
 * Room Entity: Device represents a sensor hardware device.
 */
@Entity(
    tableName = "devices",
    indices = [
        Index(value = ["device_code"], unique = true),
        Index(value = ["bluetooth_address"], unique = true)
    ]
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val device_id: Long = 0,

    val device_code: String,
    val bluetooth_address: String? = null,
    val firmware_version: String? = null,
    val last_battery_level: Int? = null,
    val last_memory_available_byte: Long? = null,
    val last_file_count_on_device: Int? = null,
    val sensor_status: SensorStatus = SensorStatus.NOT_CHECKED,
    val last_checked_at: Long? = null,
    val notes: String? = null
)

/**
 * Room Entity: Session represents one overnight measurement session.
 * 
 * UNIQUE CONSTRAINTS ENFORCED:
 * 1. (patient_id, recording_day): A patient can only have one session per recording day.
 * 2. (device_id, recording_day): A physical sensor device can only be used for one session per day.
 * 
 * IMPORTANT: Always validate these conditions in application logic BEFORE downloading or 
 * writing measurement files to disk storage.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patient_id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["device_id"],
            childColumns = ["device_id"],
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

    val patient_id: Long,
    val recording_day: String,
    val device_id: Long = 1,
    val arm: ArmSide = ArmSide.UNKNOWN,
    val start_time: Long? = null,
    val end_time: Long? = null,
    val recording_interval: Int = 10,
    val recording_repeats: Int = 5,
    val frequency_start_ghz: Double = 1.0,
    val frequency_end_ghz: Double = 6.0,
    val transferred_at: Long? = null,
    val modified_at: Long? = null,
    val transfer_status: SessionTransferStatus = SessionTransferStatus.NOT_TRANSFERRED,
    val completeness_status: SessionCompletenessStatus = SessionCompletenessStatus.NOT_CHECKED,
    val validity_status: RecordValidityStatus = RecordValidityStatus.VALID,
    val void_reason: String? = null,
    val voided_at: Long? = null,
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
        Index(value = ["session_id", "sensor_file_name"], unique = true)
    ]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val file_id: Long = 0,

    val session_id: Long,
    val sensor_file_name: String,
    val tablet_file_name: String? = null,
    val tablet_file_path: String? = null,
    val file_index: Int? = null,
    val recorded_at: Long? = null,
    val transferred_at: Long? = null,
    val transfer_status: MeasurementTransferStatus = MeasurementTransferStatus.NOT_TRANSFERRED,
    val file_completeness_status: FileCompletenessStatus = FileCompletenessStatus.NOT_CHECKED,
    val checksum: String? = null,
    val checksum_status: ChecksumStatus = ChecksumStatus.NOT_CHECKED,
    val file_size_bytes: Long? = null,
    val modified_at: Long? = null,
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
        Index(value = ["session_id", "model_version", "preprocessing_version"], unique = true)
    ]
)
data class ResultEntity(
    @PrimaryKey(autoGenerate = true)
    val result_id: Long = 0,

    val session_id: Long,
    val model_version: String,
    val preprocessing_version: String? = null,
    val analysis_config_json: String? = null,
    val prediction_label: PredictionLabel? = null,
    val probability_json: String? = null,
    val confidence: Double? = null,
    val input_file_count: Int? = null,
    val analysis_status: AnalysisStatus = AnalysisStatus.ANALYZED,
    val analyzed_at: Long = System.currentTimeMillis(),
    val export_status: ExportStatus = ExportStatus.NOT_EXPORTED,
    val exported_at: Long? = null,
    val modified_at: Long? = null,
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
      AND (:ageGroup IS NULL OR age = :ageGroup)
      AND (:studyGroup IS NULL OR study_group = :studyGroup)
      AND (:location IS NULL OR location = :location)
      AND (:validityStatus IS NULL OR validity_status = :validityStatus)
    ORDER BY patient_code ASC
    """
    )
    suspend fun searchPatients(
        patientCodeQuery: String?,
        sex: String?,
        ageGroup: String?,
        studyGroup: String?,
        location: String?,
        validityStatus: String?
    ): List<PatientEntity>
}

@Dao
interface DeviceDao {

    /** Inserts a new device record into the database and returns the generated device ID. */
    @Insert
    suspend fun insertDevice(device: DeviceEntity): Long

    /** Updates an existing device record in the database. */
    @Update
    suspend fun updateDevice(device: DeviceEntity)

    /** Retrieves all device records ordered by device code. */
    @Query("SELECT * FROM devices ORDER BY device_code ASC")
    suspend fun getAllDevices(): List<DeviceEntity>

    /** Retrieves a device matching the exact device code. */
    @Query("SELECT * FROM devices WHERE device_code = :deviceCode LIMIT 1")
    suspend fun getDeviceByCode(deviceCode: String): DeviceEntity?

    /** Retrieves a device matching the bluetooth address. */
    @Query("SELECT * FROM devices WHERE bluetooth_address = :bluetoothAddress LIMIT 1")
    suspend fun getDeviceByBluetoothAddress(bluetoothAddress: String): DeviceEntity?

    /** Searches and filters device records. */
    @Query(
        """
    SELECT * FROM devices
    WHERE (:deviceCodeQuery IS NULL OR device_code LIKE '%' || :deviceCodeQuery || '%')
      AND (:sensorStatus IS NULL OR sensor_status = :sensorStatus)
    ORDER BY device_code ASC
    """
    )
    suspend fun searchDevices(
        deviceCodeQuery: String?,
        sensorStatus: String?
    ): List<DeviceEntity>

    /** Deletes a device record by ID. */
    @Query("DELETE FROM devices WHERE device_id = :deviceId")
    suspend fun deleteDeviceById(deviceId: Long): Int
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

    /** Updates the transfer status of a specific measurement session. */
    @Query("UPDATE sessions SET transfer_status = :status, modified_at = :modifiedAt WHERE session_id = :sessionId")
    suspend fun updateSessionTransferStatus(sessionId: Long, status: SessionTransferStatus, modifiedAt: Long = System.currentTimeMillis())

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
      AND (:transferStatus IS NULL OR transfer_status = :transferStatus)
      AND (:completenessStatus IS NULL OR completeness_status = :completenessStatus)
      AND (:validityStatus IS NULL OR validity_status = :validityStatus)
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
        transferStatus: String?,
        completenessStatus: String?,
        validityStatus: String?,
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
    @Query("UPDATE measurements SET transfer_status = :status, modified_at = :modifiedAt WHERE file_id = :measurementId")
    suspend fun updateTransferStatus(measurementId: Long, status: MeasurementTransferStatus, modifiedAt: Long = System.currentTimeMillis()): Int

    /** Updates the transfer status and details for a specific measurement file. */
    @Query("UPDATE measurements SET transfer_status = :status, transferred_at = :transferredAt, checksum = :checksum, checksum_status = :checksumStatus, file_size_bytes = :fileSizeBytes, modified_at = :modifiedAt WHERE file_id = :fileId")
    suspend fun updateTransferDetails(
        fileId: Long,
        status: MeasurementTransferStatus,
        transferredAt: Long?,
        checksum: String?,
        checksumStatus: ChecksumStatus,
        fileSizeBytes: Long?,
        modifiedAt: Long = System.currentTimeMillis()
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
      AND (:transferStatus IS NULL OR transfer_status = :transferStatus)
      AND (:fileCompletenessStatus IS NULL OR file_completeness_status = :fileCompletenessStatus)
      AND (:checksumStatus IS NULL OR checksum_status = :checksumStatus)
      AND (:sensorFileName IS NULL OR sensor_file_name LIKE '%' || :sensorFileName || '%')
      AND (:tabletFileName IS NULL OR tablet_file_name LIKE '%' || :tabletFileName || '%')
      AND (:minFileSizeBytes IS NULL OR file_size_bytes >= :minFileSizeBytes)
      AND (:maxFileSizeBytes IS NULL OR file_size_bytes <= :maxFileSizeBytes)
      AND (:fromTransferredAt IS NULL OR transferred_at >= :fromTransferredAt)
      AND (:toTransferredAt IS NULL OR transferred_at <= :toTransferredAt)
    ORDER BY session_id DESC, file_id ASC
    """
    )
    suspend fun searchMeasurements(
        sessionId: Long?,
        transferStatus: String?,
        fileCompletenessStatus: String?,
        checksumStatus: String?,
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

    /** Inserts a new prediction result record and returns its generated result ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ResultEntity): Long

    /** Updates an existing prediction result record. */
    @Update
    suspend fun updateResult(result: ResultEntity): Int

    /** Retrieves all prediction results for a given session sorted by analysis timestamp. */
    @Query("SELECT * FROM results WHERE session_id = :sessionId ORDER BY analyzed_at DESC")
    suspend fun getResultsBySession(sessionId: Long): List<ResultEntity>

    /** Retrieves the most recent prediction result for a session. */
    @Query("SELECT * FROM results WHERE session_id = :sessionId ORDER BY analyzed_at DESC LIMIT 1")
    suspend fun getLatestResultBySession(sessionId: Long): ResultEntity?

    /** Retrieves a prediction result matching exact session ID, model version, and preprocessing version. */
    @Query("SELECT * FROM results WHERE session_id = :sessionId AND model_version = :modelVersion AND (:preprocessingVersion IS NULL OR preprocessing_version = :preprocessingVersion) LIMIT 1")
    suspend fun getResultByModelAndPreprocessing(sessionId: Long, modelVersion: String, preprocessingVersion: String?): ResultEntity?

    /** Updates the analysis status and export status of a result record. */
    @Query("UPDATE results SET analysis_status = :analysisStatus, export_status = :exportStatus, modified_at = :modifiedAt WHERE result_id = :resultId")
    suspend fun updateResultStatus(resultId: Long, analysisStatus: AnalysisStatus, exportStatus: ExportStatus, modifiedAt: Long = System.currentTimeMillis()): Int

    /** Deletes a specific prediction result record by its ID. */
    @Query("DELETE FROM results WHERE result_id = :resultId")
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
      AND (:analysisStatus IS NULL OR analysis_status = :analysisStatus)
      AND (:exportStatus IS NULL OR export_status = :exportStatus)
      AND (:modelVersion IS NULL OR model_version = :modelVersion)
      AND (:preprocessingVersion IS NULL OR preprocessing_version = :preprocessingVersion)
      AND (:minConfidence IS NULL OR confidence >= :minConfidence)
      AND (:maxConfidence IS NULL OR confidence <= :maxConfidence)
      AND (:fromAnalyzedAt IS NULL OR analyzed_at >= :fromAnalyzedAt)
      AND (:toAnalyzedAt IS NULL OR analyzed_at <= :toAnalyzedAt)
    ORDER BY analyzed_at DESC
    """
    )
    suspend fun searchResults(
        sessionId: Long?,
        predictionLabel: String?,
        analysisStatus: String?,
        exportStatus: String?,
        modelVersion: String?,
        preprocessingVersion: String?,
        minConfidence: Double?,
        maxConfidence: Double?,
        fromAnalyzedAt: Long?,
        toAnalyzedAt: Long?
    ): List<ResultEntity>
}


/**
 * Class definition & Enums
 */
enum class SexGender {
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

enum class AgeGroup {
    CHILD,
    ADULT,
    OLDER_ADULT,
    UNKNOWN
}

enum class StudyGroup {
    MF_POSITIVE,
    MF_NEGATIVE_LF_POSITIVE,
    CONTROL,
    UNCERTAIN
}

enum class RecordValidityStatus {
    VALID,
    VOIDED
}

enum class SensorStatus {
    NOT_CHECKED,
    OK,
    WARNING,
    ERROR
}

enum class ArmSide {
    LEFT,
    RIGHT,
    UNKNOWN
}

enum class SessionTransferStatus {
    NOT_TRANSFERRED,
    PARTIALLY_TRANSFERRED,
    TRANSFERRED,
    TRANSFER_FAILED
}

enum class SessionCompletenessStatus {
    NOT_CHECKED,
    COMPLETE,
    INCOMPLETE,
    NO_DATA_FOUND
}

enum class MeasurementTransferStatus {
    NOT_TRANSFERRED,
    TRANSFERRED,
    TRANSFER_FAILED
}

enum class FileCompletenessStatus {
    NOT_CHECKED,
    COMPLETE,
    INCOMPLETE,
    MISSING_ON_DEVICE
}

enum class ChecksumStatus {
    NOT_CHECKED,
    MATCHED,
    MISMATCHED,
    NOT_AVAILABLE
}

enum class AnalysisStatus {
    ANALYZED,
    ANALYSIS_FAILED
}

enum class ExportStatus {
    NOT_EXPORTED,
    EXPORTED,
    EXPORT_FAILED
}

enum class PredictionLabel(val displayName: String) {
    POSITIVE("POSITIVE"),
    NEGATIVE("NEGATIVE"),
    UNCLASSIFIED("UNCLASSIFIED"),
    MF_PLUS("MF+"),
    MF_MINUS_LF_PLUS("MF-LF+"),
    MF_MINUS("MF-"),
    NOT_ANALYSED("not analysed")
}
