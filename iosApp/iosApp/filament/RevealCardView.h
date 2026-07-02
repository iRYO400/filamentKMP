#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * A Metal-backed Filament view that renders the box-open reveal — the iOS counterpart of Android's
 * `RevealRenderer`: a procedural box (two half-cubes + a seam-glow quad) plus the holo card quad,
 * all UNLIT. It owns a Filament `Engine` (Metal) in an internal `MTKView` whose delegate drives the
 * frame loop. Kotlin never sees Filament: it pushes the shared, already-eased channels once per
 * frame (see `RevealReducer.visuals`) and this shim maps them onto transforms + material colours.
 *
 * The C++/Filament/Metal implementation lives entirely in `RevealCardView.mm`.
 */
@interface RevealCardView : UIView

/** Invoked once, on the main thread, after the first frame renders (readiness signal). */
@property (nonatomic, copy, nullable) void (^onReady)(void);

/**
 * Push the latest derived reveal channels (cheap setter; applied on the next draw). Box channels
 * are normalized 0..1 where noted; this shim owns the geometry distances. When [inspect] is YES the
 * card follows [yaw]/[pitch]/[cardScale] (A3 physics), else [cardRise]/[spinYaw] (choreography).
 */
- (void)updateShake:(float)shakeX
                bob:(float)bob
              scale:(float)scale
              split:(float)split
               glow:(float)glow
            opacity:(float)opacity
        cardVisible:(BOOL)cardVisible
            inspect:(BOOL)inspect
           cardRise:(float)cardRise
            spinYaw:(float)spinYaw
                yaw:(float)yaw
              pitch:(float)pitch
          cardScale:(float)cardScale;

/** Stop rendering and tear down the Filament engine and all its resources. */
- (void)dispose;

@end

NS_ASSUME_NONNULL_END
