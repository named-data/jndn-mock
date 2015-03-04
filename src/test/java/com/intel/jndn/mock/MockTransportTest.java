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
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.util.Blob;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Mock the transport class TODO add face.registerPrefix() example
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockTransportTest {

  /**
   * Setup logging
   */
  private static final Logger logger = Logger.getLogger(MockTransportTest.class.getName());

  /**
   * Test sending a Data packet.
   *
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
        logger.fine("Received data");
        assertEquals(data.getContent().buf(), new Blob("...").buf());
      }
    });

    // process the face until one response
    while (count.get() == 0) {
      face.processEvents();
    }

    // check for sent packets
    assertEquals(0, transport.getSentDataPackets().size());
    assertEquals(1, transport.getSentInterestPackets().size());
  }

  /**
   * Test sending multiple Data packets.
   *
   * @throws java.io.IOException
   * @throws net.named_data.jndn.encoding.EncodingException
   */
  @Test
  public void testSendMultipleData() throws IOException, EncodingException {
    MockTransport transport = new MockTransport();
    Face face = new Face(transport, null);

    // setup return data
    Data response1 = new Data(new Name("/a/b/c/1"));
    response1.setContent(new Blob("..."));
    transport.respondWith(response1);
    Data response2 = new Data(new Name("/a/b/c/2"));
    response2.setContent(new Blob("..."));
    transport.respondWith(response2);

    // express interest on the face
    final Counter count = new Counter();
    face.expressInterest(new Interest(new Name("/a/b/c/1")), new OnData() {
      @Override
      public void onData(Interest interest, Data data) {
        count.inc();
        logger.fine("Received data");
        assertEquals(data.getContent().buf(), new Blob("...").buf());
      }
    });

    // process the face until one response received
    while (count.get() == 0) {
      face.processEvents();
    }

    // check for sent packets
    assertEquals(0, transport.getSentDataPackets().size());
    assertEquals(1, transport.getSentInterestPackets().size());

    // express interest again, but this time it should time out because there 
    // is no data left on the wire; the first processEvents() has already 
    // picked it up
    final Counter count2 = new Counter();
    Interest failingInterest = new Interest(new Name("/a/b/c/2"));
    failingInterest.setInterestLifetimeMilliseconds(50);
    face.expressInterest(failingInterest, new OnData() {
      @Override
      public void onData(Interest interest, Data data) {
        count2.inc();
        fail("Should not return data; data should already be cleared");
      }
    }, new OnTimeout() {
      @Override
      public void onTimeout(Interest interest) {
        count2.inc();
        assertTrue(true);
      }
    });

    // process the face until timeout
    while (count2.get() == 0) {
      face.processEvents();
    }

    // check for sent packets
    assertEquals(0, transport.getSentDataPackets().size());
    assertEquals(2, transport.getSentInterestPackets().size());
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
