/*
 * jndn-mock
 * Copyright (c) 2016, Intel Corporation.
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

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnRegisterSuccess;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.util.Blob;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test MockFace functionality.
 */
public class MockFaceTest {
  private static final Logger LOG = Logger.getLogger(MockFaceTest.class.getName());
  private MockFace face;
  private int counter;
  private Data recvData = null;
  private boolean isTimeout = false;
  private Exception exception = null;

  /////////////////////////////////////////////////////////////////////////////

  @Before
  public void setup() throws SecurityException {
    face = new MockFace();
    counter = 0;
    recvData = null;
    isTimeout = false;
    exception = null;
  }

  @Test
  public void testExpressingAnInterest() throws IOException, EncodingException, InterruptedException {
    // make request
    expressInterest("/test/with/responses");

    run(2);

    // add response (after face is connectd)
    Data response = new Data(new Name("/test/with/responses"));
    response.setContent(new Blob("..."));
    face.receive(response);

    run(20);

    assertNotNull(recvData);
    assertEquals(isTimeout, false);
    assertEquals(recvData.getName().toString(), "/test/with/responses");
    assertEquals(recvData.getContent().buf(), new Blob("...").buf());
  }

  @Test
  public void testExpressingAnInterestAfterConfiguration() throws IOException, EncodingException, InterruptedException {
    // add response (before face is connected)
    Data response = new Data(new Name("/test/with/responses"));
    response.setContent(new Blob("..."));
    face.receive(response);

    // make request
    expressInterest("/test/with/responses");

    run(20);

    assertNotNull(recvData);
    assertEquals(isTimeout, false);
    assertEquals(recvData.getName().toString(), "/test/with/responses");
    assertEquals(recvData.getContent().buf(), new Blob("...").buf());
  }

  @Test
  public void testInterestTimeouts() throws IOException, EncodingException, InterruptedException {
    // make request
    expressInterest("/some/name");

    run(20);

    assertEquals(recvData, null);
    assertEquals(isTimeout, true);
  }

  @Test
  public void testPrefixRegistration() throws IOException, SecurityException, EncodingException, InterruptedException {
    class State {
      boolean regFailed = false;
      boolean regSucceed = false;
    }
    final State state = new State();

    LOG.info("Register prefix: /test/with/handlers");
    face.registerPrefix(new Name("/test/with/handlers"), new OnInterestCallback() {
      @Override
      public void onInterest(final Name prefix, final Interest interest, final Face face, final long interestFilterId,
                             final InterestFilter filter) {
        LOG.info("Received interest, responding: " + interest.getName().toUri());
        Data response = new Data(new Name("/test/with/handlers"));
        response.setContent(new Blob("..."));
        try {
          face.putData(response);
        } catch (IOException e) {
          exception = e;
        }
        counter++;
      }
    }, new OnRegisterFailed() {
      @Override
      public void onRegisterFailed(final Name prefix) {
        LOG.info("Prefix registration fails: " + prefix);
        state.regFailed = true;
        counter++;
      }
    }, new OnRegisterSuccess() {
      @Override
      public void onRegisterSuccess(final Name prefix, final long registeredPrefixId) {
        LOG.info("Prefix registration succeed: " + prefix);
        state.regSucceed = true;
        counter++;
      }
    });

    run(100, 1);
    assertTrue(state.regSucceed);
    assertFalse(state.regFailed);

    // make request
    face.receive(new Interest(new Name("/test/with/handlers")));

    run(100, 2);

    assertNull(exception);

    assertEquals(face.sentData.size(), 1);
    assertFalse(isTimeout);
    assertEquals("/test/with/handlers", face.sentData.get(0).getName().toString());
    assertEquals(new Blob("...").buf(), face.sentData.get(0).getContent().buf());
  }

  @Test
  public void testThatTransportConnectsOnPrefixRegistration() throws IOException, SecurityException {
    assertFalse(face.getTransport().getIsConnected());
    face.registerPrefix(new Name("/fake/prefix"), null, null, (OnRegisterSuccess) null);
    assertTrue(face.getTransport().getIsConnected());
  }

