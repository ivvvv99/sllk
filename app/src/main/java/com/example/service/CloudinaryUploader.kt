package com.example.service

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object CloudinaryUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads an image from Uri to Cloudinary using their unsigned REST API upload endpoint.
     * @param context Android context
     * @param uri The Uri of the image to upload
     * @param cloudName The Cloudinary cloud name
     * @param uploadPreset The Cloudinary unsigned upload preset
     */
    suspend fun uploadImage(
        context: Context,
        uri: Uri,
        cloudName: String,
        uploadPreset: String
    ): String = withContext(Dispatchers.IO) {
        if (cloudName.isEmpty() || uploadPreset.isEmpty()) {
            throw Exception("Cloudinary credentials not configured. Please configure in App settings.")
        }

        val url = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"
        
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Failed to open image stream")
            
        val byteArray = inputStream.use { stream ->
            val bos = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var len: Int
            while (stream.read(buffer).also { len = it } != -1) {
                bos.write(buffer, 0, len)
            }
            bos.toByteArray()
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart(
                "file",
                "upload.jpg",
                byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull(), 0, byteArray.size)
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: throw Exception("Empty response from Cloudinary")
            if (!response.isSuccessful) {
                val errMsg = try {
                    JSONObject(bodyStr).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "Cloudinary Error: Code ${response.code}"
                }
                throw Exception(errMsg)
            }

            val json = JSONObject(bodyStr)
            json.getString("secure_url")
        }
    }
}
