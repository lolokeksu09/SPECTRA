package com.spectra.twin.render

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.widget.FrameLayout
import com.google.android.filament.View
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * GLB-backed offline Y70 viewer. No procedural meshes, network or root.
 * One engine/asset per view lifetime; 30 fps cap and lifecycle-aware rendering.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class FilamentDeviceView(context: Context) : FrameLayout(context) {
    companion object {
        private const val BUILTIN_WHITE = ModelSources.WHITE_ASSET
        private const val BUILTIN_BLACK = ModelSources.BLACK_ASSET
        private const val MAX_BYTES = 16 * 1024 * 1024
        init { Utils.init() }
    }

    // TextureView is composited within Compose's clipped viewer card; on some
    // Android 16 drivers a SurfaceView can leave garbage pixels outside the model.
    private val surface = TextureView(context).apply { isOpaque = true }
    private val viewer: ModelViewer
    private var fillLight: Int = 0
    private var nativeReleased = false
    private val scheduler = Choreographer.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var generation = 0L
    private var sourceKey: String? = null
    private var lastSide: DeviceView? = null
    private var active = false
    private var frameFailures = 0
    private var lastTouchNs = 0L
    private var attached = false
    private var disposed = false
    private var callbackScheduled = false
    private var lastFrame = 0L
    private var yaw = PI.toFloat() - .2f
    private var pitch = -.1f
    private var radius = 4.2f
    private var x = 0f
    private var y = 0f
    private val focusZ = -4.0
    var onStatus: (String?) -> Unit = {}

    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            if (factor.isFinite() && factor > 0f) radius = (radius / factor).coerceIn(2.9f, 8f)
            return true
        }
    })

    private val frameCallback = Choreographer.FrameCallback { ns ->
        callbackScheduled = false
        if (active && attached && !disposed) {
            val interval = if (System.nanoTime() - lastTouchNs < 1_000_000_000L ||
                    loadJob?.isActive == true) 33_000_000L else 100_000_000L
            if (ns - lastFrame >= interval) {
                lastFrame = ns
                val c = cos(pitch)
                val eyeX = radius * c * sin(yaw)
                val eyeY = radius * sin(pitch)
                val eyeZ = focusZ + radius * c * cos(yaw)
                // Camera updates and rendering both touch Filament native state.
                // Keep them behind the same recoverable error boundary.
                try {
                    viewer.camera.lookAt(eyeX.toDouble(),eyeY.toDouble(),eyeZ.toDouble(),0.0,0.0,focusZ,0.0,1.0,0.0)
                    viewer.render(ns)
                    frameFailures = 0
                } catch (e: Exception) {
                    frameFailures++
                    if (frameFailures >= 2) {
                        onStatus("Ошибка графики: " + e.javaClass.simpleName)
                        pause()
                    }
                }
            }
            schedule()
        }
    }

    init {
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Register before ModelViewer so we release our own light BEFORE its
        // detach listener destroys the native engine. Reverse order can crash.
        surface.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {}
            override fun onViewDetachedFromWindow(v: android.view.View) {
                pause()
                disposed = true
                generation++
                loadJob?.cancel()
                scope.cancel()
                if (!nativeReleased && fillLight != 0) {
                    nativeReleased = true
                    viewer.scene.removeEntity(fillLight)
                    viewer.engine.destroyEntity(fillLight)
                    EntityManager.get().destroy(fillLight)
                    fillLight = 0
                }
            }
        })
        viewer = ModelViewer(surface, manipulator = null)
        // Opaque TextureView + an explicitly cleared swapchain. Filament's
        // Renderer.ClearOptions defaults to clear=false; retaining uninitialised
        // swapchain pixels can expose noisy strips on some Android GPU drivers.
        // Clearing is independent of dynamic-resolution / post-processing.
        viewer.renderer.clearOptions = viewer.renderer.clearOptions.apply {
            clear = true
            // Some Android drivers expose invalidated pixels after discard.
            discard = false
            clearColor = doubleArrayOf(0.012, 0.016, 0.024, 1.0)
        }
        viewer.scene.skybox = null
        // GPU compatibility: disabling only bloom, AO, and dynamic scaling
        // still leaves Filament's intermediate post-processing buffers active.
        // Filament upstream documents corrupted/tearing pixels on Android
        // OpenGL composition; a full post-processing bypass avoids this path.
        // This sacrifices FXAA/tone mapping until validated on the Legion Y70.
        viewer.view.blendMode = View.BlendMode.OPAQUE
        viewer.view.isPostProcessingEnabled = false
        viewer.view.setShadowingEnabled(false)
        viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
            enabled = false
        }
        viewer.view.antiAliasing = View.AntiAliasing.NONE
        viewer.view.bloomOptions = viewer.view.bloomOptions.apply { enabled = false }
        viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply { enabled = false }
        viewer.engine.lightManager.setDirection(
            viewer.engine.lightManager.getInstance(viewer.light), -.3f, -.6f, -.8f
        )
        // Back face was almost black: the default sun lights only one side.
        fillLight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .direction(.25f, -.45f, .85f)
            .intensity(42_000f)
            .castShadows(false)
            .build(viewer.engine, fillLight)
        viewer.scene.addEntity(fillLight)
        surface.setOnTouchListener { _, event -> handleTouch(event) }
        contentDescription = "3D-модель Lenovo Legion Y70. Поворот одним пальцем, масштаб двумя."
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        lastTouchNs = System.nanoTime()
        pinch.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                x = event.x; y = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                x = event.getX(0); y = event.getY(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pinch.isInProgress && event.pointerCount == 1) {
                    yaw = (yaw + (event.x - x) * .008f) % (2f * PI.toFloat())
                    pitch = (pitch + (event.y - y) * .006f).coerceIn(-1.15f, 1.15f)
                }
                x = event.getX(0); y = event.getY(0)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (event.actionIndex == 0) 1 else 0
                if (remaining < event.pointerCount) {
                    x = event.getX(remaining); y = event.getY(remaining)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    fun showSide(side: DeviceView) {
        if (lastSide == side) return
        lastSide = side
        yaw = side.filamentYaw()
        pitch = -.10f
    }

    fun setSource(uri: Uri?, finish: DeviceFinish) {
        val key = uri?.toString() ?: ModelSources.builtinAsset(finish)
        if (disposed || sourceKey == key) return
        sourceKey = key
        val request = ++generation
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    (if (uri == null) context.assets.open(key) else
                        context.contentResolver.openInputStream(uri)
                            ?: throw IllegalArgumentException("Файл недоступен")).use {
                        val bytes = it.readBytes()
                        require(bytes.size <= MAX_BYTES) { "GLB превышает 16 МБ" }
                        GlbHeader.validate(bytes)
                        bytes
                    }
                }
                if (disposed || generation != request) return@launch
                viewer.loadModelGlb(ByteBuffer.wrap(bytes))
                check(viewer.asset != null) { "GLB не загружен" }
                viewer.transformToUnitCube()
                onStatus(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!disposed && generation == request) {
                    sourceKey = null // allow retry after a file permissions or read failure
                    onStatus("Ошибка 3D: " + e.javaClass.simpleName)
                }
            }
        }
    }

    private fun schedule() {
        if (!callbackScheduled && active && attached && !disposed) {
            callbackScheduled = true
            scheduler.postFrameCallback(frameCallback)
        }
    }

    fun resume() {
        if (disposed) return
        active = true
        lastFrame = 0L
        schedule()
    }

    fun pause() {
        active = false
        if (callbackScheduled) {
            scheduler.removeFrameCallback(frameCallback)
            callbackScheduled = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        schedule()
    }

    override fun onDetachedFromWindow() {
        pause()
        attached = false
        disposed = true
        loadJob?.cancel()
        scope.cancel()
        // Native cleanup already runs in the child's pre-ModelViewer listener.
        // Never access the engine from the parent's later detach callback.
        super.onDetachedFromWindow()
    }
}
