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
import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.encoding.TlvWireFormat;
import net.named_data.jndn.encoding.tlv.Tlv;
import net.named_data.jndn.encoding.tlv.TlvDecoder;
import net.named_data.jndn.transport.Transport;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class BufferHandler implements MockTransport.OnSendBlockSignal {

  private static final Logger LOGGER = Logger.getLogger(BufferHandler.class.getName());
  private final MockTransport transport;
  private final MockForwarder.Fib fib;
  private final MockForwarder.Pit pit;

  public BufferHandler(MockTransport transport, MockForwarder.Fib fib, MockForwarder.Pit pit) {
    this.transport = transport;
    this.fib = fib;
    this.pit = pit;
  }

  @Override
  public void emit(ByteBuffer buffer) {
    try {
      if (isInterest(buffer) || isData(buffer)) {
        TlvDecoder decoder = new TlvDecoder(buffer);
        if (decoder.peekType(Tlv.Interest, buffer.remaining())) {
          Interest interest = new Interest();
          interest.wireDecode(buffer, TlvWireFormat.get());
          forward(interest, transport);
        } else if (decoder.peekType(Tlv.Data, buffer.remaining())) {
          Data data = new Data();
          data.wireDecode(buffer, TlvWireFormat.get());
          forward(data);
        }
      } else {
        LOGGER.warning("Received an unknown packet");
      }
    } catch (EncodingException e) {
      LOGGER.log(Level.INFO, "Failed to decodeParameters incoming packet", e);
    }
  }

  private boolean isInterest(ByteBuffer buffer) {
    return buffer.get(0) == Tlv.Interest;
  }

  private boolean isData(ByteBuffer buffer) {
    return buffer.get(0) == Tlv.Data;
  }

  private void forward(Interest interest, Transport transport) {
    if (pit.has(interest)) {
      LOGGER.info("Already seen interest, swallowing: " + interest.toUri());
      return;
    }

    LOGGER.info("Adding interest to PIT: " + interest.toUri());
    pit.add(new PitEntryImpl(interest, (MockTransport) transport));

    LOGGER.info("Forwarding interest: " + interest.toUri());
    for (MockForwarder.FibEntry entry : fib.find(interest)) {
      entry.forward(interest, transport);
    }
  }

  private void forward(Data data) {
    Collection<MockForwarder.PitEntry> found = pit.extract(data.getName());
    LOGGER.log(Level.INFO, "Found {0} pending interests", found.size());

    for (MockForwarder.PitEntry pendingInterest : found) {
      pendingInterest.forward(data);
    }
  }
}
