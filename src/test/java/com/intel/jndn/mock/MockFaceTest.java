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

import net.named_data.jndn.*;
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
    final TestCounter count = new TestCounter();
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
    face.registerPrefix(new Name("/test/with/handlers"), new OnInterestCallback() {
      @Override
      public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
        logger.fine("Received interest, responding: " + interest.getName().toUri());
        Data response = new Data(new Name("/test/with/handlers"));
        response.setContent(new Blob("..."));
        try {
          face.putData(response);
        } catch (IOException e) {
          fail("Failed to send encoded data packet.");
        }
      }
    }, null);

    // make request
    final TestCounter count = new TestCounter();
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
  
  /**
   * Test that interest filters work as expected
   */
  @Test
  public void testInterestFilters() throws IOException, SecurityException, EncodingException {
    MockFace face = new MockFace();
    
    final TestCounter count = new TestCounter();
    face.setInterestFilter(new InterestFilter("/a/b"), new OnInterestCallback() {
      @Override
      public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
        count.inc();
      }
    });
    
    face.expressInterest(new Interest(new Name("/a/b")).setInterestLifetimeMilliseconds(100), null);
    face.processEvents();
    
    assertEquals(1, count.get());
  }
  
  @Test
  public void testResponseFromInsideElementReader() throws IOException, SecurityException, EncodingException{
    MockFace face = new MockFace();
    face.setInterestFilter(new InterestFilter("/a/b"), new OnInterestCallback() {
      @Override
      public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
        try {
          face.putData(new Data(interest.getName()).setContent(new Blob("......")));
        } catch (IOException ex) {
          fail("Failed to put data.");
        }
      }
    });
    
    final TestCounter count = new TestCounter();
    face.expressInterest(new Interest(new Name("/a/b/c")), new OnData() {
      @Override
      public void onData(Interest interest, Data data) {
        logger.info("Data returned: " + data.getContent().toString());
        count.inc();
      }
    });
    assertEquals(0, count.get());
    
    face.processEvents();
    face.processEvents(); // the second processEvents() is required because the InterestFilter sends data from within the first processEvents loop
    assertEquals(1, count.get());
  }
}
