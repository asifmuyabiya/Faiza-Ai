import os
A = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android'


def edit(path, a, b):
    p = os.path.join(A, *path.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:60], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


edit('app/build.gradle', '''android {
    namespace 'com.faiza.ai'
    compileSdk 34
''', '''// Release signing: android/keystore.properties (keep it and keystore/ backed up — without them no update can be signed).
def keystoreProps = new Properties()
def keystoreFile = rootProject.file('keystore.properties')
if (keystoreFile.exists()) keystoreFile.withInputStream { keystoreProps.load(it) }

// The license server. Put the real address in android/gradle.properties: FAIZA_SERVER_URL=https://your-server
def serverUrl = (project.findProperty('FAIZA_SERVER_URL') ?: 'https://CHANGE-ME.example.com').toString()

android {
    namespace 'com.faiza.ai'
    compileSdk 34

    signingConfigs {
        release {
            if (keystoreFile.exists()) {
                storeFile rootProject.file(keystoreProps['storeFile'])
                storePassword keystoreProps['storePassword']
                keyAlias keystoreProps['keyAlias']
                keyPassword keystoreProps['keyPassword']
            }
        }
    }
''')
edit('app/build.gradle', '''        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        debug {
            minifyEnabled false
        }''', '''        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            if (keystoreFile.exists()) signingConfig signingConfigs.release
            buildConfigField "String", "SERVER_URL", "\\"${serverUrl}\\""
        }
        debug {
            minifyEnabled false
            // 10.0.2.2 = this PC from the Android emulator.
            buildConfigField "String", "SERVER_URL", "\\"${project.findProperty('FAIZA_DEBUG_SERVER_URL') ?: 'http://10.0.2.2:5000'}\\""
        }''')

edit('app/src/main/java/com/faiza/ai/FaizaApp.kt', '''        /**
         * TESTING SWITCH: true = every Pro feature unlocked, no trial limit, no license needed.
         * Set to false before selling — that single change restores all license locks.
         */
        const val UNLOCK_ALL_FOR_TESTING = true''', '''        /**
         * TESTING SWITCH: the test (debug) APK has everything unlocked; the release APK you sell is always locked
         * behind the license — nothing to remember before selling.
         */
        const val UNLOCK_ALL_FOR_TESTING = BuildConfig.DEBUG''')
edit('app/src/main/java/com/faiza/ai/FaizaApp.kt', '''        // Server URL - change to your production server
        const val SERVER_URL = "http://10.0.2.2:5000" // 10.0.2.2 = localhost from Android emulator''', '''        /** License server: FAIZA_SERVER_URL in android/gradle.properties (release); the PC for the debug APK. */
        const val SERVER_URL = BuildConfig.SERVER_URL

        /** The release was built without a real server address: licenses can't be checked yet. */
        val serverMissing: Boolean get() = SERVER_URL.contains("CHANGE-ME")''')

edit('gradle.properties', '''android.nonTransitiveRClass=true''', '''android.nonTransitiveRClass=true
# Your live license server (Render / Railway / VPS). Change this, then build the release APK.
FAIZA_SERVER_URL=https://CHANGE-ME.example.com''')

p = os.path.join(A, 'app', 'proguard-rules.pro')
s = open(p, encoding='utf-8').read()
if 'TypeToken' not in s:
    s += '''
# Gson TypeToken generics (lists saved as JSON) and enums read by name
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keepclassmembers enum * { *; }
# CameraX / Vosk / JNA reflection warnings
-dontwarn org.slf4j.**
'''
    open(p, 'w', encoding='utf-8').write(s)
print('ok')
