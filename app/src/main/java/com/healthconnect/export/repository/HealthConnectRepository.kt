package com.healthconnect.export.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.VisibleForTesting
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthconnect.export.BuildConfig
import com.healthconnect.export.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

class HealthConnectRepository(
    private val context: Context,
) {
    companion object {
        private const val TAG = "HealthConnectRepo"
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        private const val PAGE_SIZE = 5000

        /**
         * Пакеты приложений для фильтрации dataOrigin, упорядоченные по приоритету.
         * Берётся из KNOWN_SOURCE_PACKAGES в DataModels.kt.
         * Если данные есть от нескольких источников, берётся первый из списка.
         */
        val PREFERRED_PACKAGES: List<String> =
            com.healthconnect.export.data.KNOWN_SOURCE_PACKAGES.keys
                .toList()
    }

    private var client: HealthConnectClient? = null
        get() {
            if (field == null) {
                try {
                    val status = HealthConnectClient.getSdkStatus(context)
                    if (status == HealthConnectClient.SDK_AVAILABLE) {
                        field = HealthConnectClient.getOrCreate(context)
                    }
                } catch (_: Exception) {
                    field = null
                }
            }
            return field
        }

    suspend fun isHealthConnectAvailable(): Boolean = client != null

    /**
     * Проверяет, установлено ли приложение Health Connect на устройстве
     */
    fun isHealthConnectInstalled(): Boolean =
        try {
            context.packageManager.getPackageInfo(HEALTH_CONNECT_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Создаёт Intent для открытия экрана разрешений Health Connect
     * через ACTION_REQUEST_PERMISSIONS с разрешениями в extra
     */
    fun createHealthPermissionsIntent(permissions: Set<String>): Intent =
        Intent("androidx.health.ACTION_REQUEST_PERMISSIONS").apply {
            `package` = HEALTH_CONNECT_PACKAGE
            putStringArrayListExtra(
                "androidx.health.extra.PERMISSION_NAMES",
                ArrayList(permissions),
            )
        }

    /**
     * Возвращает ActivityResultContract для запроса разрешений Health Connect
     */
    fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract(context.packageName)

    /**
     * Возвращает набор Health Connect разрешений для указанных типов данных
     */
    fun getPermissionsForTypes(types: Set<HealthDataType>): Set<String> {
        val permissions = mutableSetOf<String>()
        types.forEach { type ->
            when (type) {
                HealthDataType.STEPS -> permissions.add(HealthPermission.getReadPermission(StepsRecord::class))
                HealthDataType.HEART_RATE -> permissions.add(HealthPermission.getReadPermission(HeartRateRecord::class))
                HealthDataType.SLEEP -> permissions.add(HealthPermission.getReadPermission(SleepSessionRecord::class))
                HealthDataType.CALORIES -> permissions.add(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
                HealthDataType.DISTANCE -> permissions.add(HealthPermission.getReadPermission(DistanceRecord::class))
                HealthDataType.FLOORS_CLIMBED -> permissions.add(HealthPermission.getReadPermission(FloorsClimbedRecord::class))
                HealthDataType.ACTIVE_CALORIES -> permissions.add(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
                HealthDataType.WEIGHT -> permissions.add(HealthPermission.getReadPermission(WeightRecord::class))
                HealthDataType.BODY_FAT -> permissions.add(HealthPermission.getReadPermission(BodyFatRecord::class))
                HealthDataType.BLOOD_PRESSURE -> permissions.add(HealthPermission.getReadPermission(BloodPressureRecord::class))
                HealthDataType.BLOOD_GLUCOSE -> permissions.add(HealthPermission.getReadPermission(BloodGlucoseRecord::class))
                HealthDataType.OXYGEN_SATURATION -> permissions.add(HealthPermission.getReadPermission(OxygenSaturationRecord::class))
                HealthDataType.BODY_TEMPERATURE -> permissions.add(HealthPermission.getReadPermission(BodyTemperatureRecord::class))
                HealthDataType.RESPIRATORY_RATE -> permissions.add(HealthPermission.getReadPermission(RespiratoryRateRecord::class))
                HealthDataType.HYDRATION -> permissions.add(HealthPermission.getReadPermission(HydrationRecord::class))
                HealthDataType.RESTING_HEART_RATE -> permissions.add(HealthPermission.getReadPermission(RestingHeartRateRecord::class))
                HealthDataType.EXERCISE -> permissions.add(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
                HealthDataType.NUTRITION -> permissions.add(HealthPermission.getReadPermission(NutritionRecord::class))
                HealthDataType.MENSTRUATION -> permissions.add(HealthPermission.getReadPermission(MenstruationFlowRecord::class))
                HealthDataType.SPEED -> permissions.add(HealthPermission.getReadPermission(SpeedRecord::class))
            }
        }
        return permissions
    }

    /**
     * Проверяет, какие разрешения уже предоставлены
     */
    suspend fun getGrantedPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return c.permissionController.getGrantedPermissions()
    }

    /**
     * Проверяет, предоставлены ли все необходимые разрешения для указанных типов
     */
    suspend fun checkPermissions(types: Set<HealthDataType>): Boolean {
        val c = client ?: return false
        val required = getPermissionsForTypes(types)
        val granted = c.permissionController.getGrantedPermissions()
        return granted.containsAll(required)
    }

    /**
     * Discovers available data source packages by querying recent health records.
     * Returns a set of package names that have data available on device.
     */
    suspend fun getAvailableSources(): Set<String> =
        withContext(Dispatchers.IO) {
            val c = client ?: return@withContext emptySet()
            val now = Instant.now()
            val weekAgo = now.minus(7, ChronoUnit.DAYS)
            val timeFilter = TimeRangeFilter.between(weekAgo, now)

            val origins = mutableSetOf<String>()

            // Query steps as a representative data type to discover origins
            try {
                val stepsRequest =
                    ReadRecordsRequest(
                        StepsRecord::class,
                        timeRangeFilter = timeFilter,
                        pageSize = 1,
                    )
                val stepsResponse = c.readRecords(stepsRequest)
                stepsResponse.records.forEach { record ->
                    record.metadata.dataOrigin
                        ?.packageName
                        ?.let { origins.add(it) }
                }
            } catch (_: Exception) {
            }

            // Also query heart rate for more complete origin discovery
            try {
                val hrRequest =
                    ReadRecordsRequest(
                        HeartRateRecord::class,
                        timeRangeFilter = timeFilter,
                        pageSize = 1,
                    )
                val hrResponse = c.readRecords(hrRequest)
                hrResponse.records.forEach { record ->
                    record.metadata.dataOrigin
                        ?.packageName
                        ?.let { origins.add(it) }
                }
            } catch (_: Exception) {
            }

            Log.d(TAG, "getAvailableSources: $origins")
            origins
        }

    // ===== Pagination helper =====

    /**
     * Вычитывает ВСЕ записи для указанного типа, используя пагинацию.
     * Стандартный readRecords без pageToken возвращает до 10 000 записей,
     * а на некоторых прошивках (MIUI/HyperOS) может быть меньше (напр. 1000).
     * Чтобы гарантированно получить всё, используем pageToken из ответа.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun <T : Record> readAllPages(
        request: ReadRecordsRequest<T>,
        onPageProgress: ((typeName: String, pageNumber: Int) -> Unit)? = null,
    ): List<T> {
        val c = client ?: return emptyList()
        val allRecords = mutableListOf<T>()
        var pageToken: String? = null
        var pageCount = 0
        val maxPages = 100 // Safety limit to prevent infinite loops
        do {
            pageCount++
            if (pageCount > maxPages) {
                Log.w(TAG, "readAllPages: exceeded max pages ($maxPages), stopping")
                break
            }
            val recordType = request.recordType
            val typeName = recordType.simpleName ?: "Unknown"
            onPageProgress?.invoke(typeName, pageCount)
            val pageRequest =
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = request.timeRangeFilter,
                    dataOriginFilter = request.dataOriginFilter,
                    ascendingOrder = request.ascendingOrder,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                )
            val response = c.readRecords(pageRequest)
            val recordsOnPage = response.records.size
            if (recordsOnPage == 0 && pageToken != null) {
                Log.w(TAG, "readAllPages: empty page with non-null pageToken, stopping")
                break
            }
            allRecords.addAll(response.records)
            pageToken = response.pageToken
            Log.d(
                TAG,
                "readAllPages(${request.recordType.simpleName}): page=$pageCount, got=$recordsOnPage, total=${allRecords.size}, nextToken=$pageToken",
            )
        } while (pageToken != null)
        return allRecords
    }

    // ===== Extraction functions (pure aggregation logic, no API calls) =====

    private fun extractSteps(
        records: List<StepsRecord>,
        selectedSourcePackage: String? = null,
    ): StepsData? {
        if (records.isEmpty()) return null
        val filtered = filterByPreferredOrigin(records, selectedSourcePackage)
        if (filtered.isEmpty()) return null
        val total = filtered.sumOf { it.count }
        Log.d(TAG, "extractSteps: records=${records.size}, filtered=${filtered.size}, totalSteps=$total")
        return StepsData(totalSteps = total, recordsCount = filtered.size)
    }

    private fun extractHeartRate(records: List<HeartRateRecord>): HeartRateData? {
        if (records.isEmpty()) return null
        val samples = records.flatMap { it.samples }
        if (samples.isEmpty()) return null
        val beats = samples.map { it.beatsPerMinute }
        return HeartRateData(
            avgBpm = beats.average(),
            minBpm = beats.min().toInt(),
            maxBpm = beats.max().toInt(),
            recordsCount = records.size,
        )
    }

    private fun extractSleep(
        records: List<SleepSessionRecord>,
        selectedSourcePackage: String? = null,
    ): SleepData? {
        if (records.isEmpty()) return null
        val kept = dedupeSleep(filterByPreferredOrigin(records, selectedSourcePackage))
        if (kept.isEmpty()) return null
        val totalMinutes =
            kept.sumOf {
                ChronoUnit.MINUTES.between(it.startTime, it.endTime)
            }
        val stageDurations = mutableMapOf<String, Long>()
        kept.forEach { record ->
            record.stages.forEach { stage ->
                val name = sleepStageToString(stage.stage) ?: "Unknown"
                val duration = ChronoUnit.MINUTES.between(stage.startTime, stage.endTime)
                if (duration > 0) {
                    stageDurations[name] = (stageDurations[name] ?: 0) + duration
                }
            }
        }
        return SleepData(
            totalDurationMinutes = totalMinutes,
            sleepStages = stageDurations,
            recordsCount = kept.size,
            sessions =
                kept.map {
                    SleepSessionData(
                        startTime = it.startTime.toDateTimeString(),
                        endTime = it.endTime.toDateTimeString(),
                        durationMinutes = ChronoUnit.MINUTES.between(it.startTime, it.endTime),
                        source = it.metadata.dataOrigin.packageName,
                    )
                },
        )
    }

    /**
     * The same night can be written more than once by one app (a preliminary and a final version).
     * Overlapping sessions are collapsed into the longest one, so a night is never counted twice.
     */
    private fun dedupeSleep(records: List<SleepSessionRecord>): List<SleepSessionRecord> {
        val kept = mutableListOf<SleepSessionRecord>()
        records.sortedBy { it.startTime }.forEach { record ->
            val last = kept.lastOrNull()
            if (last != null && record.startTime.isBefore(last.endTime)) {
                val lastMinutes = ChronoUnit.MINUTES.between(last.startTime, last.endTime)
                val minutes = ChronoUnit.MINUTES.between(record.startTime, record.endTime)
                if (minutes > lastMinutes) kept[kept.size - 1] = record
            } else {
                kept.add(record)
            }
        }
        return kept
    }

    private fun extractCalories(
        records: List<TotalCaloriesBurnedRecord>,
        selectedSourcePackage: String? = null,
    ): CaloriesData? {
        if (records.isEmpty()) return null
        val filtered = filterByPreferredOrigin(records, selectedSourcePackage)
        if (filtered.isEmpty()) return null
        val total = filtered.sumOf { it.energy.inKilocalories }
        return CaloriesData(totalCalories = total, recordsCount = filtered.size)
    }

    private fun extractDistance(
        records: List<DistanceRecord>,
        selectedSourcePackage: String? = null,
    ): DistanceData? {
        if (records.isEmpty()) return null
        val filtered = filterByPreferredOrigin(records, selectedSourcePackage)
        if (filtered.isEmpty()) return null
        val total = filtered.sumOf { it.distance.inMeters }
        return DistanceData(totalDistanceMeters = total, recordsCount = filtered.size)
    }

    private fun extractFloorsClimbed(
        records: List<FloorsClimbedRecord>,
        selectedSourcePackage: String? = null,
    ): FloorsClimbedData? {
        if (records.isEmpty()) return null
        val filtered = filterByPreferredOrigin(records, selectedSourcePackage)
        if (filtered.isEmpty()) return null
        val total = filtered.sumOf { it.floors }
        return FloorsClimbedData(totalFloors = total, recordsCount = filtered.size)
    }

    private fun extractActiveCalories(
        records: List<ActiveCaloriesBurnedRecord>,
        selectedSourcePackage: String? = null,
    ): ActiveCaloriesData? {
        if (records.isEmpty()) return null
        val filtered = filterByPreferredOrigin(records, selectedSourcePackage)
        if (filtered.isEmpty()) return null
        val total = filtered.sumOf { it.energy.inKilocalories }
        return ActiveCaloriesData(totalCalories = total, recordsCount = filtered.size)
    }

    private fun extractWeight(records: List<WeightRecord>): WeightData? {
        if (records.isEmpty()) return null
        val avg = records.map { it.weight.inKilograms }.average()
        return WeightData(weightKg = avg, recordsCount = records.size)
    }

    private fun extractBodyFat(records: List<BodyFatRecord>): BodyFatData? {
        if (records.isEmpty()) return null
        val avg = records.map { it.percentage.value }.average()
        return BodyFatData(percentage = avg, recordsCount = records.size)
    }

    private fun extractBloodPressure(records: List<BloodPressureRecord>): BloodPressureData? {
        if (records.isEmpty()) return null
        val systolicAvg = records.map { it.systolic.inMillimetersOfMercury }.average()
        val diastolicAvg = records.map { it.diastolic.inMillimetersOfMercury }.average()
        val position = bodyPositionToString(records.firstOrNull()?.bodyPosition)
        return BloodPressureData(
            systolicMmHg = systolicAvg,
            diastolicMmHg = diastolicAvg,
            bodyPosition = position,
            recordsCount = records.size,
        )
    }

    private fun extractBloodGlucose(records: List<BloodGlucoseRecord>): BloodGlucoseData? {
        if (records.isEmpty()) return null
        val avg = records.map { it.level.inMillimolesPerLiter }.average()
        val source = specimenSourceToString(records.firstOrNull()?.specimenSource)
        val mealType = mealTypeToString(records.firstOrNull()?.mealType)
        return BloodGlucoseData(
            level = avg,
            specimenSource = source,
            mealType = mealType,
            recordsCount = records.size,
        )
    }

    private fun extractOxygenSaturation(records: List<OxygenSaturationRecord>): OxygenSaturationData? {
        if (records.isEmpty()) return null
        val avg = records.map { it.percentage.value }.average()
        return OxygenSaturationData(percentage = avg, recordsCount = records.size)
    }

    private fun extractBodyTemperature(records: List<BodyTemperatureRecord>): BodyTemperatureData? {
        if (records.isEmpty()) return null
        val avg = records.map { it.temperature.inCelsius }.average()
        val location = measurementLocationToString(records.firstOrNull()?.measurementLocation)
        return BodyTemperatureData(
            temperatureCelsius = avg,
            measurementLocation = location,
            recordsCount = records.size,
        )
    }

    private fun extractRespiratoryRate(records: List<RespiratoryRateRecord>): RespiratoryRateData? {
        if (records.isEmpty()) return null
        val avg = records.map { it.rate }.average()
        return RespiratoryRateData(rate = avg, recordsCount = records.size)
    }

    private fun extractHydration(records: List<HydrationRecord>): HydrationData? {
        if (records.isEmpty()) return null
        val total = records.sumOf { it.volume.inLiters }
        return HydrationData(totalVolumeLiters = total, recordsCount = records.size)
    }

    private fun extractRestingHeartRate(records: List<RestingHeartRateRecord>): RestingHeartRateData? {
        if (records.isEmpty()) return null
        val bpmValues = records.map { it.beatsPerMinute }
        return RestingHeartRateData(
            avgBpm = bpmValues.average(),
            minBpm = bpmValues.min(),
            maxBpm = bpmValues.max(),
            recordsCount = records.size,
        )
    }

    private fun extractExercises(records: List<ExerciseSessionRecord>): List<ExerciseData>? {
        if (records.isEmpty()) return null
        return records.map { record ->
            ExerciseData(
                exerciseType = exerciseTypeToString(record.exerciseType) ?: "Unknown",
                startTime = record.startTime.toDateTimeString(),
                endTime = record.endTime.toDateTimeString(),
                durationMinutes = ChronoUnit.MINUTES.between(record.startTime, record.endTime),
                title = record.title,
                notes = record.notes,
            )
        }
    }

    private fun extractNutrition(records: List<NutritionRecord>): List<NutritionData>? {
        if (records.isEmpty()) return null
        return records.map { record ->
            NutritionData(
                name = record.name,
                mealType = nutritionMealTypeToString(record.mealType),
                energyKcal = record.energy?.inKilocalories,
                proteinG = record.protein?.inGrams,
                totalCarbohydrateG = record.totalCarbohydrate?.inGrams,
                fatG = record.totalFat?.inGrams,
                dietaryFiberG = record.dietaryFiber?.inGrams,
                sugarG = record.sugar?.inGrams,
                saturatedFatG = record.saturatedFat?.inGrams,
                transFatG = record.transFat?.inGrams,
                cholesterolMg = record.cholesterol?.inMilligrams,
                sodiumMg = record.sodium?.inMilligrams,
                potassiumMg = record.potassium?.inMilligrams,
                caffeineMg = record.caffeine?.inMilligrams,
                calciumMg = record.calcium?.inMilligrams,
                ironMg = record.iron?.inMilligrams,
                magnesiumMg = record.magnesium?.inMilligrams,
                vitaminCMg = record.vitaminC?.inMilligrams,
                vitaminDMcg = record.vitaminD?.inMicrograms,
            )
        }
    }

    private fun extractSpeed(records: List<SpeedRecord>): SpeedData? {
        if (records.isEmpty()) return null
        val allSamples = records.flatMap { it.samples }
        if (allSamples.isEmpty()) return null
        val avgSpeed = allSamples.map { it.speed.inMetersPerSecond }.average()
        return SpeedData(
            avgSpeedMetersPerSecond = avgSpeed,
            recordsCount = records.size,
        )
    }

    private fun extractMenstruation(records: List<MenstruationFlowRecord>): MenstruationData? {
        if (records.isEmpty()) return null
        val record = records.first()
        return MenstruationData(
            flowType = menstruationFlowToString(record.flow),
            time = record.time.toDateTimeString(),
        )
    }

    // ===== Batch period reader =====

    /**
     * Reads health data for an entire period in batch mode.
     * For each data type, makes ONE API call to read ALL records for the full period,
     * then groups results by day. This avoids rate limiting from N×M calls (N=days, M=types).
     */
    // ── Type handler for data-type-agnostic batch processing ──

    private class TypeHandler(
        val recordClass: KClass<out Record>,
        val timeSelector: (Any) -> Instant,
        val extract: (List<*>, String?) -> Any?,
        val updateRecord: (DailyHealthRecord, Any?) -> DailyHealthRecord,
    )

    private val typeHandlers: Map<HealthDataType, TypeHandler> =
        mapOf(
            HealthDataType.STEPS to
                TypeHandler(
                    StepsRecord::class,
                    { (it as StepsRecord).startTime },
                    { r, p -> extractSteps(r as List<StepsRecord>, p) },
                    { r, d -> r.copy(steps = d as StepsData?) },
                ),
            HealthDataType.HEART_RATE to
                TypeHandler(
                    HeartRateRecord::class,
                    { (it as HeartRateRecord).startTime },
                    { r, _ -> extractHeartRate(r as List<HeartRateRecord>) },
                    { r, d -> r.copy(heartRate = d as HeartRateData?) },
                ),
            HealthDataType.SLEEP to
                TypeHandler(
                    SleepSessionRecord::class,
                    // A night belongs to the day you wake up (23:30 -> 07:00 counts for the morning it ends)
                    { (it as SleepSessionRecord).endTime },
                    { r, p -> extractSleep(r as List<SleepSessionRecord>, p) },
                    { r, d -> r.copy(sleep = d as SleepData?) },
                ),
            HealthDataType.CALORIES to
                TypeHandler(
                    TotalCaloriesBurnedRecord::class,
                    { (it as TotalCaloriesBurnedRecord).startTime },
                    { r, p -> extractCalories(r as List<TotalCaloriesBurnedRecord>, p) },
                    { r, d -> r.copy(calories = d as CaloriesData?) },
                ),
            HealthDataType.DISTANCE to
                TypeHandler(
                    DistanceRecord::class,
                    { (it as DistanceRecord).startTime },
                    { r, p -> extractDistance(r as List<DistanceRecord>, p) },
                    { r, d -> r.copy(distance = d as DistanceData?) },
                ),
            HealthDataType.FLOORS_CLIMBED to
                TypeHandler(
                    FloorsClimbedRecord::class,
                    { (it as FloorsClimbedRecord).startTime },
                    { r, p -> extractFloorsClimbed(r as List<FloorsClimbedRecord>, p) },
                    { r, d -> r.copy(floorsClimbed = d as FloorsClimbedData?) },
                ),
            HealthDataType.ACTIVE_CALORIES to
                TypeHandler(
                    ActiveCaloriesBurnedRecord::class,
                    { (it as ActiveCaloriesBurnedRecord).startTime },
                    { r, p -> extractActiveCalories(r as List<ActiveCaloriesBurnedRecord>, p) },
                    { r, d -> r.copy(activeCalories = d as ActiveCaloriesData?) },
                ),
            HealthDataType.WEIGHT to
                TypeHandler(
                    WeightRecord::class,
                    { (it as WeightRecord).time },
                    { r, _ -> extractWeight(r as List<WeightRecord>) },
                    { r, d -> r.copy(weight = d as WeightData?) },
                ),
            HealthDataType.BODY_FAT to
                TypeHandler(
                    BodyFatRecord::class,
                    { (it as BodyFatRecord).time },
                    { r, _ -> extractBodyFat(r as List<BodyFatRecord>) },
                    { r, d -> r.copy(bodyFat = d as BodyFatData?) },
                ),
            HealthDataType.BLOOD_PRESSURE to
                TypeHandler(
                    BloodPressureRecord::class,
                    { (it as BloodPressureRecord).time },
                    { r, _ -> extractBloodPressure(r as List<BloodPressureRecord>) },
                    { r, d -> r.copy(bloodPressure = d as BloodPressureData?) },
                ),
            HealthDataType.BLOOD_GLUCOSE to
                TypeHandler(
                    BloodGlucoseRecord::class,
                    { (it as BloodGlucoseRecord).time },
                    { r, _ -> extractBloodGlucose(r as List<BloodGlucoseRecord>) },
                    { r, d -> r.copy(bloodGlucose = d as BloodGlucoseData?) },
                ),
            HealthDataType.OXYGEN_SATURATION to
                TypeHandler(
                    OxygenSaturationRecord::class,
                    { (it as OxygenSaturationRecord).time },
                    { r, _ -> extractOxygenSaturation(r as List<OxygenSaturationRecord>) },
                    { r, d -> r.copy(oxygenSaturation = d as OxygenSaturationData?) },
                ),
            HealthDataType.BODY_TEMPERATURE to
                TypeHandler(
                    BodyTemperatureRecord::class,
                    { (it as BodyTemperatureRecord).time },
                    { r, _ -> extractBodyTemperature(r as List<BodyTemperatureRecord>) },
                    { r, d -> r.copy(bodyTemperature = d as BodyTemperatureData?) },
                ),
            HealthDataType.RESPIRATORY_RATE to
                TypeHandler(
                    RespiratoryRateRecord::class,
                    { (it as RespiratoryRateRecord).time },
                    { r, _ -> extractRespiratoryRate(r as List<RespiratoryRateRecord>) },
                    { r, d -> r.copy(respiratoryRate = d as RespiratoryRateData?) },
                ),
            HealthDataType.HYDRATION to
                TypeHandler(
                    HydrationRecord::class,
                    { (it as HydrationRecord).startTime },
                    { r, _ -> extractHydration(r as List<HydrationRecord>) },
                    { r, d -> r.copy(hydration = d as HydrationData?) },
                ),
            HealthDataType.RESTING_HEART_RATE to
                TypeHandler(
                    RestingHeartRateRecord::class,
                    { (it as RestingHeartRateRecord).time },
                    { r, _ -> extractRestingHeartRate(r as List<RestingHeartRateRecord>) },
                    { r, d -> r.copy(restingHeartRate = d as RestingHeartRateData?) },
                ),
            HealthDataType.EXERCISE to
                TypeHandler(
                    ExerciseSessionRecord::class,
                    { (it as ExerciseSessionRecord).startTime },
                    { r, _ -> extractExercises(r as List<ExerciseSessionRecord>) },
                    { r, d -> r.copy(exercises = d as List<ExerciseData>?) },
                ),
            HealthDataType.NUTRITION to
                TypeHandler(
                    NutritionRecord::class,
                    { (it as NutritionRecord).startTime },
                    { r, _ -> extractNutrition(r as List<NutritionRecord>) },
                    { r, d -> r.copy(nutrition = d as List<NutritionData>?) },
                ),
            HealthDataType.SPEED to
                TypeHandler(
                    SpeedRecord::class,
                    { (it as SpeedRecord).startTime },
                    { r, _ -> extractSpeed(r as List<SpeedRecord>) },
                    { r, d -> r.copy(speed = d as SpeedData?) },
                ),
            HealthDataType.MENSTRUATION to
                TypeHandler(
                    MenstruationFlowRecord::class,
                    { (it as MenstruationFlowRecord).time },
                    { r, _ -> extractMenstruation(r as List<MenstruationFlowRecord>) },
                    { r, d -> r.copy(menstruation = d as MenstruationData?) },
                ),
        )

    /**
     * Processes a single health data type: reads all pages, groups by day,
     * extracts aggregated data, and updates the daysMap.
     */
    private suspend fun processTypeData(
        handler: TypeHandler,
        type: HealthDataType,
        daysMap: MutableMap<String, DailyHealthRecord>,
        timeFilter: TimeRangeFilter,
        startDate: LocalDate,
        endDate: LocalDate,
        selectedSourcePackage: String?,
        onPageProgress: ((typeName: String, pageNumber: Int) -> Unit)?,
    ) {
        val typeName = type.displayName
        // When a specific source is selected, push the filter down to the API so we don't
        // read (and then discard) records from every other source. In auto mode we still
        // read all sources and let filterByPreferredOrigin pick the best one.
        val originFilter: Set<DataOrigin>? =
            selectedSourcePackage
                ?.takeIf { it.isNotBlank() }
                ?.let { setOf(DataOrigin(it)) }
        val allRecords =
            if (originFilter != null) {
                readAllPages(
                    ReadRecordsRequest(handler.recordClass, timeRangeFilter = timeFilter, dataOriginFilter = originFilter),
                ) { _, page -> onPageProgress?.invoke(typeName, page) }
            } else {
                readAllPages(
                    ReadRecordsRequest(handler.recordClass, timeRangeFilter = timeFilter),
                ) { _, page -> onPageProgress?.invoke(typeName, page) }
            }

        val byDay =
            if (type == HealthDataType.SLEEP) {
                // Sleep is never clamped: a night that ended before the window belongs to its own day,
                // and clamping it would add it to the first day of the window (counting it twice).
                allRecords
                    .map { record -> handler.timeSelector(record).atZone(ZoneId.systemDefault()).toLocalDate() to record }
                    .filter { (day, _) -> !day.isBefore(startDate) && !day.isAfter(endDate) }
                    .groupBy({ it.first.toString() }, { it.second })
            } else {
                allRecords.groupBy { record -> clampToWindow(handler.timeSelector(record), startDate, endDate).toString() }
            }
        byDay.forEach { (dateStr, records) ->
            // Only reachable if the caller seeded daysMap with a different window.
            val existing = daysMap[dateStr] ?: return@forEach
            daysMap[dateStr] = handler.updateRecord(existing, handler.extract(records, selectedSourcePackage))
        }

        Log.d(TAG, "readPeriodInBatch ${type.name}: total=${allRecords.size}, days=${byDay.size}")
    }

    /**
     * Maps an instant onto the day it is attributed to inside the export window.
     *
     * Health Connect returns every interval record that merely *overlaps* the
     * requested range, not only those starting inside it: a sleep session running
     * 23:00 -> 07:00 comes back for a window that begins at 00:00 of the next day,
     * even though its start day lies outside the window. The caller pre-seeds its
     * day map with exactly the requested days, so attributing such a record to its
     * own start day would abort the whole export.
     *
     * Clamping to the nearest window boundary keeps the data and the export alive.
     * This is the common case rather than an edge case for the background workers,
     * which read a single day at a time.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun clampToWindow(
        instant: Instant,
        startDate: LocalDate,
        endDate: LocalDate,
    ): LocalDate {
        val day = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return when {
            day.isBefore(startDate) -> startDate
            day.isAfter(endDate) -> endDate
            else -> day
        }
    }

    suspend fun readPeriodInBatch(
        startDate: LocalDate,
        endDate: LocalDate,
        types: Set<HealthDataType>,
        selectedSourcePackage: String? = null,
        onPageProgress: ((typeName: String, pageNumber: Int) -> Unit)? = null,
    ): List<DailyHealthRecord> =
        withContext(Dispatchers.IO) {
            val start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val timeFilter = TimeRangeFilter.between(start, end)

            Log.d(TAG, "readPeriodInBatch: start=$startDate, end=$endDate, types=$types, source=$selectedSourcePackage")
            Log.d(TAG, "readPeriodInBatch: timeFilter start=$start, end=$end")

            val metadata =
                ExportMetadata(
                    appVersion = BuildConfig.VERSION_NAME,
                    exportTimestamp = Instant.now().toDateTimeString(),
                    timezone = ZoneId.systemDefault().id,
                    sourceDevice = android.os.Build.MODEL,
                )

            // Build a map: date -> record, pre-populated for every day in range
            val daysMap = mutableMapOf<String, DailyHealthRecord>()
            var current = startDate
            while (!current.isAfter(endDate)) {
                daysMap[current.toString()] =
                    DailyHealthRecord(
                        date = current.toString(),
                        metadata = metadata,
                    )
                current = current.plusDays(1)
            }

            // Process each requested data type through its registered handler
            types.forEach { type ->
                val handler = typeHandlers[type]
                if (handler != null) {
                    processTypeData(handler, type, daysMap, timeFilter, startDate, endDate, selectedSourcePackage, onPageProgress)
                } else {
                    Log.w(TAG, "readPeriodInBatch: no handler registered for $type")
                }
            }

            // Build sorted result list (compatible with minSdk 28)
            val result = mutableListOf<DailyHealthRecord>()
            var day = startDate
            while (!day.isAfter(endDate)) {
                result.add(daysMap[day.toString()] ?: DailyHealthRecord(date = day.toString(), metadata = metadata))
                day = day.plusDays(1)
            }

            val daysWithData =
                result.count { r ->
                    r.steps != null ||
                        r.heartRate != null ||
                        r.sleep != null ||
                        r.calories != null ||
                        r.distance != null ||
                        r.weight != null
                }
            Log.d(TAG, "readPeriodInBatch: completed, ${result.size} days total, $daysWithData days with data")
            result
        }

    // ===== Private readers (now delegate to extraction functions) =====

    /**
     * Фильтрует записи по предпочитаемому пакету-источнику данных.
     * Необходимо, чтобы одни и те же данные не суммировались от разных источников одновременно.
     *
     * @param selectedSourcePackage если задан — используется только этот источник
     * @param records записи для фильтрации
     * @return отфильтрованные записи от одного источника
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun <T> filterByPreferredOrigin(
        records: List<T>,
        selectedSourcePackage: String? = null,
    ): List<T> {
        if (records.isEmpty()) return records

        val groupedByOrigin =
            records.groupBy { record ->
                val origin = (record as? androidx.health.connect.client.records.Record)?.metadata?.dataOrigin
                origin?.packageName ?: "unknown"
            }

        Log.d(TAG, "filterByPreferredOrigin: sources=${groupedByOrigin.keys}, selected=$selectedSourcePackage")

        // 0. If user explicitly selected a source, use it (even if not in preferred list)
        if (selectedSourcePackage != null) {
            val selected = groupedByOrigin[selectedSourcePackage]
            if (selected != null) {
                Log.d(TAG, "filterByPreferredOrigin: using user-selected '$selectedSourcePackage' (${selected.size} records)")
                return selected
            }
            Log.w(TAG, "filterByPreferredOrigin: user-selected '$selectedSourcePackage' not found in sources, falling back to auto")
        }

        // 1. Try preferred packages in order
        for (preferred in PREFERRED_PACKAGES) {
            if (groupedByOrigin.containsKey(preferred)) {
                val selected = groupedByOrigin[preferred]!!
                Log.d(
                    TAG,
                    "filterByPreferredOrigin: using '$preferred' (${selected.size} records), ignoring ${groupedByOrigin.keys - preferred}",
                )
                return selected
            }
        }

        // 2. Fallback: pick the source with the most records (never mix sources)
        val bestSource = groupedByOrigin.maxByOrNull { (_, records) -> records.size }
        if (bestSource != null) {
            val (source, selected) = bestSource
            Log.d(
                TAG,
                "filterByPreferredOrigin: no preferred, using '$source' with most records (${selected.size}), ignoring ${groupedByOrigin.keys - source}",
            )
            return selected
        }

        return records
    }
}
