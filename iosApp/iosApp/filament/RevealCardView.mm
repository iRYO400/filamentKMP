#import "RevealCardView.h"

#import <MetalKit/MetalKit.h>

#include <filament/Engine.h>
#include <filament/Renderer.h>
#include <filament/Scene.h>
#include <filament/View.h>
#include <filament/Camera.h>
#include <filament/Viewport.h>
#include <filament/SwapChain.h>
#include <filament/RenderableManager.h>
#include <filament/TransformManager.h>
#include <filament/VertexBuffer.h>
#include <filament/IndexBuffer.h>
#include <filament/Material.h>
#include <filament/MaterialInstance.h>
#include <filament/Box.h>

#include <filamat/MaterialBuilder.h>

#include <utils/EntityManager.h>
#include <utils/Entity.h>

#include <math/mat4.h>
#include <math/vec3.h>
#include <math/vec4.h>

#include <cstdlib>
#include <cstring>

using namespace filament;
using namespace filament::math;
using namespace utils;

// Filament uploads buffer data asynchronously and does NOT copy it — the caller must keep the
// bytes alive until the backend is done. The card quad gets away with `static const` arrays, but
// the box/glow vertices are computed from runtime constants, so we copy every buffer to the heap
// and free it via this callback once the GPU upload completes. (Skipping this left the box with
// garbage vertices — invisible — while the static card quad rendered fine.)
static void releaseBuffer(void* buffer, size_t, void*) { free(buffer); }

// Geometry / feel constants — kept in sync with androidMain RevealRenderer.
static const float BOX_W = 1.2f;
static const float BOX_HALF_H = 0.5f;
static const float BOX_D = 0.7f;
static const float SPLIT_DIST = 0.55f;
static const float RISE_DIST = 1.3f;
static const float CARD_SETTLE_Y = 1.3f;
static const float GLOW_W = 1.3f;
static const float GLOW_MIN_H = 0.05f;
static const float GLOW_SPLIT_H = 0.5f;
static const float3 BOX_COLOR = {0.35f, 0.30f, 0.55f};
static const float3 GLOW_COLOR = {1.0f, 0.85f, 0.40f};

/**
 * iOS reveal renderer. Faithful port of androidMain `RevealRenderer`: an opaque UNLIT green card
 * quad + a transparent UNLIT box (two half-cubes sharing a mesh) + a seam-glow quad, driven each
 * frame by the channels pushed from Kotlin. The MTKView delegate is the vsync loop (Choreographer
 * equivalent).
 */
@interface RevealCardView () <MTKViewDelegate>
@end

@implementation RevealCardView {
    MTKView* _mtkView;

    Engine* _engine;
    Renderer* _renderer;
    Scene* _scene;
    View* _view;
    Camera* _camera;
    SwapChain* _swapChain;

    Material* _cardMaterial;
    Material* _fxMaterial;
    MaterialInstance* _cardInstance;
    MaterialInstance* _boxInstance;
    MaterialInstance* _glowInstance;

    VertexBuffer* _cardVb;
    IndexBuffer* _cardIb;
    VertexBuffer* _halfVb;
    IndexBuffer* _halfIb;
    VertexBuffer* _glowVb;
    IndexBuffer* _glowIb;

    Entity _cardEntity;
    Entity _lidEntity;
    Entity _baseEntity;
    Entity _glowEntity;
    Entity _cameraEntity;
    BOOL _cardInScene;

    // Latest channels pushed from Kotlin; read on the draw callback.
    float _shakeX, _bob, _boxScale, _split, _glow, _opacity;
    float _cardRise, _spinYaw, _cardYaw, _cardPitch, _cardScale;
    BOOL _cardVisible, _inspect;
    BOOL _disposed;
}

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self) {
        _boxScale = 1.0f;
        _opacity = 1.0f;
        _cardScale = 1.0f;
        _cardInScene = NO;
        _disposed = NO;
        [self setupMetalView];
        [self setupFilament];
        [self registerLifecycleObservers];
    }
    return self;
}

#pragma mark - App lifecycle

- (void)registerLifecycleObservers {
    NSNotificationCenter* nc = [NSNotificationCenter defaultCenter];
    [nc addObserver:self selector:@selector(appDidEnterBackground)
               name:UIApplicationDidEnterBackgroundNotification object:nil];
    [nc addObserver:self selector:@selector(appWillEnterForeground)
               name:UIApplicationWillEnterForegroundNotification object:nil];
}

- (void)appDidEnterBackground { if (!_disposed) _mtkView.paused = YES; }
- (void)appWillEnterForeground { if (!_disposed) _mtkView.paused = NO; }

