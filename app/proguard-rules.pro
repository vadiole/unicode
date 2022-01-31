# Default PV proguard file - use it and abuse it if its useful.

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclassmembers
-verbose

# Optimization is turned off by default. Dex does not like code run
# through the ProGuard optimize and preverify steps (and performs some of these optimisations on its own).
-dontpreverify

# Note that if you want to enable optimization, you cannot just
# include optimization flags in your own project configuration file;
# instead you will need to point to the
# "proguard-android-optimize.txt" file instead of this one from your
# project.properties file.

##########
# Android:
##########

##########
# Parcelables: Mantain the parcelables working
##########
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

#############
# Serializables
#############
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
##########
# Kotlin
##########
-dontwarn kotlin.**
-dontnote kotlin.**
-dontwarn kotlinx.atomicfu.AtomicBoolean
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit
#Ignore null checks at runtime
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}
##########
# Coroutines
##########
# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

########################################
# External Libraries
########################################

##########
# Android architecture components: Lifecycle ( https://issuetracker.google.com/issues/62113696 )
##########

# HttpClient Legacy (Ignore) - org.apache.http legacy
#############
-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
##########

##########
# JSR 305 annotations are for embedding nullability information.
##########
-dontwarn javax.annotation.**