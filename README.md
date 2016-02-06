# jndn-mock

This project consists of tools for testing NDN applications without network IO. It relies on the [NDN Protocol](https://named-data.net) and its associated [client library](https://github.com/named-data/jndn).

## Install

With Maven, add the following to your POM:
```xml
<dependency>
  <groupId>com.intel.jndn.mock</groupId>
  <artifactId>jndn-mock</artifactId>
  <version>RELEASE</version> <!-- or a specific version -->
</dependency>
```

## Use

`MockFace` is a test tool that can be passed to applications instead of a network IO `Face`; management of the `Face.processEvents()` is still the user's responsibility, though this may change in a future release. For example:
```java
Face face = new MockFace();
face.expressInterest(interest, onData, onTimeout);
face.processEvents();
```

When using the `MockFace`, retrieve statistics about sent/received Interests and Data packets like:
```java
MockFace face = new MockFace();
assertEquals(0, face.sentDatas.size());
assertEquals(0, face.sentInterests.size());

face.expressInterest(interest, onData, onTimeout);
...
face.processEvents();
...
assertEquals(1, face.sentInterests.size());
```

## License

Copyright 2015, Intel Corporation.

This program is free software; you can redistribute it and/or modify it under the terms and conditions of the GNU Lesser General Public License, version 3, as published by the Free Software Foundation.

This program is distributed in the hope it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the [GNU Lesser General Public License](https://github.com/01org/jndn-mock/blob/master/LICENSE) for more details.
