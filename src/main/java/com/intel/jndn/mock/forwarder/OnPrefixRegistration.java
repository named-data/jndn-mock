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
import net.named_data.jndn.ControlParameters;
import net.named_data.jndn.ControlResponse;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.pib.PibImpl;
import net.named_data.jndn.security.tpm.TpmBackEnd;
import net.named_data.jndn.transport.Transport;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handle prefix registration requests from clients to a mock forwarder; must conform to specification outlined in
 * https://redmine.named-data.net/projects/nfd/wiki/RibMgmt.
 *
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class OnPrefixRegistration implements MockForwarder.OnInterestReceived {
  private static final Logger LOGGER = Logger.getLogger(OnPrefixRegistration.class.getName());
  private static final int STATUS_CODE_OK = 200;
  private static final int CONTROL_PARAMETERS_NAME_OFFSET = -5;
  private static final int CONTROL_COMMAND_NAME_OFFSET = 3;
  private final KeyChain keyChain;
  private final MockForwarder.Fib fib;

  public OnPrefixRegistration(KeyChain keyChain, MockForwarder.Fib fib) {
    this.keyChain = keyChain;
    this.fib = fib;
  }

  @Override
  public void in(Interest interest, Transport destinationTransport, Face localFace) {
    LOGGER.info("Received registration request: " + interest.toUri());
    ControlParameters params = decodeParameters(interest);

    MockForwarder.FibEntry entry = new ClientFibEntry(params.getName(),
                                                      (MockTransport) destinationTransport,
                                                      params.getForwardingFlags());
    fib.add(entry);
    LOGGER.info("Added new route " + params.getName() + " to: " + destinationTransport);

    ControlResponse response = encodeResponse(params);

    Data data = new Data();
    data.setName(interest.getName());
    data.setContent(response.wireEncode());
    signResponse(data);

    try {
      localFace.putData(data);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to send registration response", e);
    }
  }

  private ControlParameters decodeParameters(Interest interest) {
    ControlParameters params = new ControlParameters();
    try {
      params.wireDecode(interest.getName().get(CONTROL_PARAMETERS_NAME_OFFSET).getValue());
      params.setFaceId(1);
      params.setOrigin(0);
      params.setCost(0);
    } catch (EncodingException e) {
      throw new IllegalArgumentException("", e);
    }
    return params;
  }

  private ControlResponse encodeResponse(ControlParameters params) {
    ControlResponse response = new ControlResponse();
    response.setStatusCode(STATUS_CODE_OK);
    response.setStatusText("OK");
    response.setBodyAsControlParameters(params);
    return response;
  }

  private void signResponse(Data data) {
    try {
      keyChain.sign(data);
    } catch (SecurityException | KeyChain.Error | TpmBackEnd.Error | PibImpl.Error e) {
      LOGGER.log(Level.FINE, "MockKeyChain signing failed", e);
    }
  }
}
