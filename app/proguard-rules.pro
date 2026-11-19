# Project-specific R8 rules. Libraries (AndroidX, Material, Room) ship their own.
# Keep line numbers so crash stack traces from release builds stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