  @Test
  public void testInterestFilters() throws IOException, SecurityException, EncodingException, InterruptedException {
    class State {
      boolean regFailed = false;
      boolean regSucceed = false;
    }
    final State state = new State();

    // connect callback
    face.registerPrefix(new Name("/fake/prefix"), null, new OnRegisterFailed() {
      @Override
      public void onRegisterFailed(final Name prefix) {
        state.regFailed = true;
        counter++;
      }
    }, new OnRegisterSuccess() {
      @Override
      public void onRegisterSuccess(final Name prefix, final long registeredPrefixId) {
        state.regSucceed = true;
        counter++;
      }
    });

    // set filter
    face.setInterestFilter(new InterestFilter("/a/b"), new OnInterestCallback() {
      @Override
      public void onInterest(final Name prefix, final Interest interest, final Face face, final long interestFilterId,
                             final InterestFilter filter) {
        counter++;
      }
    });

    face.receive(new Interest(new Name("/a/b")).setInterestLifetimeMilliseconds(100));

    run(10, 2);

    assertEquals(2, counter);
    assertTrue(state.regSucceed);
    assertFalse(state.regFailed);
  }

  @Test
  public void testMockWithoutPacketLogging() throws Exception {
    face = new MockFace(new MockFace.Options().setEnablePacketLogging(false));

    // make request
    expressInterest("/test/with/responses");
    run(2);

    assertEquals(0, face.sentInterests.size());
  }

  @Test
  public void testMockWithoutMockRegistrationReply() throws Exception {
    face = new MockFace(new MockFace.Options().setEnableRegistrationReply(false));

    class State {
      boolean regFailed = false;
      boolean regSucceed = false;
    }
    final State state = new State();

    LOG.info("Register prefix: /test/with/handlers");
    face.registerPrefix(new Name("/test/with/handlers"), new OnInterestCallback() {
      @Override
      public void onInterest(final Name prefix, final Interest interest, final Face face, final long interestFilterId,
                             final InterestFilter filter) {
        LOG.info("Received interest, responding: " + interest.getName().toUri());
        Data response = new Data(new Name("/test/with/handlers"));
        response.setContent(new Blob("..."));
        try {
          face.putData(response);
        } catch (IOException e) {
          exception = e;
        }
        counter++;
      }
    }, new OnRegisterFailed() {
      @Override
      public void onRegisterFailed(final Name prefix) {
        LOG.info("Prefix registration fails: " + prefix);
        state.regFailed = true;
        counter++;
      }
    }, new OnRegisterSuccess() {
      @Override
      public void onRegisterSuccess(final Name prefix, final long registeredPrefixId) {
        LOG.info("Prefix registration succeed: " + prefix);
        state.regSucceed = true;
        counter++;
      }
    });

    run(100, 1);
    assertFalse(state.regSucceed);
    assertTrue(state.regFailed);
  }

  /////////////////////////////////////////////////////////////////////////////

  private void run(final int limit, final int maxCounter) throws IOException, EncodingException, InterruptedException {
    // process face until a response is received
    int allowedLoops = limit;
    while (counter < maxCounter && allowedLoops > 0) {
      allowedLoops--;
      face.processEvents();
      Thread.sleep(100);
    }
  }

  private void run(final int limit) throws IOException, EncodingException, InterruptedException {
    run(limit, 1);
  }

  private void expressInterest(final String name) throws IOException {
    LOG.info("Express interest: " + name);
    face.expressInterest(new Interest(new Name(name)).setInterestLifetimeMilliseconds(1000), new OnData() {
      @Override
      public void onData(final Interest interest, final Data data) {
        counter++;
        LOG.fine("Received data");
        recvData = data;
      }
    }, new OnTimeout() {
      @Override
      public void onTimeout(final Interest interest) {
        LOG.fine("Received timeout");
        counter++;
        isTimeout = true;
      }
    });
  }
}
