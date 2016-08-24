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
import com.intel.jndn.mock.MockTransport;
import net.named_data.jndn.ForwardingFlags;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.transport.Transport;

import java.util.logging.Logger;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
class ClientFibEntry implements MockForwarder.FibEntry {

  private static final Logger LOGGER = Logger.getLogger(ClientFibEntry.class.getName());
  private final Name prefix;
  private final MockTransport transport;
  private final ForwardingFlags flags;

  ClientFibEntry(Name prefix, MockTransport transport, ForwardingFlags flags) {
    this.prefix = prefix;
    this.transport = transport;
    this.flags = flags;
  }

  @Override
  public void forward(Interest interest, Transport sourceTransport) {
    LOGGER.info("Receiving interest on: " + this.transport);
    transport.receive(interest.wireEncode().buf());
  }

  @Override
  public Name getPrefix() {
    return new Name(prefix);
  }

  @Override
  public ForwardingFlags getFlags() {
    return flags;
  }
}
