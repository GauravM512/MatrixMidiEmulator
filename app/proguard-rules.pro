# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Remove Android Log calls in release builds.
-assumenosideeffects class android.util.Log {
	public static int v(...);
	public static int d(...);
	public static int i(...);
	public static int w(...);
	public static int e(...);
	public static int wtf(...);
	public static int println(...);
	public static boolean isLoggable(...);
}
