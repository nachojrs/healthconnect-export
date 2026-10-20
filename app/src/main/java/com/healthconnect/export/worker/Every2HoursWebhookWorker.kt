package com.healthconnect.export.worker

import android.content.Context
import androidx.work.*
import com.healthconnect.export.data.ExportConfig
import com.healthconnect.export.repository.HealthConnectRepository
import com.healthconnect.export.repository.WebhookRepository
import com.healthconnect.export.repository.WebhookResult
import com.healthconnect.export.util.DeliveryLog
import com.healthconnect.export.util.ExportForegroundNotifier
import com.healthconnect.export.util.ExportSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Background worker that reads today's health data every 2 hours
 * and sends it to the configured webhook (without local save or Drive sync).
 */
class Every2HoursWebhookWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        const val WORK_NAME = "every_2_hours_webhook"
        const val KEY_CONFIG = "webhook_config"
        private const val TRIGGER = "every-2h"
        private val json = Json { ignoreUnknownKeys = true }

        fun schedule(
            context: Context,
            config: ExportConfig,
        ) {
            if (config.webhookUrl.isBlank()) {
                cancel(context)
                return
            }

            val inputData =
                workDataOf(
                    KEY_CONFIG to json.encodeToString(config),
                )

            val request =
                PeriodicWorkRequestBuilder<Every2HoursWebhookWorker>(
                    2,
                    TimeUnit.HOURS,
                ).setInputData(inputData)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    private val healthRepo = HealthConnectRepository(applicationContext)
    private val webhookRepo = WebhookRepository(applicationContext)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val config = resolveConfig()
            try {
                if (!ExportSettings.hasWebhook(config)) {
                    return@withContext Result.success()
                }

                // Health Connect (Android 14+) rejects background reads unless the
                // app is in the foreground or running as a foreground service.
                ExportForegroundNotifier.promoteToForeground(this@Every2HoursWebhookWorker)

                // Read yesterday and today: yesterday's last hours (after the previous run) are then
                // delivered too, and a day is never left incomplete
                val today = LocalDate.now()
                val records =
                    healthRepo.readPeriodInBatch(
                        startDate = today.minusDays(1),
                        endDate = today,
                        types = config.enabledTypes,
                        selectedSourcePackage = config.selectedSourcePackage,
                    )

                // Send to webhook (no local save, no Drive sync)
                when (val result = webhookRepo.sendRecords(config.webhookUrl, records, config.webhookAuthToken)) {
                    is WebhookResult.Success -> {
                        DeliveryLog.recordSuccess(applicationContext, TRIGGER, records.map { it.date }, statusCode = result.statusCode)
                        Result.success()
                    }
                    is WebhookResult.Error -> {
                        DeliveryLog.recordFailure(
                            applicationContext,
                            TRIGGER,
                            "HTTP ${result.statusCode}: ${result.message}",
                        )
                        Result.retry()
                    }
                }
            } catch (e: SecurityException) {
                // Retry, never fail: a failed periodic run is terminated by
                // WorkManager for good, which is how the delivery went silent.
                DeliveryLog.recordFailure(applicationContext, TRIGGER, "permissions: ${e.message}")
                Result.retry()
            } catch (e: IllegalStateException) {
                DeliveryLog.recordFailure(applicationContext, TRIGGER, "health connect: ${e.message}")
                Result.retry()
            } catch (e: Exception) {
                DeliveryLog.recordFailure(applicationContext, TRIGGER, e.message ?: e.toString())
                Result.retry()
            }
        }

    /**
     * Input data carries the configuration snapshot taken when the job was
     * enqueued; the webhook settings are refreshed from the persisted values so
     * a URL/token changed later still applies to that job.
     */
    private fun resolveConfig(): ExportConfig {
        val fromInput =
            inputData.getString(KEY_CONFIG)?.let { configJson ->
                runCatching { Json.decodeFromString<ExportConfig>(configJson) }.getOrNull()
            } ?: return ExportSettings.loadConfig(applicationContext)

        val persisted = ExportSettings.loadConfig(applicationContext)
        return if (persisted.webhookUrl.isNotBlank()) {
            fromInput.copy(
                webhookUrl = persisted.webhookUrl,
                webhookAuthToken = persisted.webhookAuthToken,
            )
        } else {
            fromInput
        }
    }
}
