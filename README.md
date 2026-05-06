# Start 8 Launcher

An Android home-screen launcher inspired by the Windows 8 Start screen. It uses native Java views, reads installed launchable apps, and presents them as horizontally scrolling Metro-style tiles.

## Features

- Registers as an Android `HOME` launcher.
- Windows 8 inspired full-screen start page.
- Separate Windows 8.1 inspired desktop mode.
- Live clock/date header.
- Horizontal tile groups with app icons and labels.
- Search field for installed apps.
- All apps panel.
- Long-press a tile to pin or unpin it.
- Desktop icons for **这台电脑**, **回收站**, and **控制面板**.
- Windowed desktop apps with Windows 8 style title bars.
- Taskbar app buttons for open windows.
- Start button that returns to the Start screen.
- Right-side control center from the system tray.

## Build

Open the folder in Android Studio, let it sync Gradle, then run the `app` configuration on a device or emulator.

If using the command line with Gradle installed:

```sh
gradle :app:assembleDebug
```

After installing, press the Android Home button and choose **Start 8 Launcher** as the home app.
