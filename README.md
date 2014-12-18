# An extremely simple, dependency-free JSON encoder.

This package implements a very simple JSON encoder, which has zero dependencies on any other packages, and which can be embedded into your application by simply dropping a single source file into your project, and changing the package name. It also has no configuration: just wrap your objects in JSON classes, and encode to bytes.

The motivation behind this is implementing logging "appenders" for various log frameworks that need to emit JSON, *without* introducing any more dependencies into the application being instrumented, especially when a package might already have a conflicting version of some JSON processor.

Right now it is intended to be only an encoder; if you need a decoder you're likely doing things that can accommodate a "real" JSON processor.