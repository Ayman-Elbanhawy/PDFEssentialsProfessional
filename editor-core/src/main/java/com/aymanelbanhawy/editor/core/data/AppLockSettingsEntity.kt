package com.aymanelbanhawy.editor.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_lock_settings")
data class AppLockSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singletonId: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    @ColumnInfo(name = "pin_hash") val pinHash: String,
    @ColumnInfo(name = "biometrics_enabled") val biometricsEnabled: Boolean,
    @ColumnInfo(name = "lock_timeout_seconds") val lockTimeoutSeconds: Int,
)
