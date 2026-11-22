package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.capture.CaptureSessionCoordinator
import com.example.capture.ScreenCaptureService
import com.example.data.model.CaptureMode
import com.example.data.model.StitchDirection
import com.example.data.model.ThemeMode
import com.example.data.storage.StoragePermissionHelper
import com.example.ui.components.LocalizedContextProvider
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OcrScreen
import com.example.ui.screens.PdfExportScreen
import com.example.ui.screens.ScreenshotDetailScreen
import com.example.ui.screens.SeamAlignmentScreen
import com.example.ui.screens.SettingsScreen
import androidx.compose.material3.SnackbarResult
import com.example.ui.theme.ScrollShotTheme
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.GalleryViewModel
import com.example.ui.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val GALLERY = "gallery"
    const val EDITOR = "editor"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{id}"
    const val PDF = "pdf"
    const val OCR = "ocr"
    const val SEAM_ALIGNMENT = "seam_alignment"

    fun detail(id: Long) = "detail/$id"
    fun editor(id: Long? = null) = if (id != null) "editor?id=$id" else "editor"
    fun pdf(ids: List<Long> = emptyList()) = if (ids.isNotEmpty()) "pdf?ids=${ids.joinToString(",")}" else "pdf"
    fun ocr(id: Long? = null) = if (id != null) "ocr?id=$id" else "ocr"
}

private data class PendingCaptureRequest(
    val isSingle: Boolean,
    val mode: CaptureMode,
    val direction: StitchDirection
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScrollShotRoot()
        }
    }
}

