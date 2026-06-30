#import "FilamentCardView.h"

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

using namespace filament;
using namespace filament::math;
using namespace utils;

/**
 * iOS A2 Filament renderer. Faithful port of androidMain `FilamentRenderer`:
 *  - flat 1.0 x 1.4 quad in the XY plane, POSITION-only,
 *  - runtime UNLIT + doubleSided material (solid green) built with filamat, targetApi METAL,
 *  - camera at +Z = 3.2 looking at the origin, 45deg vertical FOV,
 *  - transform = Ry(yaw) * Rx(pitch) * scale, applied via TransformManager each frame.
 *
 * The MTKView's delegate (this class) is the vsync loop, equivalent to Android's Choreographer.
 */
@interface FilamentCardView () <MTKViewDelegate>
@end

@implementation FilamentCardView {
    MTKView* _mtkView;

    Engine* _engine;
    Renderer* _renderer;
    Scene* _scene;
    View* _view;
    Camera* _camera;
    SwapChain* _swapChain;

    Material* _material;
    MaterialInstance* _materialInstance;
    VertexBuffer* _vertexBuffer;
    IndexBuffer* _indexBuffer;

    Entity _renderable;
    Entity _cameraEntity;

    // Latest transform pushed from Kotlin; read on the render thread's draw callback.
    float _yaw;
    float _pitch;
    float _scale;
    BOOL _disposed;
}

- (instancetype)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    if (self) {
        _yaw = 0.0f;
        _pitch = 0.0f;
        _scale = 1.0f;
        _disposed = NO;
        [self setupMetalView];
        [self setupFilament];
        [self registerLifecycleObservers];
    }
    return self;
}

#pragma mark - App lifecycle (pause rendering when not visible)

// iOS counterpart of Android's ON_RESUME/ON_PAUSE wiring. Metal must not render to a backgrounded
// layer (the GPU access is revoked → command-buffer errors / termination), so we pause the
// MTKView's internal display link in the background and resume it on return.
- (void)registerLifecycleObservers {
    NSNotificationCenter* nc = [NSNotificationCenter defaultCenter];
    [nc addObserver:self selector:@selector(appDidEnterBackground)
               name:UIApplicationDidEnterBackgroundNotification object:nil];
    [nc addObserver:self selector:@selector(appWillEnterForeground)
               name:UIApplicationWillEnterForegroundNotification object:nil];
}

- (void)appDidEnterBackground {
    if (!_disposed) _mtkView.paused = YES;
}

- (void)appWillEnterForeground {
    if (!_disposed) _mtkView.paused = NO;
}

- (void)setupMetalView {
    _mtkView = [[MTKView alloc] initWithFrame:self.bounds device:MTLCreateSystemDefaultDevice()];
    _mtkView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    _mtkView.enableSetNeedsDisplay = NO;  // continuous, vsync-driven
    _mtkView.paused = NO;
    _mtkView.delegate = self;
    // Match the Android dark-navy clear; MTKView clear is overridden by Filament's clear options.
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

    // SwapChain from the MTKView's CAMetalLayer.
    _swapChain = _engine->createSwapChain((__bridge void*)_mtkView.layer);

    Renderer::ClearOptions clearOptions;
    clearOptions.clear = true;
    clearOptions.clearColor = {0.055f, 0.055f, 0.078f, 1.0f};
    _renderer->setClearOptions(clearOptions);

    _camera->setProjection(45.0, 1.0, 0.1, 100.0, Camera::Fov::VERTICAL);
    _camera->lookAt({0.0, 0.0, 3.2}, {0.0, 0.0, 0.0}, {0.0, 1.0, 0.0});
    _view->setCamera(_camera);
    _view->setScene(_scene);

    [self buildMaterial];
    [self buildGeometry];
}

- (void)buildMaterial {
    filamat::MaterialBuilder::init();
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

    _material = Material::Builder()
        .package(pkg.getData(), pkg.getSize())
        .build(*_engine);
    _materialInstance = _material->createInstance();
    filamat::MaterialBuilder::shutdown();
}

- (void)buildGeometry {
    static const float vertices[] = {
        -0.5f, -0.7f, 0.0f,
         0.5f, -0.7f, 0.0f,
         0.5f,  0.7f, 0.0f,
        -0.5f,  0.7f, 0.0f,
    };
    static const uint16_t indices[] = { 0, 1, 2, 0, 2, 3 };

    _vertexBuffer = VertexBuffer::Builder()
        .vertexCount(4)
        .bufferCount(1)
        .attribute(VertexAttribute::POSITION, 0, VertexBuffer::AttributeType::FLOAT3, 0, 12)
        .build(*_engine);
    _vertexBuffer->setBufferAt(*_engine, 0,
        VertexBuffer::BufferDescriptor(vertices, sizeof(vertices), nullptr));

    _indexBuffer = IndexBuffer::Builder()
        .indexCount(6)
        .bufferType(IndexBuffer::IndexType::USHORT)
        .build(*_engine);
    _indexBuffer->setBuffer(*_engine,
        IndexBuffer::BufferDescriptor(indices, sizeof(indices), nullptr));

    _renderable = EntityManager::get().create();
    RenderableManager::Builder(1)
        .boundingBox({{0, 0, 0}, {1, 1, 1}})
        .geometry(0, RenderableManager::PrimitiveType::TRIANGLES, _vertexBuffer, _indexBuffer, 0, 6)
        .material(0, _materialInstance)
        .culling(false)
        .castShadows(false)
        .receiveShadows(false)
        .build(*_engine, _renderable);

    _engine->getTransformManager().create(_renderable);
    _scene->addEntity(_renderable);
}

#pragma mark - Transform push (from Kotlin)

- (void)setYaw:(float)yaw pitch:(float)pitch scale:(float)scale {
    _yaw = yaw;
    _pitch = pitch;
    _scale = scale;
}

- (void)applyTransform {
    // Ry(yaw) * Rx(pitch) * uniformScale — identical to Android's updateTransform().
    mat4f transform = mat4f::rotation(_yaw, float3{0, 1, 0})
                    * mat4f::rotation(_pitch, float3{1, 0, 0})
                    * mat4f::scaling(float3{_scale, _scale, _scale});
    TransformManager& tcm = _engine->getTransformManager();
    tcm.setTransform(tcm.getInstance(_renderable), transform);
}

#pragma mark - MTKViewDelegate (vsync loop)

- (void)drawInMTKView:(MTKView*)view {
    if (_disposed || _swapChain == nullptr) return;
    [self applyTransform];
    if (_renderer->beginFrame(_swapChain)) {
        _renderer->render(_view);
        _renderer->endFrame();
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
        _engine->destroy(_renderable);
        _engine->destroy(_vertexBuffer);
        _engine->destroy(_indexBuffer);
        _engine->destroy(_materialInstance);
        _engine->destroy(_material);
        _engine->destroyCameraComponent(_cameraEntity);
        _engine->destroy(_view);
        _engine->destroy(_scene);
        _engine->destroy(_renderer);
        if (_swapChain) {
            _engine->destroy(_swapChain);
            _swapChain = nullptr;
        }

        EntityManager& em = EntityManager::get();
        em.destroy(_renderable);
        em.destroy(_cameraEntity);

        Engine::destroy(&_engine);  // sets _engine to nullptr
    }
}

- (void)dealloc {
    [self dispose];
}

@end
