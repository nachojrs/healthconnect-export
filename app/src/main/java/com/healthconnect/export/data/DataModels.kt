package com.healthconnect.export.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ===== Core data classes for Health Connect =====

@Serializable
data class DailyHealthRecord(
    @SerialName("date") val date: String,
    @SerialName("steps") val steps: StepsData? = null,
    @SerialName("heart_rate") val heartRate: HeartRateData? = null,
    @SerialName("sleep") val sleep: SleepData? = null,
    @SerialName("calories") val calories: CaloriesData? = null,
    @SerialName("distance") val distance: DistanceData? = null,
    @SerialName("floors_climbed") val floorsClimbed: FloorsClimbedData? = null,
    @SerialName("active_calories") val activeCalories: ActiveCaloriesData? = null,
    @SerialName("weight") val weight: WeightData? = null,
    @SerialName("body_fat") val bodyFat: BodyFatData? = null,
    @SerialName("blood_pressure") val bloodPressure: BloodPressureData? = null,
    @SerialName("blood_glucose") val bloodGlucose: BloodGlucoseData? = null,
    @SerialName("oxygen_saturation") val oxygenSaturation: OxygenSaturationData? = null,
    @SerialName("body_temperature") val bodyTemperature: BodyTemperatureData? = null,
    @SerialName("respiratory_rate") val respiratoryRate: RespiratoryRateData? = null,
    @SerialName("hydration") val hydration: HydrationData? = null,
    @SerialName("resting_heart_rate") val restingHeartRate: RestingHeartRateData? = null,
    @SerialName("exercises") val exercises: List<ExerciseData>? = null,
    @SerialName("nutrition") val nutrition: List<NutritionData>? = null,
    @SerialName("speed") val speed: SpeedData? = null,
    @SerialName("menstruation") val menstruation: MenstruationData? = null,
    @SerialName("metadata") val metadata: ExportMetadata,
)

