package com.example.data.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class GalleryStorageAccessMode {
    SCOPED_MEDIA_STORE,
    LEGACY_PUBLIC_GALLERY_GRANTED,
    APP_STORAGE_ONLY_PERMISSION_DENIED
}

object StoragePermissionHelper {

    fun requiresLegacyGalleryWritePermission(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return sdkInt < Build.VERSION_CODES.Q
    }

    fun hasLegacyGalleryWritePermission(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (sdkInt >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requiredGalleryWritePermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        return if (requiresLegacyGalleryWritePermission(sdkInt)) {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            emptyArray()
        }
    }

    fun hasNotificationPermission(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getAccessMode(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): GalleryStorageAccessMode {
        return when {
            sdkInt >= Build.VERSION_CODES.Q -> GalleryStorageAccessMode.SCOPED_MEDIA_STORE
            hasLegacyGalleryWritePermission(context, sdkInt) -> GalleryStorageAccessMode.LEGACY_PUBLIC_GALLERY_GRANTED
            else -> GalleryStorageAccessMode.APP_STORAGE_ONLY_PERMISSION_DENIED
        }
    }

    fun accessModeTitle(mode: GalleryStorageAccessMode): String {
        return when (mode) {
            GalleryStorageAccessMode.SCOPED_MEDIA_STORE ->
                "MediaStore Scoped Storage Active"
            GalleryStorageAccessMode.LEGACY_PUBLIC_GALLERY_GRANTED ->
                "Public Gallery & App Storage Active"
            GalleryStorageAccessMode.APP_STORAGE_ONLY_PERMISSION_DENIED ->
                "App-Private Storage Fallback Active"
        }
    }

    fun accessModeDescription(mode: GalleryStorageAccessMode): String {
        return when (mode) {
            GalleryStorageAccessMode.SCOPED_MEDIA_STORE ->
                "Captures save to ScrollShot and Pictures/ScrollShot automatically via MediaStore with zero storage permissions required."
            GalleryStorageAccessMode.LEGACY_PUBLIC_GALLERY_GRANTED ->
                "Captures save in ScrollShot and appear in Pictures/ScrollShot in your device Gallery."
            GalleryStorageAccessMode.APP_STORAGE_ONLY_PERMISSION_DENIED ->
                "Storage permission is disabled on Android 8–9. Screenshots still save safely inside ScrollShot, or you can allow gallery access."
        }
    }
}
