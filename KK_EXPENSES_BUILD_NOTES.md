# KK Expenses — modified source build

Changes made: app display name KK Expenses (all localized app_name strings); separate Android application ID org.kkexpenses.app; custom launcher artwork; original source, accounting logic, and migrations retained.

Build prerequisites: Android SDK and Java 21, plus network access to Gradle dependencies.

Try: `./gradlew :myExpenses:assembleExternDebug`

**Important**: This is a source customization, not a completed modernization of the UI or the 14 proposed features. Google Drive encrypted manual backup enhancements are not implemented. The app ID change means private data from original My Expenses will not automatically transfer. Some integrations or deep links may need updates for the new ID. No APK has been built or tested in this environment.

License: preserve upstream GPLv3 notices and comply with source distribution requirements.