- (void)setupMetalView {
    _mtkView = [[MTKView alloc] initWithFrame:self.bounds device:MTLCreateSystemDefaultDevice()];
    _mtkView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    _mtkView.enableSetNeedsDisplay = NO;
    _mtkView.paused = NO;
    _mtkView.delegate = self;
    _mtkView.clearColor = MTLClearColorMake(0.055, 0.055, 0.078, 1.0);
    [self addSubview:_mtkView];
}

- (void)setupFilament {
    _engine = Engine::create(Engine::Backend::METAL);
    _renderer = _engine->createRenderer();
    _scene = _engine->createScene();
    _view = _engine->createView();

    EntityManager& em = EntityManager::get();
    _cameraEntity = em.create();
    _camera = _engine->createCamera(_cameraEntity);

    _swapChain = _engine->createSwapChain((__bridge void*)_mtkView.layer);

    Renderer::ClearOptions clearOptions;
    clearOptions.clear = true;
    clearOptions.clearColor = {0.055f, 0.055f, 0.078f, 1.0f};
    _renderer->setClearOptions(clearOptions);

    _camera->setProjection(45.0, 1.0, 0.1, 100.0, Camera::Fov::VERTICAL);
    _camera->lookAt({0.0, 0.4, 4.6}, {0.0, 0.4, 0.0}, {0.0, 1.0, 0.0});
    _view->setCamera(_camera);
    _view->setScene(_scene);

    [self buildMaterials];
    [self buildGeometry];
}

- (void)buildMaterials {
    filamat::MaterialBuilder::init();

    // Card: opaque UNLIT green (same as FilamentCardView).
    {
        filamat::MaterialBuilder builder;
        filamat::Package pkg = builder
            .name("card")
            .material(
                "void material(inout MaterialInputs material) {\n"
                "    prepareMaterial(material);\n"
                "    material.baseColor = float4(0.55, 0.80, 0.58, 1.0);\n"
                "}\n")
            .shading(filamat::MaterialBuilder::Shading::UNLIT)
            .doubleSided(true)
            .platform(filamat::MaterialBuilder::Platform::MOBILE)
            .targetApi(filamat::MaterialBuilder::TargetApi::METAL)
            .build(_engine->getJobSystem());
        _cardMaterial = Material::Builder().package(pkg.getData(), pkg.getSize()).build(*_engine);
        _cardInstance = _cardMaterial->createInstance();
    }

    // FX: transparent UNLIT with a per-frame baseColor parameter (box body + seam glow).
    {
        filamat::MaterialBuilder builder;
        filamat::Package pkg = builder
            .name("fx")
            .parameter("baseColor", filamat::MaterialBuilder::UniformType::FLOAT4)
            .material(
                "void material(inout MaterialInputs material) {\n"
                "    prepareMaterial(material);\n"
                "    material.baseColor = materialParams.baseColor;\n"
                "}\n")
            .shading(filamat::MaterialBuilder::Shading::UNLIT)
            .doubleSided(true)
            .blending(BlendingMode::TRANSPARENT)
            .platform(filamat::MaterialBuilder::Platform::MOBILE)
            .targetApi(filamat::MaterialBuilder::TargetApi::METAL)
            .build(_engine->getJobSystem());
        _fxMaterial = Material::Builder().package(pkg.getData(), pkg.getSize()).build(*_engine);
        _boxInstance = _fxMaterial->createInstance();
        _glowInstance = _fxMaterial->createInstance();
    }

    filamat::MaterialBuilder::shutdown();
}

- (VertexBuffer*)vertexBuffer:(const float*)vertices count:(uint32_t)count bytes:(size_t)bytes {
    void* copy = malloc(bytes);
    memcpy(copy, vertices, bytes);
    VertexBuffer* vb = VertexBuffer::Builder()
        .vertexCount(count)
        .bufferCount(1)
        .attribute(VertexAttribute::POSITION, 0, VertexBuffer::AttributeType::FLOAT3, 0, 12)
        .build(*_engine);
    vb->setBufferAt(*_engine, 0, VertexBuffer::BufferDescriptor(copy, bytes, releaseBuffer));
    return vb;
}

- (IndexBuffer*)indexBuffer:(const uint16_t*)indices count:(uint32_t)count bytes:(size_t)bytes {
    void* copy = malloc(bytes);
    memcpy(copy, indices, bytes);
    IndexBuffer* ib = IndexBuffer::Builder()
        .indexCount(count)
        .bufferType(IndexBuffer::IndexType::USHORT)
        .build(*_engine);
    ib->setBuffer(*_engine, IndexBuffer::BufferDescriptor(copy, bytes, releaseBuffer));
    return ib;
}

