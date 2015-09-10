# Capsule Desktop

This application will build Mac OS X, Linux and/or Windows native executables for a [capsule](https://github.com/puniverse/capsule).

## Usage

`capsule-desktop` is itself distributed as a capsule. Run it with the `-?` option to print the usage information:

```
Option                                  Description          
------                                  -----------          
-?, -h, --help                          Show help            
-c, --capsule <A single capsule                              
  pathname to build native binaries                          
  for>                                                       
-l, --loglevel <Log level (default =                         
  INFO)>                                                     
-m, --macosx                            Build Mac OS X binary
-o, --output <The base output pathname                       
  of built binaries (default = the                           
  capsule pathname)>                                         
-u, --unix                              Build Unix binary    
-w, --windows                           Build Windows binary 
```

`capsule-desktop` can be run both against plain (e.g. "fat") capsules and [Maven-based](https://github.com/puniverse/capsule-desktop) ones.

An [example Java Swing application is available](https://github.com/puniverse/capsule-gui-demo) that can conveniently be used to try out `capsule-desktop`.

The following section explains additional application-specific manifest entries that `capsule-desktop` can use.

## Additional Capsule manifest entries

  * `GUI`: whether the `GUIMavenCapsule` caplet should be used instead of `MavenCapsule`. The former will launch a basic Swing-based window displaying dependencies retrieval progress. In addition, when this option is active, Capsule won't wait for the application JVM process to complete before exiting.
  * `Icon`: the icon to be used for the desktop application.
  * `Single-Instance`: if `true` will enforce a single-instance run policy for the native application built by `capsule-desktop`. It currently only works on Mac OS X and Windows.
  * `Implementation-Vendor`, `Native-Description`, `Copyright` and `Internal-Name`: if any of these native metadata entries is present then the other ones must be present as well. Native metadata is currently only supported on Windows

## License

    Copyright (c) 2014-2015, Parallel Universe Software Co. and Contributors. All rights reserved.

    This program and the accompanying materials are licensed under the terms
    of the Eclipse Public License v1.0 as published by the Eclipse Foundation.

        http://www.eclipse.org/legal/epl-v10.html
