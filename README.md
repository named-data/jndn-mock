# jndn-mock

This project consists of tools for testing NDN applications without network IO. It relies on the [NDN Protocol](https://named-data.net) and its associated [client library](https://github.com/named-data/jndn).

## Install
With Maven, add the following to your POM:
```
<dependency>
  <groupId>com.intel.jndn.mock</groupId>
  <artifactId>jndn-mock</artifactId>
  <version>RELEASE</version> <!-- or a specific version -->
</dependency>
```

## Use
`MockFace` and `MockTransport` are test tools that can be passed to applications instead of the typical `Face` and `Transport`. For example:
```
MockTransport transport = new MockTransport();
MockFace face = new MockFace(transport, null);

application.doSomethingOn(face);
assertEquals(0, transport.getSentDataPackets().size());
assertEquals(1, transport.getSentInterestPackets().size());
```

## License
Copyright © 2015, Intel Corporation.

This program is free software; you can redistribute it and/or modify it under the terms and conditions of the GNU Lesser General Public License, version 3, as published by the Free Software Foundation.

This program is distributed in the hope it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the [GNU Lesser General Public License](https://github.com/01org/jndn-mock/blob/master/LICENSE) for more details.