@Composable
fun ScrollShotRoot(
    mainViewModel: MainViewModel = viewModel(),
    galleryViewModel: GalleryViewModel = viewModel(),
    editorViewModel: EditorViewModel = viewModel()
) {
    val prefs by mainViewModel.preferences.collectAsStateWithLifecycle()
    val darkTheme = when (prefs.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LocalizedContextProvider(language = prefs.language) {
        ScrollShotTheme(darkTheme = darkTheme) {
            ScrollShotScaffold(
                mainViewModel = mainViewModel,
                galleryViewModel = galleryViewModel,
                editorViewModel = editorViewModel
            )
        }
    }
}

@Composable
private fun ScrollShotScaffold(
    mainViewModel: MainViewModel,
    galleryViewModel: GalleryViewModel,
    editorViewModel: EditorViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs by mainViewModel.preferences.collectAsStateWithLifecycle()
    val allScreenshots by mainViewModel.allScreenshots.collectAsStateWithLifecycle()
    val filteredScreenshots by galleryViewModel.filteredScreenshots.collectAsStateWithLifecycle()
    val pagedScreenshots by galleryViewModel.pagedScreenshots.collectAsStateWithLifecycle()
    val hasMoreGalleryPages by galleryViewModel.hasMorePages.collectAsStateWithLifecycle()
    val albums by galleryViewModel.allAlbums.collectAsStateWithLifecycle()
    val recoverableSession by mainViewModel.recoverableSession.collectAsStateWithLifecycle()
    val captureState by mainViewModel.captureState.collectAsStateWithLifecycle()
    val galleryUiState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val availableBytes by mainViewModel.availableStorageBytes.collectAsStateWithLifecycle()
    val usedBytes by mainViewModel.usedStorageBytes.collectAsStateWithLifecycle()
    val storageAccessMode by mainViewModel.storageAccessMode.collectAsStateWithLifecycle()
    val pdfState by mainViewModel.pdfUiState.collectAsStateWithLifecycle()
    val ocrState by mainViewModel.ocrUiState.collectAsStateWithLifecycle()
    val seamState by mainViewModel.seamEditorState.collectAsStateWithLifecycle()
    val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()
    val toastMsg by mainViewModel.toastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            snackbarHostState.showSnackbar(it)
            mainViewModel.clearToast()
        }
    }

    LaunchedEffect(galleryUiState.statusMessage, galleryUiState.lastExportedFile) {
        val msg = galleryUiState.statusMessage
        val exported = galleryUiState.lastExportedFile
        if (msg != null) {
            if (exported != null) {
                val res = snackbarHostState.showSnackbar(message = msg, actionLabel = "Share")
                if (res == SnackbarResult.ActionPerformed) {
                    galleryViewModel.createShareFileIntent(exported.filePath, exported.format.mimeType)?.let {
                        context.startActivity(Intent.createChooser(it, "Share Exported Image"))
                    }
                }
                galleryViewModel.clearLastExportedFile()
            } else {
                snackbarHostState.showSnackbar(msg)
            }
            galleryViewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(editorState.statusMessage) {
        editorState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            editorViewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(allScreenshots.size) {
        mainViewModel.refreshStorageStats()
    }

    var pendingRequest by remember { mutableStateOf<PendingCaptureRequest?>(null) }
    var promptedLegacyStorageForCapture by remember { mutableStateOf(false) }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val req = pendingRequest
        pendingRequest = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null && req != null) {
            CaptureSessionCoordinator.pendingProjectionResultCode = result.resultCode
            CaptureSessionCoordinator.pendingProjectionData = result.data
            val svcIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_IS_SINGLE, req.isSingle)
                putExtra(ScreenCaptureService.EXTRA_MODE, req.mode.name)
                putExtra(ScreenCaptureService.EXTRA_DIRECTION, req.direction.name)
            }
            ContextCompat.startForegroundService(context, svcIntent)
        } else {
            mainViewModel.showToast(context.getString(R.string.error_permission_denied))
        }
    }

    fun launchProjectionConsent(req: PendingCaptureRequest) {
        pendingRequest = req
        val mpm = context.getSystemService(MediaProjectionManager::class.java)
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with MediaProjection consent regardless so capture still works even if notification was denied
        pendingRequest?.let { launchProjectionConsent(it) }
    }

    fun continueToNotificationAndProjection(req: PendingCaptureRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchProjectionConsent(req)
        }
    }

    val legacyCaptureStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        mainViewModel.refreshStorageStats()
        galleryViewModel.refreshStorageAccessMode()
        if (!granted) {
            mainViewModel.showToast(context.getString(R.string.storage_legacy_permission_denied_notice))
        }
        pendingRequest?.let { continueToNotificationAndProjection(it) }
    }

    val legacyStandaloneStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        mainViewModel.refreshStorageStats()
        galleryViewModel.refreshStorageAccessMode()
        if (granted) {
            mainViewModel.showToast("Public device gallery saving enabled (Pictures/ScrollShot).")
            galleryViewModel.refreshGallery(silent = true)
        } else {
            mainViewModel.showToast(context.getString(R.string.storage_legacy_permission_denied_notice))
        }
    }

    fun requestLegacyGalleryPermission() {
        if (StoragePermissionHelper.requiresLegacyGalleryWritePermission()) {
            legacyStandaloneStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun requestCapture(isSingle: Boolean, mode: CaptureMode, direction: StitchDirection) {
        val req = PendingCaptureRequest(isSingle, mode, direction)
        pendingRequest = req
        if (!promptedLegacyStorageForCapture &&
            !StoragePermissionHelper.hasLegacyGalleryWritePermission(context)
        ) {
            promptedLegacyStorageForCapture = true
            legacyCaptureStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            continueToNotificationAndProjection(req)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME
    val showTopLevelNav = currentRoute == Routes.HOME ||
        currentRoute == Routes.GALLERY ||
        currentRoute.startsWith(Routes.EDITOR) ||
        currentRoute == Routes.SETTINGS

    val navItems = listOf(
        Triple(Routes.HOME, stringResource(R.string.nav_home), Icons.Rounded.Home),
        Triple(Routes.GALLERY, stringResource(R.string.nav_gallery), Icons.Rounded.Collections),
        Triple(Routes.EDITOR, stringResource(R.string.nav_editor), Icons.Rounded.Edit),
        Triple(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Rounded.Settings)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar {
                        navItems.forEach { (route, label, icon) ->
                            val selected = currentRoute == route || (route == Routes.EDITOR && currentRoute.startsWith(Routes.EDITOR))
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail {
                        navItems.forEach { (route, label, icon) ->
                            val selected = currentRoute == route || (route == Routes.EDITOR && currentRoute.startsWith(Routes.EDITOR))
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            preferences = prefs,
                            screenshots = allScreenshots,
                            recoverableSession = recoverableSession,
                            captureState = captureState,
                            availableStorageBytes = availableBytes,
                            onStartLongScreenshot = { mode, dir -> requestCapture(false, mode, dir) },
                            onStartQuickScreenshot = { requestCapture(true, CaptureMode.MANUAL, StitchDirection.VERTICAL) },
                            onNavigateGallery = { navController.navigate(Routes.GALLERY) },
                            onNavigateEditor = { id -> navController.navigate(Routes.editor(id)) },
                            onNavigatePdf = { navController.navigate(Routes.pdf()) },
                            onNavigateOcr = { navController.navigate(Routes.ocr()) },
                            onOpenScreenshotDetail = { id -> navController.navigate(Routes.detail(id)) },
                            onRecoverSession = { session -> mainViewModel.recoverAndStitchSession(session) },
                            onDiscardSession = { session -> mainViewModel.discardSession(session) },
                            onCaptureFrameManual = { ScreenCaptureService.sendAction(context, ScreenCaptureService.ACTION_CAPTURE_FRAME) },
                            onPauseCapture = { ScreenCaptureService.sendAction(context, ScreenCaptureService.ACTION_PAUSE) },
                            onResumeCapture = { ScreenCaptureService.sendAction(context, ScreenCaptureService.ACTION_RESUME) },
                            onUndoCaptureFrame = { ScreenCaptureService.sendAction(context, ScreenCaptureService.ACTION_UNDO_FRAME) },
                            onFinishCapture = { ScreenCaptureService.sendAction(context, ScreenCaptureService.ACTION_FINISH) },
                            onCancelCapture = { ScreenCaptureService.sendAction(context, ScreenCaptureService.ACTION_CANCEL) },
                            onOpenSeamEditor = {
                                captureState.lastStitchPlan?.let {
                                    mainViewModel.loadStitchPlanForAlignment(it)
                                    navController.navigate(Routes.SEAM_ALIGNMENT)
                                }
                            },
                            onDismissCaptureStatus = { ScreenCaptureService.resetSessionState() },
                            onUpdateMode = { m -> mainViewModel.updatePreferences { it.copy(defaultCaptureMode = m) } },
                            onUpdateDirection = { d -> mainViewModel.updatePreferences { it.copy(defaultDirection = d) } }
                        )
                    }

                    composable(Routes.GALLERY) {
                        GalleryScreen(
                            screenshots = pagedScreenshots,
                            allFilteredScreenshots = filteredScreenshots,
                            albums = albums,
                            uiState = galleryUiState,
                            hasMorePages = hasMoreGalleryPages,
                            defaultJpegQuality = prefs.jpegQuality,
                            onRefresh = { galleryViewModel.refreshGallery(silent = false) },
                            onLoadMore = galleryViewModel::loadNextPage,
                            onSearchChange = galleryViewModel::setSearchQuery,
                            onSortChange = galleryViewModel::setGallerySort,
                            onFilterChange = galleryViewModel::setGalleryFilter,
                            onDateFilterChange = galleryViewModel::setDateFilter,
                            onClearAllFilters = galleryViewModel::clearAllFilters,
                            onEnterSelectionMode = galleryViewModel::enterSelectionMode,
                            onToggleSelect = galleryViewModel::toggleSelectScreenshot,
                            onSelectAll = galleryViewModel::selectAll,
                            onDeselectAll = galleryViewModel::deselectAll,
                            onToggleFavorite = galleryViewModel::toggleFavorite,
                            onRenameScreenshot = galleryViewModel::renameScreenshot,
                            onDuplicateScreenshot = galleryViewModel::duplicateScreenshot,
                            onCopyToClipboard = { shot ->
                                galleryViewModel.copyImageToClipboard(shot.filePath, shot.mediaStoreUri)
                            },
                            onDeleteScreenshot = galleryViewModel::deleteScreenshot,
                            onDeleteSelected = galleryViewModel::deleteSelectedScreenshots,
                            onShareSelected = { items ->
                                if (items.isNotEmpty()) {
                                    galleryViewModel.createShareImagesIntentForItems(items)?.let { intent ->
                                        context.startActivity(Intent.createChooser(intent, "Share Screenshots"))
                                    }
                                }
                            },
                            onMoveScreenshotsToAlbum = galleryViewModel::moveScreenshotsToAlbum,
                            onRemoveFromAlbum = galleryViewModel::removeFromAlbum,
                            onExportSelectedToPdf = { ids ->
                                galleryViewModel.deselectAll()
                                navController.navigate(Routes.pdf(ids))
                            },
                            onExportImage = galleryViewModel::exportScreenshot,
                            onExportImageToUri = galleryViewModel::saveScreenshotToExternalUri,
                            onRequestLegacyGalleryPermission = { requestLegacyGalleryPermission() },
                            onDismissLegacyStorageBanner = galleryViewModel::dismissLegacyStorageBanner,
                            onCreateAlbum = galleryViewModel::createAlbum,
                            onRenameAlbum = galleryViewModel::renameAlbum,
                            onDeleteAlbum = galleryViewModel::deleteAlbum,
                            onImportPhotoUri = { uri ->
                                galleryViewModel.importPhoto(uri) { newId ->
                                    navController.navigate(Routes.detail(newId))
                                }
                            },
                            onOpenDetail = { id -> navController.navigate(Routes.detail(id)) }
                        )
                    }

                    composable(
                        route = "${Routes.EDITOR}?id={id}",
                        arguments = listOf(navArgument("id") {
                            type = NavType.LongType
                            defaultValue = -1L
                        })
                    ) { backStackEntry ->
                        val idArg = backStackEntry.arguments?.getLong("id") ?: -1L
                        LaunchedEffect(idArg) {
                            if (idArg > 0L) editorViewModel.loadScreenshot(idArg)
                        }
                        EditorScreen(
                            uiState = editorState,
                            preferences = prefs,
                            availableScreenshots = allScreenshots,
                            onSelectScreenshot = { id -> editorViewModel.loadScreenshot(id) },
                            onImportPhoto = { uri ->
                                mainViewModel.importPhoto(uri) { newId ->
                                    editorViewModel.loadScreenshot(newId)
                                }
                            },
                            onSelectTool = editorViewModel::selectTool,
                            onSelectColor = editorViewModel::selectColor,
                            onStrokeWidthChange = editorViewModel::setStrokeWidth,
                            onShapeKindChange = editorViewModel::setShapeKind,
                            onShapeFilledChange = editorViewModel::setShapeFilled,
                            onTextPillChange = editorViewModel::setTextBackgroundPill,
                            onApplyOperation = editorViewModel::applyOperation,
                            onUndo = editorViewModel::undo,
                            onRedo = editorViewModel::redo,
                            onReset = editorViewModel::resetAll,
                            onSave = { overwrite, fmt, quality ->
                                editorViewModel.saveEdited(overwrite, fmt, quality) { savedId ->
                                    navController.navigate(Routes.detail(savedId)) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            preferences = prefs,
                            albums = albums,
                            usedStorageBytes = usedBytes,
                            availableStorageBytes = availableBytes,
                            storageAccessMode = storageAccessMode,
                            onRequestLegacyGalleryPermission = { requestLegacyGalleryPermission() },
                            onUpdatePreferences = mainViewModel::updatePreferences,
                            onClearCache = mainViewModel::clearTemporaryCache
                        )
                    }

                    composable(
                        route = Routes.DETAIL,
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) { entry ->
                        val shotId = entry.arguments?.getLong("id") ?: -1L
                        val previewList = remember(filteredScreenshots, allScreenshots, shotId) {
                            if (filteredScreenshots.any { it.id == shotId }) filteredScreenshots
                            else allScreenshots
                        }
                        ScreenshotDetailScreen(
                            screenshots = previewList,
                            initialScreenshotId = shotId,
                            albums = albums,
                            defaultJpegQuality = prefs.jpegQuality,
                            onBack = { navController.popBackStack() },
                            onEdit = { id -> navController.navigate(Routes.editor(id)) },
                            onExtractText = { id -> navController.navigate(Routes.ocr(id)) },
                            onExportPdf = { id -> navController.navigate(Routes.pdf(listOf(id))) },
                            onExportImage = galleryViewModel::exportScreenshot,
                            onExportImageToUri = galleryViewModel::saveScreenshotToExternalUri,
                            onDuplicate = galleryViewModel::duplicateScreenshot,
                            onCopyClipboard = { shot ->
                                galleryViewModel.copyImageToClipboard(shot.filePath, shot.mediaStoreUri)
                            },
                            onShare = { shot ->
                                galleryViewModel.createShareImagesIntentForItems(listOf(shot))?.let { intent ->
                                    context.startActivity(Intent.createChooser(intent, "Share Screenshot"))
                                }
                            },
                            onRename = galleryViewModel::renameScreenshot,
                            onToggleFavorite = galleryViewModel::toggleFavorite,
                            onMoveToAlbum = { id, albumId -> galleryViewModel.moveScreenshotsToAlbum(listOf(id), albumId) },
                            onCreateAlbum = galleryViewModel::createAlbum,
                            onRetake = { requestCapture(true, CaptureMode.MANUAL, StitchDirection.VERTICAL) },
                            onDelete = galleryViewModel::deleteScreenshot
                        )
                    }

                    composable(
                        route = "${Routes.PDF}?ids={ids}",
                        arguments = listOf(navArgument("ids") {
                            type = NavType.StringType
                            defaultValue = ""
                        })
                    ) { entry ->
                        val idsStr = entry.arguments?.getString("ids").orEmpty()
                        val initialIds = remember(idsStr) {
                            idsStr.split(",").mapNotNull { it.toLongOrNull() }
                        }
                        LaunchedEffect(Unit) { mainViewModel.resetPdfState() }
                        PdfExportScreen(
                            screenshots = allScreenshots,
                            initialSelectedIds = initialIds,
                            uiState = pdfState,
                            defaultQuality = prefs.jpegQuality,
                            onBack = { navController.popBackStack() },
                            onImportPhoto = { uri, cb -> mainViewModel.importPhoto(uri, cb) },
                            onExportPdf = mainViewModel::exportToPdf,
                            onSharePdf = { filePath ->
                                val intent = mainViewModel.shareFileIntent(filePath, "application/pdf")
                                context.startActivity(Intent.createChooser(intent, "Share PDF"))
                            }
                        )
                    }

                    composable(
                        route = "${Routes.OCR}?id={id}",
                        arguments = listOf(navArgument("id") {
                            type = NavType.LongType
                            defaultValue = -1L
                        })
                    ) { entry ->
                        val idArg = entry.arguments?.getLong("id")?.takeIf { it > 0L }
                        OcrScreen(
                            screenshots = allScreenshots,
                            initialScreenshotId = idArg,
                            uiState = ocrState,
                            onBack = { navController.popBackStack() },
                            onImportPhoto = { uri, cb -> mainViewModel.importPhoto(uri, cb) },
                            onRunOcr = mainViewModel::extractText,
                            onSearchQueryChange = mainViewModel::setOcrSearchQuery,
                            onCopyText = mainViewModel::copyExtractedText,
                            onExportTxt = { text, baseTitle ->
                                val intent = mainViewModel.exportExtractedTextToTxt(text, baseTitle)
                                if (intent != null) {
                                    context.startActivity(Intent.createChooser(intent, "Share Extracted Text"))
                                }
                            }
                        )
                    }

                    composable(Routes.SEAM_ALIGNMENT) {
                        SeamAlignmentScreen(
                            uiState = seamState,
                            onBack = { navController.popBackStack() },
                            onUpdateJoin = mainViewModel::updateSeamJoin,
                            onReStitch = {
                                mainViewModel.reStitchWithCurrentPlan { savedId ->
                                    navController.navigate(Routes.detail(savedId)) {
                                        popUpTo(Routes.HOME)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
