# AI Photo Studio (Java)

A starter Android Studio image editor written in Java. Minimum Android version: Android 12 (API 31).

## Included
- Pick an image using Android's system photo picker/content picker
- Brightness and contrast controls
- Black-and-white and sepia filters
- Reset to the original image
- Save edited JPEGs to `Pictures/AI Photo Studio`

## Open and build
1. Extract this ZIP file.
2. Open the `AIPhotoStudio` folder in Android Studio.
3. Use JDK 17 when prompted and allow Gradle sync to finish.
4. Connect an Android 12+ device or start an emulator.
5. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. The debug APK is normally created at `app/build/outputs/apk/debug/app-debug.apk`.

## Notes
- This is a basic image editor, not a complete AI editor.
- Automatic removal of people, background replacement, and body reshaping are not implemented; these need dedicated on-device ML models or a cloud image-editing service.
- The project uses Android Gradle Plugin 8.7.3 and compile SDK 35. Android Studio may offer a compatible Gradle/JDK setup during import.
