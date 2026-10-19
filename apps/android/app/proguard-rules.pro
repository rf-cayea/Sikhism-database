# Keep rules for R8 (app/build.gradle.kts, release).

# The gurmukhi bindings (packages/gurmukhi, UniFFI) reach libgurmukhi.so through
# JNA, which finds Structure fields, callbacks and Library interfaces by reflection
# and calls back into these classes from native code. Stripped or renamed, they fail
# only at runtime.
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class uniffi.gurmukhi.** { *; }

# JNA's desktop helpers name AWT, which Android does not have.
-dontwarn java.awt.**
