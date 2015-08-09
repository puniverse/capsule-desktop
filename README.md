# `capsule-desktop`

This wrapper-only capsule will build native desktop wrappers for the capsule passed on the command line and will launch the binary for the current platform (if found).

Manifest entries:

  * `GUI`: whether the capsule uses a GUI.
  * `Icon`: the icon to be used for the desktop application.
  * `Native-Platforms`: Native capsules to be built (defaults to current platform). One or more of: CURRENT, macos, linux, windows (currently supported only on Windows systems).
  * `Native-Output-Base`: Output pathname for the native capsule (defaults to the capsule pathname itself minus the .jar extension).
