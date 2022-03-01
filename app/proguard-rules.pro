-renamesourcefileattribute SourceFile
-verbose
-optimizationpasses 5
-overloadaggressively
-allowaccessmodification

-repackageclasses ""
-renamesourcefileattribute ""

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
  public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
  public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
  public static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
  public static void checkNotNull(java.lang.Object);
  public static void checkNotNull(java.lang.Object, java.lang.String);
  public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
  public static void checkNotNullParameter(java.lang.Object, java.lang.String);
  public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
  public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
  public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

-dontwarn kotlin.**
-dontnote kotlin.**
-dontwarn kotlinx.atomicfu.AtomicBoolean
-keepclassmembers class **$WhenMappings {
    <fields>;
}
# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}