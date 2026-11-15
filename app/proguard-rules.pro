# Règles R8 de l'appli. org.json fait partie d'Android : rien à garder de plus pour l'instant.
-dontwarn org.slf4j.**

# WorkManager crée sa base Room par réflexion : R8 ne doit pas retirer ses constructeurs.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.** { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
