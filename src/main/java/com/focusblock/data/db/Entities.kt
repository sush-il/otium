package com.focusblock.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.focusblock.domain.model.RuleType

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val type: RuleType, // Room stores enums as their name (TEXT)
    val dailyLimitMinutes: Int?,
    val createdAt: Long,
    // Added in schema v2
    val hourlyLimitMinutes: Int? = null,
    val launchLimit: Int? = null,
    val blockUntil: Long? = null, // epoch ms
    val pausedUntil: Long? = null, // epoch ms, added in schema v3
    val wholePhone: Boolean = false, // added in schema v4
)

@Entity(
    tableName = "profile_apps",
    primaryKeys = ["profileId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProfileAppEntity(
    val profileId: Long,
    val packageName: String,
)

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
)

/** A profile row together with its apps and schedules, loaded in one query. */
data class ProfileWithDetails(
    @Embedded val profile: ProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profileId")
    val apps: List<ProfileAppEntity>,
    @Relation(parentColumn = "id", entityColumn = "profileId")
    val schedules: List<ScheduleEntity>,
)
