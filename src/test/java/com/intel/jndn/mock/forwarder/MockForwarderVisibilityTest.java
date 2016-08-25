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

package com.intel.jndn.mock.forwarder;

import com.intel.jndn.mock.MockForwarder;
import net.named_data.jndn.Face;
import net.named_data.jndn.ForwardingFlags;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.transport.Transport;
import org.junit.Test;

/**
 * Ensure MockForwarder methods are visible publicly; does not test functionality
 *
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class MockForwarderVisibilityTest {

  @Test
  public void ensureVisibility() {
    MockForwarder forwarder = new MockForwarder();

    forwarder.connect();

    forwarder.register(new Name("/a/b/c"), new MockForwarder.OnInterestReceived() {
      @Override
      public void in(Interest interest, Transport destinationTransport, Face sourceFace) {
        // do nothing
      }
    }, new ForwardingFlags());
  }
}