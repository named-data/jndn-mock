/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.jndn.mock;

import java.io.IOException;
import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterest;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.transport.Transport;
import net.named_data.jndn.util.Blob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockFaceTest {

  /**
   * Setup logging
   */
  private static final Logger logger = LogManager.getLogger();

  /**
   * Test of addResponse method, of class MockFace.
   * @throws java.io.IOException
   * @throws net.named_data.jndn.encoding.EncodingException
   */
  @Test
  public void testWithResponses() throws IOException, EncodingException {
    MockFace face = new MockFace();

    // add response
    Data response = new Data(new Name("/test/with/responses"));
    response.setContent(new Blob("..."));
    face.addResponse(new Name("/test/with/responses"), response);

    // make request
    final Counter count = new Counter();
    logger.info("Express interest: /test/with/responses");
    face.expressInterest(new Interest(new Name("/test/with/responses")), new OnData() {
      @Override
      public void onData(Interest interest, Data data) {
        count.inc();
        logger.debug("Received data");
        assertEquals(data.getContent().buf(), new Blob("...").buf());
      }
    });

    // process face until a response is received
    int allowedLoops = 100;
    while (count.get() == 0 && allowedLoops > 0) {
      allowedLoops--;
      face.processEvents();
    }
    assertEquals(1, count.get());
  }

  /**
   * Test of removeResponse method, of class MockFace.
   * @throws net.named_data.jndn.encoding.EncodingException
   * @throws java.io.IOException
   * @throws net.named_data.jndn.security.SecurityException
   */
  @Test
  public void testWithHandlers() throws EncodingException, IOException, net.named_data.jndn.security.SecurityException {
    MockFace face = new MockFace();

    // add interest handler
    logger.info("Register prefix: /test/with/responses");
    face.registerPrefix(new Name("/test/with/handlers"), new OnInterest() {
      @Override
      public void onInterest(Name prefix, Interest interest, Transport transport, long registeredPrefixId) {
        logger.debug("Received interest, responding: " + interest.getName().toUri());
        Data response = new Data(new Name("/test/with/handlers"));
        response.setContent(new Blob("..."));
        try {
          transport.send(response.wireEncode().buf());
        } catch (IOException e) {
          fail("Failed to send encoded data packet.");
        }
      }
    }, null);

    // make request
    final Counter count = new Counter();
    logger.info("Express interest: /test/with/responses");
    face.expressInterest(new Interest(new Name("/test/with/handlers")), new OnData() {
      @Override
      public void onData(Interest interest, Data data) {
        count.inc();
        logger.debug("Received data");
        assertEquals(data.getContent().buf(), new Blob("...").buf());
      }
    });

    // process faces until a response is received
    int allowedLoops = 100;
    while (count.get() == 0 && allowedLoops > 0) {
      allowedLoops--;
      face.processEvents();
    }
    assertEquals(1, count.get());
  }

  /**
   * Count reference
   */
  class Counter {

    int count = 0;

    public void inc() {
      count++;
    }

    public int get() {
      return count;
    }
  }
}
