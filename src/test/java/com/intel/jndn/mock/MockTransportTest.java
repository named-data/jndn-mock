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
import net.named_data.jndn.encoding.EncodingException;
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
   * Test sending Data
   * @throws java.io.IOException
   * @throws net.named_data.jndn.encoding.EncodingException
   */
  @Test
  public void testSendData() throws IOException, EncodingException {
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
  
  /**
   * Count reference
   */
  class Counter{
    int count = 0;
    public void inc(){
      count++;
    }
    public int get(){
      return count;
    }
  }
}
