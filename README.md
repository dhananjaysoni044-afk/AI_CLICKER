# AI Clicker Keyboard

Native Android Input Method Editor starter with a timed text-key repeater.

## Features
- Installable Android keyboard selectable as a system input method.
- QWERTY layout, emoji panel, clipboard paste and toolbar.
- Clicker: choose a character or emoji, duration (1–300 seconds), repeat delay (minimum 50 ms).
- Visible Stop control; repeats only into the focused text field and never triggers Send or submits a form.
- Basic offline text utilities.

## Build
GitHub Actions builds a debug APK on every push to main. Download it from the workflow run's Artifacts section.

## Install
1. Install the APK on Android.
2. Open AI Clicker and tap Enable keyboard.
3. Enable AI Clicker in system keyboard settings.
4. Tap Choose AI Clicker and select it.
5. Switch keyboards from the keyboard picker in a text field.

## Limitations
This is an MVP, not a complete Gboard replacement. GIF search, voice typing, predictive suggestions, multilingual layouts, and a true on-device generative AI model are not included. Offline smart tools currently perform local text transformations. Debug APK is not Play Store release-signed.
