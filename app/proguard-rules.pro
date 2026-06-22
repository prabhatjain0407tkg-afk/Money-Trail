# Keep Kotlin metadata for reflection
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
