# Compose and Kotlin metadata
-keep class kotlin.Metadata { *; }
-keep class androidx.compose.runtime.snapshots.** { *; }
-dontwarn androidx.compose.runtime.snapshots.**

# Room / WorkManager / Serialization
-keep class androidx.room.RoomDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.work.impl.background.systemjob.SystemJobService { *; }
-keep class androidx.work.impl.background.systemalarm.SystemAlarmService { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# PDF / OCR stack
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# App surface and enterprise config
-keep class com.aymanelbanhawy.enterprisepdf.app.release.** { *; }
-keep class com.aymanelbanhawy.editor.core.runtime.** { *; }
-keep class com.aymanelbanhawy.editor.core.security.** { *; }
-keep class com.aymanelbanhawy.editor.core.forms.** { *; }
-keep class com.aymanelbanhawy.editor.core.search.** { *; }
-keep class com.aymanelbanhawy.editor.core.ocr.** { *; }
-keep class com.aymanelbanhawy.aiassistant.core.** { *; }

# Retain instrumentation benchmark entry points when assembling non-debug variants for CI compile checks
-keep class androidx.benchmark.junit4.** { *; }
