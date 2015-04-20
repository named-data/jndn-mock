/*
 * jndn-mock
 * Copyright (c) 2015, Intel Corporation.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms and conditions of the GNU Lesser General Public License,
 * version 3, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 */
package com.intel.jndn.mock;

import java.io.IOException;
import java.util.logging.Logger;
import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterest;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.transport.Transport;
import net.named_data.jndn.util.Blob;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test MockFace functionality
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockFaceTest {

  /**
   * Setup logging
   */
  private static final Logger logger = Logger.getLogger(MockFaceTest.class.getName());

  /**
   * Test setting responses for specific names
   *
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
        logger.fine("Received data");
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
   * Test serving data dynamically with OnInterest handlers
   *
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
        logger.fine("Received interest, responding: " + interest.getName().toUri());
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
        logger.fine("Received data");
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

  // TODO add childInherit test
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

  /**
   * Ensure registering a prefix connects the underlying transport
   *
   * @throws IOException
   * @throws SecurityException
   */
  @Test
  public void testRegistrationConnectsTransport() throws IOException, SecurityException {
    MockFace face = new MockFace();
    assertFalse(face.getTransport().getIsConnected());
    face.registerPrefix(new Name("/fake/prefix"), (OnInterest) null, null);
    assertTrue(face.getTransport().getIsConnected());
  }
}
