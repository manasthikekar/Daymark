package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CalendarEvent
import com.example.model.EventCategory
import com.example.model.ReminderOption
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class CalendarRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeEvents(userId: String): Flow<List<CalendarEvent>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            throw IllegalStateException("Authenticated session does not match requested user.")
        }
        val path = "users/$userId/events"
        emitAll(
            db.collection("users").document(userId).collection("events")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CalendarEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<CalendarEvent>(
                            { it.dateEpochDay },
                            { !it.allDay },
                            { it.startMinute },
                            { it.title.lowercase() }
                        )
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createEvent(
        title: String,
        notes: String?,
        location: String?,
        dateEpochDay: Long,
        startMinute: Long,
        endMinute: Long,
        allDay: Boolean,
        category: EventCategory,
        reminderOption: ReminderOption,
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/events"
        return try {
            val uid = requireUserId()
            val trimmedTitle = title.trim()
            require(trimmedTitle.length in 1..100) { "Title must be 1 to 100 characters." }

            val trimmedNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmedNotes != null) {
                require(trimmedNotes.length <= 500) { "Notes must be 500 characters or fewer." }
            }

            val trimmedLocation = location?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmedLocation != null) {
                require(trimmedLocation.length <= 120) { "Location must be 120 characters or fewer." }
            }

            require(dateEpochDay in -365243L..365243L) { "Date is out of valid range." }

            val normalizedStart = if (allDay) 0L else startMinute.coerceIn(0L, 1439L)
            val normalizedEnd = if (allDay) 1440L else maxOf(maxOf(1L, normalizedStart), endMinute).coerceAtMost(1440L)

            val docRef = db.collection("users").document(uid).collection("events").document()
            docPath = docRef.path

            val rawPayload: Map<String, Any?> = mapOf(
                "userId" to uid,
                "title" to trimmedTitle,
                "notes" to trimmedNotes,
                "location" to trimmedLocation,
                "dateEpochDay" to dateEpochDay,
                "startMinute" to normalizedStart,
                "endMinute" to normalizedEnd,
                "allDay" to allDay,
                "category" to category.name,
                "reminderMinutes" to reminderOption.minutesBefore,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            @Suppress("UNCHECKED_CAST")
            val cleanPayload = rawPayload.filterValues { it != null } as Map<String, Any>
            docRef.set(cleanPayload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateEvent(
        eventId: String,
        title: String,
        notes: String?,
        location: String?,
        dateEpochDay: Long,
        startMinute: Long,
        endMinute: Long,
        allDay: Boolean,
        category: EventCategory,
        reminderOption: ReminderOption,
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/events/$eventId"
        return try {
            val uid = requireUserId()
            require(eventId.isNotBlank()) { "Event ID must not be blank for update." }

            val trimmedTitle = title.trim()
            require(trimmedTitle.length in 1..100) { "Title must be 1 to 100 characters." }

            val trimmedNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmedNotes != null) {
                require(trimmedNotes.length <= 500) { "Notes must be 500 characters or fewer." }
            }

            val trimmedLocation = location?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmedLocation != null) {
                require(trimmedLocation.length <= 120) { "Location must be 120 characters or fewer." }
            }

            require(dateEpochDay in -365243L..365243L) { "Date is out of valid range." }

            val normalizedStart = if (allDay) 0L else startMinute.coerceIn(0L, 1439L)
            val normalizedEnd = if (allDay) 1440L else maxOf(maxOf(1L, normalizedStart), endMinute).coerceAtMost(1440L)

            val docRef = db.collection("users").document(uid).collection("events").document(eventId)
            docPath = docRef.path

            val updatePayload = mapOf<String, Any>(
                "title" to trimmedTitle,
                "notes" to (trimmedNotes ?: FieldValue.delete()),
                "location" to (trimmedLocation ?: FieldValue.delete()),
                "dateEpochDay" to dateEpochDay,
                "startMinute" to normalizedStart,
                "endMinute" to normalizedEnd,
                "allDay" to allDay,
                "category" to category.name,
                "reminderMinutes" to reminderOption.minutesBefore,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.update(updatePayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/events/$eventId"
        return try {
            val uid = requireUserId()
            require(eventId.isNotBlank()) { "Event ID must not be blank for deletion." }
            val docRef = db.collection("users").document(uid).collection("events").document(eventId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
