package com.sadvakassov.filament.kmp.reveal

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
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
import com.sadvakassov.filament.kmp.scene.CardController
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * The reveal renderer (Android). Built by lifting [com.sadvakassov.filament.kmp.scene.FilamentRenderer]'s
 * engine/`SurfaceView`/`UiHelper`/camera/`Choreographer` scaffold and the card quad, then adding a
 * **procedural box** (two half-renderables + a seam-glow quad).
 *
 * Every frame it reads the shared [RevealReducer.visuals] (choreography) and — once in Inspect —
 * [CardController.state] (A3 physics), mapping them onto transforms and the box/glow material
 * colour. The card material stays the opaque UNLIT green from Phase A; only the box + glow use a
 * runtime-built transparent material with a per-frame `baseColor` parameter.
 */
class RevealRenderer(
    context: Context,
    private val reveal: RevealController,
    private val card: CardController,
    onReady: () -> Unit,
) {
    // TextureView (not SurfaceView): it composites in the view hierarchy, so Nav3's enter/exit
    // transitions animate it smoothly. A SurfaceView punches a window hole that can't animate and
    // black-flashes on exit — the "strange" Android exit. Filament's UiHelper supports both.
    val textureView = TextureView(context)

    // Fired once, on the main thread, after the first frame actually renders (readiness signal).
    private var onReady: (() -> Unit)? = onReady

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

    private lateinit var cardMaterial: Material
    private lateinit var fxMaterial: Material
    private lateinit var cardInstance: MaterialInstance
    private lateinit var boxInstance: MaterialInstance
    private lateinit var glowInstance: MaterialInstance

    private lateinit var cardVb: VertexBuffer
    private lateinit var cardIb: IndexBuffer
    private lateinit var halfVb: VertexBuffer
    private lateinit var halfIb: IndexBuffer
    private lateinit var glowVb: VertexBuffer
    private lateinit var glowIb: IndexBuffer

    private val cardEntity = EntityManager.get().create()
    private val lidEntity = EntityManager.get().create()
    private val baseEntity = EntityManager.get().create()
    private val glowEntity = EntityManager.get().create()
    private var cardInScene = false

    private val frameCallback = FrameCallback()
    private val m = FloatArray(16)
    private var destroyed = false
    private var running = false

    init {
        setupView()
        setupScene()
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(textureView)
        resume()
    }

    private fun setupView() {
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.055, 0.055, 0.078, 1.0)
        }
        camera.setProjection(45.0, 1.0, 0.1, 100.0, Camera.Fov.VERTICAL)
        // Pulled back + looking slightly up so the closed box and the risen card both frame.
        camera.lookAt(0.0, 0.4, 4.6, 0.0, 0.4, 0.0, 0.0, 1.0, 0.0)
        view.camera = camera
        view.scene = scene
    }

    private fun setupScene() {
        cardMaterial = buildCardMaterial()
        fxMaterial = buildFxMaterial()
        cardInstance = cardMaterial.createInstance()
        boxInstance = fxMaterial.createInstance()
        glowInstance = fxMaterial.createInstance()

        // Card quad (~card aspect), facing +Z — same geometry as FilamentRenderer.
        cardVb = buildVertexBuffer(
            floatArrayOf(
                -0.5f, -0.7f, 0f,
                0.5f, -0.7f, 0f,
                0.5f, 0.7f, 0f,
                -0.5f, 0.7f, 0f,
            ),
            4,
        )
        cardIb = buildIndexBuffer(shortArrayOf(0, 1, 2, 0, 2, 3))
        buildRenderable(cardEntity, cardVb, cardIb, 6, cardInstance)

        // Box half (one mesh, two instances: lid + base). Closed, the two halves form a 1.0-tall box.
        val (hv, hi) = boxHalfMesh(BOX_W, BOX_HALF_H, BOX_D)
        halfVb = hv
        halfIb = hi
        buildRenderable(lidEntity, halfVb, halfIb, 36, boxInstance)
        buildRenderable(baseEntity, halfVb, halfIb, 36, boxInstance)

        // Seam glow: a thin quad at the front of the seam, scaled/brightened as the box opens.
        glowVb = buildVertexBuffer(
            floatArrayOf(
                -GLOW_W / 2f, -0.5f, 0f,
                GLOW_W / 2f, -0.5f, 0f,
                GLOW_W / 2f, 0.5f, 0f,
                -GLOW_W / 2f, 0.5f, 0f,
            ),
            4,
        )
        glowIb = buildIndexBuffer(shortArrayOf(0, 1, 2, 0, 2, 3))
        buildRenderable(glowEntity, glowVb, glowIb, 6, glowInstance)

        // Start with the box + glow visible; the card joins the scene once it rises.
        scene.addEntity(lidEntity)
        scene.addEntity(baseEntity)
        scene.addEntity(glowEntity)
    }

    private fun buildVertexBuffer(vertices: FloatArray, count: Int): VertexBuffer {
        val buf = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder())
        buf.asFloatBuffer().put(vertices)
        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(count)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)
        vb.setBufferAt(engine, 0, buf)
        return vb
    }

    private fun buildIndexBuffer(indices: ShortArray): IndexBuffer {
        val buf = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        buf.asShortBuffer().put(indices)
        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, buf)
        return ib
    }

    private fun buildRenderable(entity: Int, vb: VertexBuffer, ib: IndexBuffer, indexCount: Int, mi: MaterialInstance) {
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, 2f, 2f, 2f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, indexCount)
            .material(0, mi)
            .culling(false)
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, entity)
        engine.transformManager.create(entity)
    }

    /** A box of the given size centered at origin: 8 vertices, 12 triangles (positions only). */
    private fun boxHalfMesh(w: Float, h: Float, d: Float): Pair<VertexBuffer, IndexBuffer> {
        val x = w / 2f; val y = h / 2f; val z = d / 2f
        val vb = buildVertexBuffer(
            floatArrayOf(
                -x, -y, -z, x, -y, -z, x, y, -z, -x, y, -z, // back
                -x, -y, z, x, -y, z, x, y, z, -x, y, z,     // front
            ),
            8,
        )
        val ib = buildIndexBuffer(
            shortArrayOf(
                4, 5, 6, 4, 6, 7, // front
                1, 0, 3, 1, 3, 2, // back
                0, 4, 7, 0, 7, 3, // left
                5, 1, 2, 5, 2, 6, // right
                3, 7, 6, 3, 6, 2, // top
                0, 1, 5, 0, 5, 4, // bottom
            ),
        )
        return vb to ib
    }

    private fun buildCardMaterial(): Material = buildMaterial {
        name("card")
        material(
            """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor = float4(0.55, 0.80, 0.58, 1.0);
            }
            """.trimIndent(),
        )
        shading(MaterialBuilder.Shading.UNLIT)
        doubleSided(true)
    }

    private fun buildFxMaterial(): Material = buildMaterial {
        name("fx")
        uniformParameter(MaterialBuilder.UniformType.FLOAT4, "baseColor")
        material(
            """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor = materialParams.baseColor;
            }
            """.trimIndent(),
        )
        shading(MaterialBuilder.Shading.UNLIT)
        doubleSided(true)
        blending(MaterialBuilder.BlendingMode.TRANSPARENT)
    }

    private inline fun buildMaterial(configure: MaterialBuilder.() -> Unit): Material {
        MaterialBuilder.init()
        try {
            val pkg = MaterialBuilder()
                .apply(configure)
                .platform(MaterialBuilder.Platform.MOBILE)
                .targetApi(MaterialBuilder.TargetApi.OPENGL)
                .build(engine)
            require(pkg.isValid) { "Filament material failed to compile" }
            val buffer = pkg.buffer
            return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    /** Column-major Ry(yaw)·Rx(pitch)·uniformScale with translation. */
    private fun composeTransform(yaw: Float, pitch: Float, scale: Float, tx: Float, ty: Float, tz: Float) {
        val cy = cos(yaw); val sy = sin(yaw); val cx = cos(pitch); val sx = sin(pitch); val s = scale
        m[0] = cy * s; m[1] = 0f; m[2] = -sy * s; m[3] = 0f
        m[4] = sy * sx * s; m[5] = cx * s; m[6] = cy * sx * s; m[7] = 0f
        m[8] = sy * cx * s; m[9] = -sx * s; m[10] = cy * cx * s; m[11] = 0f
        m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1f
    }

    /** Axis-aligned non-uniform scale with translation (no rotation) — for the glow quad. */
    private fun composeScaled(sx: Float, sy: Float, sz: Float, tx: Float, ty: Float, tz: Float) {
        m[0] = sx; m[1] = 0f; m[2] = 0f; m[3] = 0f
        m[4] = 0f; m[5] = sy; m[6] = 0f; m[7] = 0f
        m[8] = 0f; m[9] = 0f; m[10] = sz; m[11] = 0f
        m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1f
    }

    private fun setTransform(entity: Int) {
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(entity), m)
    }

    private fun updateScene() {
        val v = RevealReducer.visuals(reveal.state.value)

        // Box halves: split apart on Y, shake on X, gentle bob, uniform pop scale.
        val lidY = BOX_HALF_H / 2f + v.boxSplit * SPLIT_DIST + v.boxBobY
        val baseY = -BOX_HALF_H / 2f - v.boxSplit * SPLIT_DIST + v.boxBobY
        composeTransform(0f, 0f, v.boxScale, v.shakeX, lidY, 0f); setTransform(lidEntity)
        composeTransform(0f, 0f, v.boxScale, v.shakeX, baseY, 0f); setTransform(baseEntity)
        setColor(boxInstance, BOX_R, BOX_G, BOX_B, v.boxOpacity)

        // Seam glow: a thin bar at the seam that widens + brightens as the box opens.
        val glowH = GLOW_MIN_H + v.boxSplit * GLOW_SPLIT_H
        composeScaled(1f, glowH, 1f, v.shakeX, v.boxBobY, BOX_D / 2f + 0.02f); setTransform(glowEntity)
        setColor(glowInstance, GLOW_R, GLOW_G, GLOW_B, v.seamGlow)

        // Card: hidden until it rises, then choreography drives it; in Inspect, A3 physics take over.
        if (v.cardVisible && !cardInScene) { scene.addEntity(cardEntity); cardInScene = true }
        if (!v.cardVisible && cardInScene) { scene.removeEntity(cardEntity); cardInScene = false }
        if (v.cardVisible) {
            if (v.inspect) {
                val c = card.state.value
                composeTransform(c.yaw, c.pitch, c.scale, 0f, CARD_SETTLE_Y, 0f)
            } else {
                composeTransform(v.cardYaw, 0f, 1f, 0f, v.cardRise * RISE_DIST, 0f)
            }
            setTransform(cardEntity)
        }
    }

    /** Set a TRANSPARENT material colour with premultiplied alpha. */
    private fun setColor(mi: MaterialInstance, r: Float, g: Float, b: Float, a: Float) {
        mi.setParameter("baseColor", r * a, g * a, b * a, a)
    }

    // --- Lifecycle ---

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

        engine.destroyEntity(cardEntity)
        engine.destroyEntity(lidEntity)
        engine.destroyEntity(baseEntity)
        engine.destroyEntity(glowEntity)
        engine.destroyVertexBuffer(cardVb)
        engine.destroyIndexBuffer(cardIb)
        engine.destroyVertexBuffer(halfVb)
        engine.destroyIndexBuffer(halfIb)
        engine.destroyVertexBuffer(glowVb)
        engine.destroyIndexBuffer(glowIb)
        engine.destroyMaterialInstance(cardInstance)
        engine.destroyMaterialInstance(boxInstance)
        engine.destroyMaterialInstance(glowInstance)
        engine.destroyMaterial(cardMaterial)
        engine.destroyMaterial(fxMaterial)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)

        val em = EntityManager.get()
        em.destroy(cardEntity)
        em.destroy(lidEntity)
        em.destroy(baseEntity)
        em.destroy(glowEntity)
        em.destroy(cameraEntity)

        engine.destroy()
    }

    private inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            val sc = swapChain ?: return
            updateScene()
            if (renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
                // First real frame drawn → report readiness once (already on the main thread).
                onReady?.let { it(); onReady = null }
            }
        }
    }

    private inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
            displayHelper.attach(renderer, textureView.display)
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

        // Geometry
        private const val BOX_W = 1.2f
        private const val BOX_HALF_H = 0.5f
        private const val BOX_D = 0.7f
        private const val SPLIT_DIST = 0.55f
        private const val RISE_DIST = 1.3f
        private const val CARD_SETTLE_Y = 1.3f
        private const val GLOW_W = 1.3f
        private const val GLOW_MIN_H = 0.05f
        private const val GLOW_SPLIT_H = 0.5f

        // Colours (linear)
        private const val BOX_R = 0.35f
        private const val BOX_G = 0.30f
        private const val BOX_B = 0.55f
        private const val GLOW_R = 1.0f
        private const val GLOW_G = 0.85f
        private const val GLOW_B = 0.40f
    }
}
