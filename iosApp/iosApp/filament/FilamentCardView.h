#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * A Metal-backed Filament view that renders the A2 card — a flat, UNLIT, double-sided quad,
 * the exact iOS counterpart of Android's `FilamentRenderer`. It owns a Filament `Engine`
 * (Metal backend) hosted in an internal `MTKView`, whose `MTKViewDelegate` drives the frame
 * loop. Kotlin never sees Filament: it only creates this view, pushes the latest transform,
 * and disposes — so this header stays pure Objective-C and is safe to import from Swift.
 *
 * The C++/Filament/Metal implementation lives entirely in `FilamentCardView.mm`.
 */
@interface FilamentCardView : UIView

/**
 * Push the latest card transform (radians / scale multiplier). Cheap setter — it just stores
 * the values; the next `MTKViewDelegate` draw applies them. Matches the Android model where the
 * renderer reads the freshest snapshot each vsync.
 */
- (void)setYaw:(float)yaw pitch:(float)pitch scale:(float)scale;

/** Stop rendering and tear down the Filament engine and all its resources. */
- (void)dispose;

@end

NS_ASSUME_NONNULL_END
