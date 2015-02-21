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
© Copyright Intel Corporation. Licensed under LGPLv3, see [LICENSE](https://github.com/01org/jndn-mock/blob/master/LICENSE).
