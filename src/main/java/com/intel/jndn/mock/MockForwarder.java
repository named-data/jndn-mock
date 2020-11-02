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

import com.intel.jndn.mock.forwarder.BufferHandler;
import com.intel.jndn.mock.forwarder.FibImpl;
import com.intel.jndn.mock.forwarder.LocalFibEntry;
import com.intel.jndn.mock.forwarder.OnPrefixRegistration;
import com.intel.jndn.mock.forwarder.PitImpl;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnNetworkNack;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.RegistrationOptions;
import net.named_data.jndn.encoding.WireFormat;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.transport.Transport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * A mock forwarder for use in testing applications without network IO. It does not fully implement NFD functionality
 * but currently does allow registering prefixes (to receive sent interests) and limited forwarding flag support.
 *
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class MockForwarder {
  private static final Logger LOGGER = Logger.getLogger(MockForwarder.class.getName());
  private final KeyChain keyChain;
  private final Name certName;
  private final Fib fib = new FibImpl();
  private final Pit pit = new PitImpl();

  /**
   * Forwarding information base API; use this for recording FIB entries.
   */
  public interface Fib {
    /**
     * @param interest the incoming client interest
     * @return all FIB entries matching the interest
     */
    Collection<FibEntry> find(Interest interest);

    /**
     * @param entry a new FIB entry to add; enables routing to the face (or more correctly, channel) contained in this
     * entry
     */
    void add(FibEntry entry);
  }

  /**
   * Entry in the FIB; use this for forwarding interest packets.
   */
  public interface FibEntry {
    /**
     * @param interest the interest to forward
     * @param sourceTransport the source of the interest for use in the callback
     */
    void forward(Interest interest, Transport sourceTransport);

    /**
     * @return then entry prefix name
     */
    Name getPrefix();

    /**
     * @return the entry flags
     */
    RegistrationOptions getFlags();
  }

  /**
   * Pending interest table API; use this for recording incoming interests.
   */
  public interface Pit {
    /**
     * @param entry the PIT entry to add
     */
    void add(PitEntry entry);

    /**
     * @param interest the incoming interest to match against
     * @return true if the interest matches an entry already in the PIT
     */
    boolean has(Interest interest);

    /**
     * @param name the name to match against
     * @return the PIT entries matching a name, removing them from the PIT
     */
    Collection<PitEntry> extract(Name name);
  }

  /**
   * Entry in the PIT; use this for forwarding data packets.
   */
  public interface PitEntry {
    /**
     * @param data the packet to forward
     */
    void forward(Data data);

    /**
     * @return the interest that first created the entry
     */
    Interest getInterest();

    /**
     * @return true if the entry has been satisfied (has had a matching data forwarded through it)
     */
    boolean isSatisfied();
  }

  /**
   * Mock-specific API for recording the source and destination of incoming interests.
   */
  public interface OnInterestReceived {
    /**
     * Called when the mock forwarder receives incoming interests from a face; see {@link #register(Name,
     * OnInterestReceived, RegistrationOptions)}.
     *
     * @param interest the incoming interest
     * @param destinationTransport the transport that sent the interest; necessary for the forwarder to be able to
     * register prefixes (TODO in the future this should also be a {@link Face}, perhaps rename as remoteFace).
     * @param sourceFace the face receiving the interest; use {@link Face#putData(Data)} to reply
     */
    void in(Interest interest, Transport destinationTransport, Face sourceFace);
  }

  public MockForwarder() {
    try {
      keyChain = MockKeyChain.configure(new Name("/mock/forwarder"));
      certName = keyChain.getDefaultCertificateName();
    } catch (SecurityException e) {
      throw new IllegalStateException("Failed to set up mock prefix registration", e);
    }

    OnPrefixRegistration onPrefixRegistration = new OnPrefixRegistration(keyChain, fib);
    Name registrationPrefix = new Name("/localhost/nfd/rib/register");
    register(registrationPrefix, onPrefixRegistration, new RegistrationOptions());
  }

  public Face connect() {
    MockForwarderFace face = new MockForwarderFace();
    face.setCommandSigningInfo(keyChain, certName);
    LOGGER.info("Connected new face using transport: " + face.getTransport());
    return face;
  }

  public void register(Name prefix, OnInterestReceived callback, RegistrationOptions flags) {
    Face registrationFace = this.connect();
    FibEntry registrationEntry = new LocalFibEntry(prefix, callback, registrationFace, flags);
    fib.add(registrationEntry);
    LOGGER.info("Registered new prefix to receive interests: " + prefix);
  }

  private class MockForwarderFace extends Face implements MeasurableFace {
    final Collection<Interest> sentInterests = new ArrayList<>();
    final Collection<Data> sentDatas = new ArrayList<>();
    final Collection<Interest> receivedInterests = new ArrayList<>();
    final Collection<Data> receivedDatas = new ArrayList<>();

    MockForwarderFace() {
      super(new MockTransport(), null);
      MockTransport transport = (MockTransport) node_.getTransport();
      transport.setOnSendBlock(new BufferHandler(transport, fib, pit));
    }

    Transport getTransport() {
      return node_.getTransport();
    }

    @Override
    public long expressInterest(Interest interest, OnData onData, OnTimeout onTimeout,
                                OnNetworkNack onNetworkNack, WireFormat wireFormat) throws IOException {
      sentInterests.add(interest);
      return super.expressInterest(interest, onData, onTimeout, onNetworkNack, wireFormat);
    }

    @Override
    public long expressInterest(Name name, Interest interestTemplate, OnData onData, OnTimeout onTimeout,
                                OnNetworkNack onNetworkNack, WireFormat wireFormat) throws IOException {
      sentInterests.add(getInterestCopy(name, interestTemplate));
      return super.expressInterest(name, interestTemplate, onData, onTimeout, onNetworkNack, wireFormat);
    }

    @Override
    public void putData(Data data, WireFormat wireFormat) throws IOException {
      sentDatas.add(data);
      super.putData(data, wireFormat);
    }

    @Override
    public Collection<Interest> sentInterests() {
      return Collections.unmodifiableCollection(sentInterests);
    }

    @Override
    public Collection<Data> sentDatas() {
      return Collections.unmodifiableCollection(sentDatas);
    }

    @Override
    public Collection<Interest> receivedInterests() {
      return Collections.unmodifiableCollection(receivedInterests);
    }

    @Override
    public Collection<Data> receivedDatas() {
      return Collections.unmodifiableCollection(receivedDatas);
    }
  }
}