@Serializable
data class StepsData(
    @SerialName("total_steps") val totalSteps: Long,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class HeartRateData(
    @SerialName("avg_bpm") val avgBpm: Double,
    @SerialName("min_bpm") val minBpm: Int,
    @SerialName("max_bpm") val maxBpm: Int,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class SleepData(
    @SerialName("total_duration_minutes") val totalDurationMinutes: Long,
    @SerialName("sleep_stages") val sleepStages: Map<String, Long>,
    @SerialName("records_count") val recordsCount: Int,
    @SerialName("sessions") val sessions: List<SleepSessionData> = emptyList(),
)

/** One sleep session as counted for the day: when it started and ended, and which app wrote it. */
@Serializable
data class SleepSessionData(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("duration_minutes") val durationMinutes: Long,
    @SerialName("source") val source: String,
)

@Serializable
data class CaloriesData(
    @SerialName("total_calories_kcal") val totalCalories: Double,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class DistanceData(
    @SerialName("total_distance_meters") val totalDistanceMeters: Double,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class FloorsClimbedData(
    @SerialName("total_floors") val totalFloors: Double,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class ActiveCaloriesData(
    @SerialName("total_calories_kcal") val totalCalories: Double,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class WeightData(
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class BodyFatData(
    @SerialName("percentage") val percentage: Double,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class BloodPressureData(
    @SerialName("systolic_mmhg") val systolicMmHg: Double,
    @SerialName("diastolic_mmhg") val diastolicMmHg: Double,
    @SerialName("body_position") val bodyPosition: String? = null,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class BloodGlucoseData(
    @SerialName("level_mmol_per_l") val level: Double,
    @SerialName("specimen_source") val specimenSource: String? = null,
    @SerialName("meal_type") val mealType: String? = null,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class OxygenSaturationData(
    @SerialName("percentage") val percentage: Double,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class BodyTemperatureData(
    @SerialName("temperature_celsius") val temperatureCelsius: Double,
    @SerialName("measurement_location") val measurementLocation: String? = null,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class RespiratoryRateData(
    @SerialName("rate") val rate: Double,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class HydrationData(
    @SerialName("total_volume_liters") val totalVolumeLiters: Double,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class RestingHeartRateData(
    @SerialName("avg_bpm") val avgBpm: Double,
    @SerialName("min_bpm") val minBpm: Long,
    @SerialName("max_bpm") val maxBpm: Long,
    @SerialName("records_count") val recordsCount: Int,
)

@Serializable
data class ExerciseData(
    @SerialName("exercise_type") val exerciseType: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("duration_minutes") val durationMinutes: Long,
    @SerialName("title") val title: String? = null,
    @SerialName("notes") val notes: String? = null,
)

@Serializable
data class NutritionData(
    @SerialName("name") val name: String? = null,
    @SerialName("meal_type") val mealType: String? = null,
    @SerialName("energy_kcal") val energyKcal: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("total_carbohydrate_g") val totalCarbohydrateG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("dietary_fiber_g") val dietaryFiberG: Double? = null,
    @SerialName("sugar_g") val sugarG: Double? = null,
    @SerialName("saturated_fat_g") val saturatedFatG: Double? = null,
    @SerialName("trans_fat_g") val transFatG: Double? = null,
    @SerialName("cholesterol_mg") val cholesterolMg: Double? = null,
    @SerialName("sodium_mg") val sodiumMg: Double? = null,
    @SerialName("potassium_mg") val potassiumMg: Double? = null,
    @SerialName("caffeine_mg") val caffeineMg: Double? = null,
    @SerialName("calcium_mg") val calciumMg: Double? = null,
    @SerialName("iron_mg") val ironMg: Double? = null,
    @SerialName("magnesium_mg") val magnesiumMg: Double? = null,
    @SerialName("vitamin_c_mg") val vitaminCMg: Double? = null,
    @SerialName("vitamin_d_mcg") val vitaminDMcg: Double? = null,
)

@Serializable
data class MenstruationData(
    @SerialName("flow_type") val flowType: String? = null,
    @SerialName("time") val time: String,
)

@Serializable
data class SpeedData(
    @SerialName("avg_speed_meters_per_second") val avgSpeedMetersPerSecond: Double? = null,
    @SerialName("records_count") val recordsCount: Int = 0,
)

@Serializable
data class ExportMetadata(
    @SerialName("app_version") val appVersion: String,
    @SerialName("export_timestamp") val exportTimestamp: String,
    @SerialName("timezone") val timezone: String,
    @SerialName("source_device") val sourceDevice: String? = null,
)

// ===== Export Configuration =====

@Serializable
enum class ExportFrequency(
    val displayName: String,
    val hours: Long,
) {
    MANUAL("Manual", 0),
    DAILY("Daily", 24),
    WEEKLY("Weekly", 168),
}

/**
 * Output format for exported health data files.
 */
@Serializable
enum class ExportFormat(
    val displayName: String,
) {
    JSON("JSON"),
    CSV("CSV"),
}

/**
 * Preset period selection for the export date range.
 *
 * When [LAST_7_DAYS] or [LAST_30_DAYS] is active, the actual start/end dates are
 * recomputed from the current date on every launch and before each export, so the
 * window keeps sliding instead of freezing on the dates first picked.
 */
@Serializable
enum class DateRangePreset(
    val displayName: String,
) {
    NONE("Custom"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    ;

    /**
     * Computes the [start, end] date window for this preset relative to [referenceDate].
     * 7 days → last 7 days including today; 30 days → last 30 days including today.
     * [NONE] is a custom range and has no meaning here — callers must handle it separately.
     */
    fun calcRange(referenceDate: LocalDate): Pair<LocalDate, LocalDate> =
        when (this) {
            LAST_7_DAYS -> referenceDate.minusDays(6) to referenceDate
            LAST_30_DAYS -> referenceDate.minusDays(29) to referenceDate
            NONE -> referenceDate to referenceDate
        }
}

@Serializable
data class ExportConfig(
    val enabledTypes: Set<HealthDataType>,
    val frequency: ExportFrequency,
    val autoSyncDrive: Boolean,
    val webhookUrl: String = "",
    val webhookAuthToken: String = "",
    val autoSendWebhook: Boolean = false,
    val autoSendWebhookEvery2Hours: Boolean = false,
    val outputDirectory: String = "HealthConnectExport",
    val selectedSourcePackage: String? = null,
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val scheduleHour: Int? = null,
)

@Serializable
enum class HealthDataType(
    val displayName: String,
) {
    STEPS("Steps"),
    HEART_RATE("Heart Rate"),
    SLEEP("Sleep"),
    CALORIES("Calories"),
    DISTANCE("Distance"),
    FLOORS_CLIMBED("Floors Climbed"),
    ACTIVE_CALORIES("Active Calories"),
    WEIGHT("Weight"),
    BODY_FAT("Body Fat"),
    BLOOD_PRESSURE("Blood Pressure"),
    BLOOD_GLUCOSE("Blood Glucose"),
    OXYGEN_SATURATION("Oxygen Saturation"),
    BODY_TEMPERATURE("Body Temperature"),
    RESPIRATORY_RATE("Respiratory Rate"),
    HYDRATION("Hydration"),
    RESTING_HEART_RATE("Resting Heart Rate"),
    EXERCISE("Exercise"),
    NUTRITION("Nutrition"),
    SPEED("Speed"),
    MENSTRUATION("Menstruation"),
}

// ===== Health Connect Int constant → Human-readable string mappers =====

fun bodyPositionToString(value: Int?): String? {
    if (value == null) return null
    return when (value) {
        0 -> "Unknown"
        1 -> "Standing"
        2 -> "Sitting"
        3 -> "Lying down"
        4 -> "Reclining"
        else -> "Other ($value)"
    }
}

fun specimenSourceToString(value: Int?): String? {
    if (value == null) return null
    return when (value) {
        0 -> "Unknown"
        1 -> "Interstitial fluid"
        2 -> "Capillary blood"
        3 -> "Plasma"
        4 -> "Serum"
        5 -> "Whole blood"
        else -> "Other ($value)"
    }
}

fun mealTypeToString(value: Int?): String? {
    if (value == null) return null
    return when (value) {
        0 -> "Unknown"
        1 -> "Fasting"
        2 -> "Before meal"
        3 -> "After meal"
        4 -> "General"
        else -> "Other ($value)"
    }
}

fun sleepStageToString(value: Int?): String? {
    if (value == null) return null
    return when (value) {
        0 -> "Unknown"
        1 -> "Awake"
        2 -> "Deep sleep"
        3 -> "Light sleep"
        4 -> "REM sleep"
        5 -> "Out of bed"
        else -> "Other ($value)"
    }
}

fun measurementLocationToString(value: Int?): String? {
    if (value == null) return null
    return when (value) {
        0 -> "Unknown"
        1 -> "Axillary"
        2 -> "Body"
        3 -> "Ear"
        4 -> "Finger"
        5 -> "Forehead"
        6 -> "Mouth"
        7 -> "Rectal"
        8 -> "Toe"
        9 -> "Tympanic"
        10 -> "Wrist"
        11 -> "Temporal artery"
        else -> "Other ($value)"
    }
}

fun menstruationFlowToString(value: Int?): String? {
    if (value == null) return null
    return when (value) {
        0 -> "Unknown"
        1 -> "Light"
        2 -> "Medium"
        3 -> "Heavy"
        else -> "Other ($value)"
    }
}

fun nutritionMealTypeToString(value: Int?): String? {
    if (value == null) return null
    return when (value) {
        0 -> "Unknown"
        1 -> "Breakfast"
        2 -> "Lunch"
        3 -> "Dinner"
        4 -> "Snack"
        else -> "Other ($value)"
    }
}

fun exerciseTypeToString(value: Int?): String? {
    if (value == null) return null
    return when (value) {
        0 -> "Other workout"
        1 -> "Back extension"
        2 -> "Badminton"
        3 -> "Baseball"
        4 -> "Basketball"
        5 -> "Cycling"
        6 -> "Stationary cycling"
        7 -> "Boot camp"
        8 -> "Boxing"
        9 -> "Burpee"
        10 -> "Calisthenics"
        11 -> "Cricket"
        12 -> "CrossFit"
        13 -> "Crunch"
        14 -> "Dancing"
        15 -> "Deadlift"
        16 -> "Fencing"
        17 -> "American football"
        18 -> "Australian football"
        19 -> "Soccer"
        20 -> "Frisbee"
        21 -> "General workout"
        22 -> "Golf"
        23 -> "Breathing exercises"
        24 -> "Gymnastics"
        25 -> "Handball"
        26 -> "HIIT"
        27 -> "Hiking"
        28 -> "Hockey"
        29 -> "Figure skating"
        30 -> "Jumping jack"
        31 -> "Jump rope"
        32 -> "Kayaking"
        33 -> "Kettlebell"
        34 -> "Kickboxing"
        35 -> "Kitesurfing"
        36 -> "Martial arts"
        37 -> "Meditation"
        38 -> "MMA"
        39 -> "P90X"
        40 -> "Pilates"
        41 -> "Plank"
        42 -> "Racquetball"
        43 -> "Rock climbing"
        44 -> "Roller skating"
        45 -> "Rowing"
        46 -> "Rowing machine"
        47 -> "Rugby"
        48 -> "Running"
        49 -> "Treadmill"
        50 -> "Sailing"
        51 -> "Scuba diving"
        52 -> "Ice skating"
        53 -> "Skiing"
        54 -> "Cross-country skiing"
        55 -> "Downhill skiing"
        56 -> "Kite skiing"
        57 -> "Roller skiing"
        58 -> "Sledding"
        59 -> "Sleep"
        60 -> "Snowboarding"
        61 -> "Snowshoeing"
        62 -> "Squash"
        63 -> "Stair climbing"
        64 -> "Stair stepper"
        65 -> "Strength training"
        66 -> "Stretching"
        67 -> "Surfing"
        68 -> "Swimming (open water)"
        69 -> "Swimming (pool)"
        70 -> "Table tennis"
        71 -> "Tennis"
        72 -> "Upper body"
        73 -> "Volleyball"
        74 -> "Walking"
        75 -> "Water polo"
        76 -> "Weightlifting"
        77 -> "Wheelchair"
        78 -> "Yoga"
        else -> "Other ($value)"
    }
}

/**
 * Map of known health data source packages to human-readable display names.
 * Used for UI selection and filtering.
 */
val KNOWN_SOURCE_PACKAGES: Map<String, String> =
    mapOf(
        "com.mi.health" to "Xiaomi Mi Fitness",
        "com.xiaomi.hm.health" to "Xiaomi Wear",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.samsung.samsunghealth" to "Samsung Health",
        "com.samsung.android.wearable.health" to "Samsung Health (Wear)",
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.mobvoi.companion.at" to "Mobvoi / TicWatch",
        "com.huawei.health" to "Huawei Health",
        "com.hmdm.wearable.health" to "Nokia Health",
        "com.sec.android.app.shealth" to "Samsung S Health",
        "com.htc.fitness" to "HTC Fitness",
        "com.sonymobile.advancedwidget.health" to "Sony Health",
        "com.google.android.wearable.app" to "Google Wear OS",
    )

/**
 * Returns a human-readable name for a source package, or the raw package name if unknown.
 */
fun sourceDisplayName(packageName: String): String = KNOWN_SOURCE_PACKAGES[packageName] ?: packageName

// ===== Export Summary =====

/**
 * Dashboard summary computed after export.
 */
data class ExportSummary(
    val totalSteps: Long = 0,
    val avgHeartRate: Double = 0.0,
    val totalCalories: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    val avgSleepMinutes: Long = 0,
    val totalActiveCalories: Double = 0.0,
    val daysCount: Int = 0,
    val startDate: String = "",
    val endDate: String = "",
)

// ===== Helpers =====

fun Instant.toDateString(): String = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(this)

fun Instant.toDateTimeString(): String = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault()).format(this)
