/*
 * File name: MockTransportTest.java
 * 
 * Purpose: Test the MockTransport
 * 
 * © Copyright Intel Corporation. All rights reserved.
 * Intel Corporation, 2200 Mission College Boulevard,
 * Santa Clara, CA 95052-8119, USA
 */
package com.intel.jndn.mock;

import java.io.IOException;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterest;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.transport.Transport;
import net.named_data.jndn.util.Blob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Mock the transport class Example: ...
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockTransportTest {

  /**
   * Setup logging
   */
  private static final Logger logger = LogManager.getLogger();

  /**
   * Test of getSentBuffer method, of class MockTransport.
   * @throws java.io.IOException
   * @throws net.named_data.jndn.encoding.EncodingException
   */
  @Test
  public void testGetSentBuffer() throws IOException, EncodingException {
    MockTransport transport = new MockTransport();
    Face face = new Face(transport, null);

    // setup return data
    Data response = new Data(new Name("/a/b/c"));
    response.setContent(new Blob("..."));
    transport.respondWith(response);

    // express interest on the face
    final Counter count = new Counter();
    face.expressInterest(new Interest(new Name("/a/b/c")), new OnData() {
      @Override
      public void onData(Interest interest, Data data) {
        count.inc();
        logger.debug("Received data");
        assertEquals(data.getContent().buf(), new Blob("...").buf());
      }
    });
    
    while(count.get() == 0){
      face.processEvents();
    }
  }
  
  class Counter{
    int count = 0;
    public void inc(){
      count++;
    }
    public int get(){
      return count;
    }
  }

  /**
   * Test of getSentBuffer method, of class MockTransport.
   */
//  @Test
//  public void testGetSentBuffer() {
//    MockTransport transport = new MockTransport();
//    Face face = new Face(transport, null);
//
//    // setup return data
//    int typeCode = 101;
//    int[] tlvBytes = new int[]{1, 200};
//
//    // register prefix
//    try {
//      Name prefix = new Name("/a/b/c");
//      logger.info("Registering prefix: " + prefix.toUri());
//      long id = face.registerPrefix(prefix, new TestOnInterest(), new TestOnRegisterFailed());
//      assertTrue(id > 0);
//    } catch (IOException | SecurityException e) {
//      fail("Failed to register prefix.");
//    }
//  }
//  class TestOnData implements OnData {
//
//    @Override
//    public void onData(Interest interest, Data data) {
//      assertEquals(data.getName(), new Name("/a/b/c"));
//      assertEquals(data.getContent(), new Blob("..."));
//    }
//  }
  class TestOnInterest implements OnInterest {

    @Override
    public void onInterest(Name prefix, Interest interest, Transport transport, long registeredPrefixId) {
      Data data = new Data(interest.getName());
      data.setContent(new Blob("..."));
      try {
        transport.send(data.wireEncode().buf());
      } catch (IOException e) {
        fail("Failed to send packet.");
      }
    }
  }

  class TestOnRegisterFailed implements OnRegisterFailed {

    @Override
    public void onRegisterFailed(Name prefix) {
      fail("Failed to register prefix.");
    }
  }
}
