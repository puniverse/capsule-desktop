# `capsule-desktop`

This wrapper-only [Caplet](https://github.com/puniverse/capsule#what-are-caplets) will build native desktop wrappers based on [launch4j](http://launch4j.sourceforge.net/) for the capsule passed on the command line and will then run the native binary for the current platform (if found).

## Additional Capsule manifest entries

  * `GUI`: whether the `GUIMavenCapsule` caplet should be used instead of `MavenCapsule`. The former will launch a basic Swing-based window displaying dependencies retrieval progress and other Capsule runtime messages. In addition, when this option is active, Capsule won't wait for the application JVM process to complete before exiting.
  * `Icon`: the icon to be used for the desktop application.
  * `Platforms`: Native capsules to be built. One or more of: `CURRENT` (default), `macos`, `linux`, `windows`.
  * `Native-Output-Pathname`: output pathname to be used as a basis by the native capsule build(s) (defaults to the capsule pathname itself minus the `.jar` extension). The Windows build will append `.exe` and the Mac OS X one will append `.app` while the Linux one won't add any suffix.
  * `Single-Instance`: if `true` will enforce a single-instance run policy for the native application built by `capsule-desktop`. Currently it only works on Mac OS X and Windows.
  * `Implementation-Vendor`: the provider's or vendor's name to be included in native application metadata. If this attribute is present then `Native-Description` and `Copyright` are mandatory.
