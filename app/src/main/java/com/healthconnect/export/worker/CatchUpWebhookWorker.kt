package com.healthconnect.export.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.healthconnect.export.repository.HealthConnectRepository
import com.healthconnect.export.repository.WebhookRepository
import com.healthconnect.export.repository.WebhookResult
import com.healthconnect.export.util.DeliveryLog
import com.healthconnect.export.util.ExportForegroundNotifier
import com.healthconnect.export.util.ExportSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * One-shot gap filler for the webhook delivery.
 *
 * The everyday jobs only ever send "today" (every-2-hours worker) or "yesterday"
 * (daily worker). If they are starved or dropped for a while — Doze, OEM
 * background restrictions, a force-stop — those days are lost forever, which is
 * exactly what happened when the export stalled. This worker resumes from the
 * newest day that was actually delivered and sends everything after it in a
 * single payload (the receiver already accepts a list of days and upserts them).
 */
class CatchUpWebhookWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_TRIGGER = "catch_up_trigger"
        const val DEFAULT_TRIGGER = "catch-up"

        /** Upper bound for one catch-up payload, so a long stall cannot produce a huge request. */
        const val MAX_CATCH_UP_DAYS = 30L

        /** Window used when nothing has ever been delivered. */
        const val FIRST_RUN_DAYS = 30L
    }

    internal var healthRepo = HealthConnectRepository(applicationContext)
    internal var webhookRepo = WebhookRepository(applicationContext)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val trigger = inputData.getString(KEY_TRIGGER) ?: DEFAULT_TRIGGER
            val config = ExportSettings.loadConfig(applicationContext)

            if (!ExportSettings.hasWebhook(config)) {
                DeliveryLog.recordIdle(applicationContext, trigger, "no webhook url")
                return@withContext Result.success()
            }

            val today = LocalDate.now()
            val resumeFrom =
                DeliveryLog.lastDataDate(applicationContext)?.plusDays(1)
                    ?: today.minusDays(FIRST_RUN_DAYS - 1)
            val start = maxOf(resumeFrom, today.minusDays(MAX_CATCH_UP_DAYS - 1))

            if (start.isAfter(today)) {
                DeliveryLog.recordIdle(applicationContext, trigger, "up to date")
                return@withContext Result.success()
            }

            try {
                // Health Connect (Android 14+) rejects background reads unless the
                // app is in the foreground or running as a foreground service.
                ExportForegroundNotifier.promoteToForeground(this@CatchUpWebhookWorker)

                val records =
                    healthRepo.readPeriodInBatch(
                        startDate = start,
                        endDate = today,
                        types = config.enabledTypes,
                        selectedSourcePackage = config.selectedSourcePackage,
                    )

                if (records.isEmpty()) {
                    // Nothing to send: keep the resume point so a later run retries this window.
                    DeliveryLog.recordIdle(applicationContext, trigger, "no health data $start..$today")
                    return@withContext Result.success()
                }

                when (val result = webhookRepo.sendRecords(config.webhookUrl, records, config.webhookAuthToken)) {
                    is WebhookResult.Success -> {
                        DeliveryLog.recordSuccess(applicationContext, trigger, records.map { it.date }, statusCode = result.statusCode)
                        Result.success()
                    }
                    is WebhookResult.Error -> {
                        DeliveryLog.recordFailure(
                            applicationContext,
                            trigger,
                            "HTTP ${result.statusCode}: ${result.message}",
                        )
                        Result.retry()
                    }
                }
            } catch (e: SecurityException) {
                // Health permissions revoked — retry keeps the job alive and it
                // heals itself as soon as the user grants them again.
                DeliveryLog.recordFailure(applicationContext, trigger, "permissions: ${e.message}")
                Result.retry()
            } catch (e: IllegalStateException) {
                DeliveryLog.recordFailure(applicationContext, trigger, "health connect: ${e.message}")
                Result.retry()
            } catch (e: Exception) {
                DeliveryLog.recordFailure(applicationContext, trigger, e.message ?: e.toString())
                Result.retry()
            }
        }
}
