package com.aymanelbanhawy.editor.core.forms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.aymanelbanhawy.editor.core.data.FormProfileDao
import com.aymanelbanhawy.editor.core.data.FormProfileEntity
import com.aymanelbanhawy.editor.core.data.SavedSignatureDao
import com.aymanelbanhawy.editor.core.data.SavedSignatureEntity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface FormSupportRepository {
    suspend fun loadProfiles(): List<FormProfileModel>
    suspend fun saveProfile(name: String, document: FormDocumentModel): FormProfileModel
    suspend fun importProfile(file: File): FormProfileModel
    suspend fun exportProfile(profile: FormProfileModel, destination: File): File
    suspend fun loadSignatures(): List<SavedSignatureModel>
    suspend fun saveSignature(name: String, kind: SignatureKind, capture: SignatureCapture): SavedSignatureModel
}

class DefaultFormSupportRepository(
    private val context: Context,
    private val profileDao: FormProfileDao,
    private val savedSignatureDao: SavedSignatureDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true; classDiscriminator = "_type" },
) : FormSupportRepository {

    override suspend fun loadProfiles(): List<FormProfileModel> = withContext(ioDispatcher) {
        profileDao.all().map { entity -> json.decodeFromString(FormProfileModel.serializer(), entity.payloadJson) }
    }

    override suspend fun saveProfile(name: String, document: FormDocumentModel): FormProfileModel = withContext(ioDispatcher) {
        val model = FormProfileModel(
            id = UUID.randomUUID().toString(),
            name = name,
            values = document.fields.associate { it.name to it.value },
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        profileDao.upsert(FormProfileEntity(model.id, model.name, json.encodeToString(FormProfileModel.serializer(), model), model.createdAtEpochMillis))
        model
    }

    override suspend fun importProfile(file: File): FormProfileModel = withContext(ioDispatcher) {
        val model = json.decodeFromString(FormProfileModel.serializer(), file.readText())
        profileDao.upsert(FormProfileEntity(model.id, model.name, json.encodeToString(FormProfileModel.serializer(), model), model.createdAtEpochMillis))
        model
    }

    override suspend fun exportProfile(profile: FormProfileModel, destination: File): File = withContext(ioDispatcher) {
        destination.parentFile?.mkdirs()
        destination.writeText(json.encodeToString(FormProfileModel.serializer(), profile))
        destination
    }

    override suspend fun loadSignatures(): List<SavedSignatureModel> = withContext(ioDispatcher) {
        savedSignatureDao.all().map { entity ->
            SavedSignatureModel(
                id = entity.id,
                name = entity.name,
                kind = SignatureKind.valueOf(entity.kind),
                imagePath = entity.imagePath,
                createdAtEpochMillis = entity.createdAtEpochMillis,
                sourceType = SignatureSourceType.valueOf(entity.sourceType),
                signingIdentityId = entity.signingIdentityId,
                signerDisplayName = entity.signerDisplayName,
                certificateSubject = entity.certificateSubject,
                certificateSha256 = entity.certificateSha256,
            )
        }
    }

    override suspend fun saveSignature(name: String, kind: SignatureKind, capture: SignatureCapture): SavedSignatureModel = withContext(ioDispatcher) {
        val id = UUID.randomUUID().toString()
        val imageFile = File(context.filesDir, "signatures/$id.png").apply { parentFile?.mkdirs() }
        renderCapture(capture).also { bitmap ->
            FileOutputStream(imageFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val model = SavedSignatureModel(
            id = id,
            name = name,
            kind = kind,
            imagePath = imageFile.absolutePath,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        savedSignatureDao.upsert(
            SavedSignatureEntity(
                id = model.id,
                name = model.name,
                kind = model.kind.name,
                imagePath = model.imagePath,
                createdAtEpochMillis = model.createdAtEpochMillis,
                sourceType = model.sourceType.name,
                signingIdentityId = model.signingIdentityId,
                signerDisplayName = model.signerDisplayName,
                certificateSubject = model.certificateSubject,
                certificateSha256 = model.certificateSha256,
            ),
        )
        model
    }

    private fun renderCapture(capture: SignatureCapture): Bitmap {
        val width = capture.width.toInt().coerceAtLeast(1)
        val height = capture.height.toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        capture.strokes.forEach { stroke ->
            stroke.points.zipWithNext().forEach { (start, end) ->
                canvas.drawLine(start.x * width, start.y * height, end.x * width, end.y * height, paint)
            }
        }
        return bitmap
    }
}
