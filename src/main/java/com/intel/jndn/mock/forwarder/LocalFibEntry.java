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
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.RegistrationOptions;
import net.named_data.jndn.transport.Transport;

import java.util.logging.Logger;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class LocalFibEntry implements MockForwarder.FibEntry {

  private static final Logger LOGGER = Logger.getLogger(LocalFibEntry.class.getName());
  private final Name prefix;
  private final MockForwarder.OnInterestReceived callback;
  private final Face registrationFace;
  private final RegistrationOptions flags;

  public LocalFibEntry(Name prefix, MockForwarder.OnInterestReceived callback, Face registrationFace, RegistrationOptions flags) {
    this.prefix = prefix;
    this.callback = callback;
    this.registrationFace = registrationFace;
    this.flags = flags;
  }

  public void forward(Interest interest, Transport sourceTransport) {
    LOGGER.info("Forwarding interest on: " + this.callback);
    callback.in(interest, sourceTransport, registrationFace);
  }

  @Override
  public Name getPrefix() {
    return new Name(prefix);
  }

  @Override
  public RegistrationOptions getFlags() {
    return flags;
  }
}
