# R8 rules for release builds. Most libraries ship their own
# consumer rules (Room, Hilt, Compose, kotlinx.serialization, navigation); these cover the rest.

# SQLCipher calls back into these classes from native code by name.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }

# Backups are kotlinx.serialization classes; keep their generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class app.cadence.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.cadence.**$$serializer { *; }

# Stack traces in bug reports should still point at the right lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
