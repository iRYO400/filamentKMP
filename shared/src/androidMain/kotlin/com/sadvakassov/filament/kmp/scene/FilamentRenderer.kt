package com.sadvakassov.filament.kmp.scene

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.filamat.MaterialBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * A2: the first *real* Filament renderer (Android). Replaces the A1 2D placeholder.
 *
 * Owns a Filament [Engine] and draws a procedural quad with a runtime-built material. Every
 * frame (driven by [Choreographer]) it reads `controller.state.value` — the same shared state
 * the Compose UI uses — and maps yaw/pitch/scale onto the renderable via [com.google.android.filament.TransformManager].
 *
 * Threading: all Filament API calls happen on the Android main thread (AndroidView factory,
 * UiHelper callbacks and the Choreographer callback all run there), which is what Filament
 * requires. Rendering itself runs on Filament's internal render thread.
 *
 * Scope note: material is UNLIT + doubleSided to guarantee a clearly visible, two-sided card
 * without depending on a correct tangent frame / lighting. Real PBR + lighting + the
 * holographic material are Phase B (B2). Geometry is a flat quad; thickness/glTF come later.
 */
class FilamentRenderer(context: Context, private val controller: CardController) {

    val surfaceView = SurfaceView(context)

    private val choreographer = Choreographer.getInstance()
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(context)

    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)

    private var swapChain: SwapChain? = null

    private lateinit var material: Material
    private lateinit var materialInstance: MaterialInstance
    private lateinit var vertexBuffer: VertexBuffer
    private lateinit var indexBuffer: IndexBuffer
    private val renderable = EntityManager.get().create()

    private val frameCallback = FrameCallback()
    private val transform = FloatArray(16)
    private var destroyed = false
    private var running = false

    init {
        setupView()
        setupScene()
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(surfaceView)
        // Start immediately: the host is typically already RESUMED on first composition,
        // so the lifecycle observer won't replay ON_RESUME. doFrame no-ops until the swap
        // chain exists. The running-guard keeps resume()/ON_RESUME from double-posting.
        resume()
    }

    private fun setupView() {
        // Soft pink background (linear space) — matches the Compose surface around the card.
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.055, 0.055, 0.078, 1.0)
        }
        camera.setProjection(45.0, 1.0, 0.1, 100.0, Camera.Fov.VERTICAL)
        // Eye in front of the card (+Z), looking at the origin.
        camera.lookAt(0.0, 0.0, 3.2, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        view.camera = camera
        view.scene = scene
    }

    private fun setupScene() {
        material = buildCardMaterial()
        materialInstance = material.createInstance()

        // Quad in the XY plane, ~card aspect (1.0 x 1.4), facing +Z toward the camera.
        val vertices = floatArrayOf(
            -0.5f, -0.7f, 0f,
            0.5f, -0.7f, 0f,
            0.5f, 0.7f, 0f,
            -0.5f, 0.7f, 0f,
        )
        val indices = shortArrayOf(0, 1, 2, 0, 2, 3)

        val vBuf = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder())
        vBuf.asFloatBuffer().put(vertices)
        val iBuf = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        iBuf.asShortBuffer().put(indices)

        vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(4)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, vBuf)

        indexBuffer = IndexBuffer.Builder()
            .indexCount(6)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer.setBuffer(engine, iBuf)

        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, 1f, 1f, 1f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, 6)
            .material(0, materialInstance)
            .culling(false)
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, renderable)

        // Renderable needs a transform component so we can drive it from CardState.
        engine.transformManager.create(renderable)
        scene.addEntity(renderable)
    }

    private fun buildCardMaterial(): Material {
        MaterialBuilder.init()
        try {
            val pkg = MaterialBuilder()
                .name("card")
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        material.baseColor = float4(0.55, 0.80, 0.58, 1.0); // soft green
                    }
                    """.trimIndent(),
                )
                .shading(MaterialBuilder.Shading.UNLIT)
                .doubleSided(true)
                .platform(MaterialBuilder.Platform.MOBILE)
                .targetApi(MaterialBuilder.TargetApi.OPENGL) // matches Engine.create() default GL backend
                .build(engine)
            require(pkg.isValid) { "Filament material failed to compile" }
            val buffer = pkg.buffer
            return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    /** Build a column-major 4x4 = Ry(yaw) * Rx(pitch) * uniformScale, translation 0. */
    private fun updateTransform(yaw: Float, pitch: Float, scale: Float) {
        val cy = cos(yaw); val sy = sin(yaw)
        val cx = cos(pitch); val sx = sin(pitch)
        val s = scale
        transform[0] = cy * s; transform[1] = 0f; transform[2] = -sy * s; transform[3] = 0f
        transform[4] = sy * sx * s; transform[5] = cx * s; transform[6] = cy * sx * s; transform[7] = 0f
        transform[8] = sy * cx * s; transform[9] = -sx * s; transform[10] = cy * cx * s; transform[11] = 0f
        transform[12] = 0f; transform[13] = 0f; transform[14] = 0f; transform[15] = 1f
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(renderable), transform)
    }

    // --- Lifecycle (driven from the Compose actual) ---

    fun resume() {
        if (destroyed || running) return
        running = true
        choreographer.postFrameCallback(frameCallback)
    }

    fun pause() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        running = false
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()

        engine.destroyEntity(renderable)
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        engine.destroyMaterialInstance(materialInstance)
        engine.destroyMaterial(material)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)

        val em = EntityManager.get()
        em.destroy(renderable)
        em.destroy(cameraEntity)

        engine.destroy()
    }

    private inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            val sc = swapChain ?: return
            val s = controller.state.value
            updateTransform(s.yaw, s.pitch, s.scale)
            if (renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }

    private inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
            displayHelper.attach(renderer, surfaceView.display)
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                engine.destroySwapChain(it)
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            val aspect = width.toDouble() / height.toDouble()
            camera.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
            view.viewport = Viewport(0, 0, width, height)
        }
    }

    companion object {
        init {
            Filament.init()
        }
    }
}
