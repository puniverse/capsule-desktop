# `capsule-desktop`

This **wrapper-only** [Caplet](https://github.com/puniverse/capsule#what-are-caplets) will build native desktop wrappers based on [launch4j](http://launch4j.sourceforge.net/) for the capsule passed on the command line and will then run the native binary for the current platform (if found).

## Usage

Run the `capsule-desktop` JAR with your capsule as its command line argument (use `-Dcapsule.log=verbose` for more information about what Capsule is doing):

``` bash
$> java -Dcapsule.log=verbose -jar capsule-desktop-0.1.jar my-capsule.jar my-capsule-arg1 ...
```

The native application(s) will be built in the same directory as your capsule and the appropriate one for your system will be launched.

An [example Java Swing application is available](https://github.com/puniverse/capsule-gui-demo) that can conveniently be used to try out `capsule-desktop`.

The following section explains additional manifest entries to tailor `capsule-desktop`'s behaviour, including the platforms for which native binaries should be built.

## Additional Capsule manifest entries

  * `GUI`: whether the `GUIMavenCapsule` caplet should be used instead of `MavenCapsule`. The former will launch a basic Swing-based window displaying dependencies retrieval progress and other Capsule runtime messages. In addition, when this option is active, Capsule won't wait for the application JVM process to complete before exiting.
  * `Icon`: the icon to be used for the desktop application.
  * `Platforms`: Native capsules to be built. One or more of: `CURRENT` (default), `macos`, `linux`, `windows`.
  * `Native-Output-Pathname`: output pathname to be used as a basis by the native capsule build(s) (defaults to the capsule pathname itself minus the `.jar` extension). The Windows build will append `.exe` and the Mac OS X one will append `.app` while the Linux one won't add any suffix.
  * `Single-Instance`: if `true` will enforce a single-instance run policy for the native application built by `capsule-desktop`. It currently only works on Mac OS X and Windows.
  * `Implementation-Vendor`: the provider's or vendor's name to be included in the native application's metadata. If this attribute is present then `Native-Description`, `Copyright` and `Internal-Name` are mandatory. Native metadata is currently only supported on Windows.