- (Entity)renderable:(VertexBuffer*)vb ib:(IndexBuffer*)ib count:(uint32_t)count mi:(MaterialInstance*)mi {
    Entity e = EntityManager::get().create();
    RenderableManager::Builder(1)
        .boundingBox({{0, 0, 0}, {2, 2, 2}})
        .geometry(0, RenderableManager::PrimitiveType::TRIANGLES, vb, ib, 0, count)
        .material(0, mi)
        .culling(false)
        .castShadows(false)
        .receiveShadows(false)
        .build(*_engine, e);
    _engine->getTransformManager().create(e);
    return e;
}

- (void)buildGeometry {
    // Card quad (~card aspect), facing +Z.
    static const float cardV[] = {
        -0.5f, -0.7f, 0.0f, 0.5f, -0.7f, 0.0f, 0.5f, 0.7f, 0.0f, -0.5f, 0.7f, 0.0f,
    };
    static const uint16_t cardI[] = {0, 1, 2, 0, 2, 3};
    _cardVb = [self vertexBuffer:cardV count:4 bytes:sizeof(cardV)];
    _cardIb = [self indexBuffer:cardI count:6 bytes:sizeof(cardI)];
    _cardEntity = [self renderable:_cardVb ib:_cardIb count:6 mi:_cardInstance];

    // Box half cube (8 verts, 36 indices) — two instances form the closed box.
    const float x = BOX_W / 2.0f, y = BOX_HALF_H / 2.0f, z = BOX_D / 2.0f;
    const float halfV[] = {
        -x, -y, -z, x, -y, -z, x, y, -z, -x, y, -z,
        -x, -y,  z, x, -y,  z, x, y,  z, -x, y,  z,
    };
    static const uint16_t halfI[] = {
        4, 5, 6, 4, 6, 7,  1, 0, 3, 1, 3, 2,  0, 4, 7, 0, 7, 3,
        5, 1, 2, 5, 2, 6,  3, 7, 6, 3, 6, 2,  0, 1, 5, 0, 5, 4,
    };
    _halfVb = [self vertexBuffer:halfV count:8 bytes:sizeof(halfV)];
    _halfIb = [self indexBuffer:halfI count:36 bytes:sizeof(halfI)];
    _lidEntity = [self renderable:_halfVb ib:_halfIb count:36 mi:_boxInstance];
    _baseEntity = [self renderable:_halfVb ib:_halfIb count:36 mi:_boxInstance];

    // Seam glow quad (unit height; scaled/brightened as the box opens).
    const float gw = GLOW_W / 2.0f;
    const float glowV[] = {
        -gw, -0.5f, 0.0f, gw, -0.5f, 0.0f, gw, 0.5f, 0.0f, -gw, 0.5f, 0.0f,
    };
    static const uint16_t glowI[] = {0, 1, 2, 0, 2, 3};
    _glowVb = [self vertexBuffer:glowV count:4 bytes:sizeof(glowV)];
    _glowIb = [self indexBuffer:glowI count:6 bytes:sizeof(glowI)];
    _glowEntity = [self renderable:_glowVb ib:_glowIb count:6 mi:_glowInstance];

    _scene->addEntity(_lidEntity);
    _scene->addEntity(_baseEntity);
    _scene->addEntity(_glowEntity);
}

#pragma mark - Channel push (from Kotlin)

- (void)updateShake:(float)shakeX bob:(float)bob scale:(float)scale split:(float)split
               glow:(float)glow opacity:(float)opacity cardVisible:(BOOL)cardVisible
            inspect:(BOOL)inspect cardRise:(float)cardRise spinYaw:(float)spinYaw
                yaw:(float)yaw pitch:(float)pitch cardScale:(float)cardScale {
    _shakeX = shakeX; _bob = bob; _boxScale = scale; _split = split; _glow = glow; _opacity = opacity;
    _cardVisible = cardVisible; _inspect = inspect; _cardRise = cardRise; _spinYaw = spinYaw;
    _cardYaw = yaw; _cardPitch = pitch; _cardScale = cardScale;
}

- (void)setColor:(MaterialInstance*)mi r:(float)r g:(float)g b:(float)b a:(float)a {
    // TRANSPARENT blending expects premultiplied alpha.
    mi->setParameter("baseColor", float4{r * a, g * a, b * a, a});
}

