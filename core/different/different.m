#import <AppKit/AppKit.h>
#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>


JNIEXPORT void JNICALL Java_processing_core_ThinkDifferent_hideMenuBar
(JNIEnv *env, jclass clazz, jboolean visible, jboolean kioskMode)
{
    NSApplicationPresentationOptions options =
			NSApplicationPresentationHideDock | NSApplicationPresentationHideMenuBar;
	[NSApp setPresentationOptions:options];
}


JNIEXPORT void JNICALL Java_processing_core_ThinkDifferent_showMenuBar
(JNIEnv *env, jclass clazz, jboolean visible, jboolean kioskMode)
{
    [NSApp setPresentationOptions:0];
}


JNIEXPORT void JNICALL Java_processing_core_ThinkDifferent_activateIgnoringOtherApps
(JNIEnv *env, jclass klass)
{
    [NSApp activateIgnoringOtherApps:true];
}