- (void)applyScene {
    TransformManager& tcm = _engine->getTransformManager();

    const float lidY = BOX_HALF_H / 2.0f + _split * SPLIT_DIST + _bob;
    const float baseY = -BOX_HALF_H / 2.0f - _split * SPLIT_DIST + _bob;
    mat4f boxScale = mat4f::scaling(float3{_boxScale, _boxScale, _boxScale});
    tcm.setTransform(tcm.getInstance(_lidEntity),
                     mat4f::translation(float3{_shakeX, lidY, 0}) * boxScale);
    tcm.setTransform(tcm.getInstance(_baseEntity),
                     mat4f::translation(float3{_shakeX, baseY, 0}) * boxScale);
    [self setColor:_boxInstance r:BOX_COLOR.r g:BOX_COLOR.g b:BOX_COLOR.b a:_opacity];

    const float glowH = GLOW_MIN_H + _split * GLOW_SPLIT_H;
    tcm.setTransform(tcm.getInstance(_glowEntity),
                     mat4f::translation(float3{_shakeX, _bob, BOX_D / 2.0f + 0.02f}) *
                     mat4f::scaling(float3{1.0f, glowH, 1.0f}));
    [self setColor:_glowInstance r:GLOW_COLOR.r g:GLOW_COLOR.g b:GLOW_COLOR.b a:_glow];

    if (_cardVisible && !_cardInScene) { _scene->addEntity(_cardEntity); _cardInScene = YES; }
    if (!_cardVisible && _cardInScene) { _scene->remove(_cardEntity); _cardInScene = NO; }
    if (_cardVisible) {
        mat4f m;
        if (_inspect) {
            m = mat4f::translation(float3{0, CARD_SETTLE_Y, 0}) *
                mat4f::rotation(_cardYaw, float3{0, 1, 0}) *
                mat4f::rotation(_cardPitch, float3{1, 0, 0}) *
                mat4f::scaling(float3{_cardScale, _cardScale, _cardScale});
        } else {
            m = mat4f::translation(float3{0, _cardRise * RISE_DIST, 0}) *
                mat4f::rotation(_spinYaw, float3{0, 1, 0});
        }
        tcm.setTransform(tcm.getInstance(_cardEntity), m);
    }
}

#pragma mark - MTKViewDelegate (vsync loop)

- (void)drawInMTKView:(MTKView*)view {
    if (_disposed || _swapChain == nullptr) return;
    [self applyScene];
    if (_renderer->beginFrame(_swapChain)) {
        _renderer->render(_view);
        _renderer->endFrame();
        // First real frame drawn → report readiness once (MTKView draws on the main thread).
        if (self.onReady) { self.onReady(); self.onReady = nil; }
    }
}

- (void)mtkView:(MTKView*)view drawableSizeWillChange:(CGSize)size {
    if (_disposed) return;
    const uint32_t width = (uint32_t)size.width;
    const uint32_t height = (uint32_t)size.height;
    if (width == 0 || height == 0) return;
    const double aspect = (double)width / (double)height;
    _camera->setProjection(45.0, aspect, 0.1, 100.0, Camera::Fov::VERTICAL);
    _view->setViewport({0, 0, width, height});
}

#pragma mark - Teardown

- (void)dispose {
    if (_disposed) return;
    _disposed = YES;
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    _mtkView.paused = YES;
    _mtkView.delegate = nil;

    if (_engine) {
        _engine->destroy(_cardEntity);
        _engine->destroy(_lidEntity);
        _engine->destroy(_baseEntity);
        _engine->destroy(_glowEntity);
        _engine->destroy(_cardVb);
        _engine->destroy(_cardIb);
        _engine->destroy(_halfVb);
        _engine->destroy(_halfIb);
        _engine->destroy(_glowVb);
        _engine->destroy(_glowIb);
        _engine->destroy(_cardInstance);
        _engine->destroy(_boxInstance);
        _engine->destroy(_glowInstance);
        _engine->destroy(_cardMaterial);
        _engine->destroy(_fxMaterial);
        _engine->destroyCameraComponent(_cameraEntity);
        _engine->destroy(_view);
        _engine->destroy(_scene);
        _engine->destroy(_renderer);
        if (_swapChain) {
            _engine->destroy(_swapChain);
            _swapChain = nullptr;
        }

        EntityManager& em = EntityManager::get();
        em.destroy(_cardEntity);
        em.destroy(_lidEntity);
        em.destroy(_baseEntity);
        em.destroy(_glowEntity);
        em.destroy(_cameraEntity);

        Engine::destroy(&_engine);
    }
}

- (void)dealloc {
    [self dispose];
}

@end
