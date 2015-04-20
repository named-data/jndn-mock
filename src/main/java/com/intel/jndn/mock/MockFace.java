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
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Logger;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.ForwardingFlags;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.Node;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterest;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.encoding.WireFormat;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.transport.Transport;

/**
 * <p>
 * Use the MockTransport to mock sending data over the network. Allows for
 * testing NDN applications while simulating network IO. TODO implement longest
 * prefix match here for comprehensive testing.
 * </p>
 * <p>
 * Usage
 * </p>
 * <pre><code>
 * Face mockFace = new MockFace();
 * mockFace.registerPrefix(...); // as usual
 * mockFace.expressInterest(...); // as usual
 *
 * // also, simply inject a response that will be returned for an expressed interest
 * mockFace.addResponse(interestName, data);
 * </pre></code>
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockFace extends FaceExtension {

  private static final Logger logger = Logger.getLogger(MockFace.class.getName());
  private final Node node_;
  HashMap<String, Data> responseMap = new HashMap<>();
  HashMap<Long, MockOnInterestHandler> handlerMap = new HashMap<>();
  long lastRegisteredId = 0;

  /**
   * Create a new Face to mock communication over the network; all packets are
   * maintained in memory
   */
  public MockFace() {
    node_ = new Node(new MockTransport(), null);
  }

  /**
   * @return a reference to the current MockTransport
   */
  public MockTransport getTransport() {
    return (MockTransport) node_.getTransport();
  }

  /**
   * Add a response Data packet to send immediately when an Interest with a
   * matching name is received; will continue to respond with the same packet
   * over multiple requests. This will preempt any registered OnInterest
   * handlers.
   *
   * @param name
   * @param data
   */
  public void addResponse(Name name, Data data) {
    logger.fine("Added response for: " + name.toUri());
    responseMap.put(name.toUri(), data);
  }

  /**
   * Stop sending a response for the given name.
   *
   * @param name
   */
  public void removeResponse(Name name) {
    logger.fine("Removed response for: " + name.toUri());
    responseMap.remove(name);
  }

  /**
   * Handle incoming Interest packets; when an Interest is expressed through
   * expressInterest(), this will run to determine if: 1) any responses have
   * been registered or 2) if any OnInterest handlers have been registered. If
   * one of these two succeeds, this method then re-directs the Interest from
   * traveling down the network stack and returns data.
   *
   * @param interest
   */
  protected void handleIncomingRequests(Interest interest) {
    String interestName = interest.getName().toUri();
    long registeredPrefixId = findRegisteredHandler(interest);
    // check if response registered
    if (responseMap.containsKey(interestName)) {
      logger.fine("Found response for: " + interestName);
      Data data = responseMap.get(interestName);
      ((MockTransport) node_.getTransport()).respondWith(data);
    } // check if handler registered
    else if (registeredPrefixId != -1) {
      logger.fine("Found handler for: " + interestName);
      MockOnInterestHandler handler = handlerMap.get(findRegisteredHandler(interest));
      handler.signal(interest, registeredPrefixId);
    } // log failure
    else {
      logger.warning("No response found for interest (aborting): " + interestName);
    }
  }

  /**
   * Find a handler that matches the incoming interest; currently, the only
   * flags supported are the ChildInherit flags.
   *
   * @param interest
   * @return
   */
  protected long findRegisteredHandler(Interest interest) {
    for (Entry<Long, MockOnInterestHandler> entry : handlerMap.entrySet()) {
      MockOnInterestHandler handler = entry.getValue();
      if (handler.flags.getChildInherit() && handler.prefix.match(interest.getName())) {
        return entry.getKey();
      }
      if (handler.prefix.equals(interest.getName())) {
        return entry.getKey();
      }
    }
    return -1;
  }

  /**
   * Helper class for holding references to OnInterest handlers
   */
  class MockOnInterestHandler {

    Name prefix;
    OnInterest onInterest;
    OnInterestCallback onInterestCallback;
    ForwardingFlags flags;

    public MockOnInterestHandler(Name prefix, OnInterest onInterest, ForwardingFlags flags) {
      this.prefix = prefix;
      this.onInterest = onInterest;
      this.flags = flags;
    }
    
    public MockOnInterestHandler(Name prefix, OnInterestCallback onInterestCallback, ForwardingFlags flags) {
      this.prefix = prefix;
      this.onInterestCallback = onInterestCallback;
      this.flags = flags;
    }
    
    public void signal(Interest interest, long registeredPrefixId){
      if(onInterest != null){
        onInterest.onInterest(prefix, interest, node_.getTransport(), registeredPrefixId);
      }
      if(onInterestCallback != null){
        onInterestCallback.onInterest(prefix, interest, MockFace.this, registeredPrefixId, null);
      }
    }
  }

  /**
   * Send the Interest through the transport, read the entire response and call
   * onData(interest, data).
   *
   * @param interest The Interest to send. This copies the Interest.
   * @param onData When a matching data packet is received, this calls
   * onData.onData(interest, data) where interest is the interest given to
   * expressInterest and data is the received Data object. NOTE: You must not
   * change the interest object - if you need to change it then make a copy.
   * @param onTimeout If the interest times out according to the interest
   * lifetime, this calls onTimeout.onTimeout(interest) where interest is the
   * interest given to expressInterest. If onTimeout is null, this does not use
   * it.
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The pending interest ID which can be used with
   * removePendingInterest.
   * @throws IOException For I/O error in sending the interest.
   */
  @Override
  public long expressInterest(Interest interest, OnData onData, OnTimeout onTimeout,
          WireFormat wireFormat) throws IOException {
    long id = node_.expressInterest(interest, onData, onTimeout, wireFormat);
    handleIncomingRequests(interest);
    return id;
  }

  /**
   * Register prefix with the connected NDN hub and call onInterest when a
   * matching interest is received. If you have not called
   * setCommandSigningInfo, this assumes you are connecting to NDNx. If you have
   * called setCommandSigningInfo, this first sends an NFD registration request,
   * and if that times out then this sends an NDNx registration request. If you
   * need to register a prefix with NFD, you must first call
   * setCommandSigningInfo.
   *
   * @param prefix A Name for the prefix to register. This copies the Name.
   * @param onInterest When an interest is received which matches the name
   * prefix, this calls onInterest.onInterest(prefix, interest, transport,
   * registeredPrefixId). NOTE: You must not change the prefix object - if you
   * need to change it then make a copy.
   * @param onRegisterFailed If register prefix fails for any reason, this calls
   * onRegisterFailed.onRegisterFailed(prefix).
   * @param flags The flags for finer control of which interests are forwarded
   * to the application.
   * @param wireFormat A WireFormat object used to encode the message.
   * @return The lastRegisteredId prefix ID which can be used with
   * removeRegisteredPrefix.
   * @throws IOException For I/O error in sending the registration request.
   * @throws SecurityException If signing a command interest for NFD and cannot
   * find the private key for the certificateName.
   */
  @Override
  public long registerPrefix(Name prefix, OnInterest onInterest, OnRegisterFailed onRegisterFailed,
          ForwardingFlags flags, WireFormat wireFormat) throws IOException, net.named_data.jndn.security.SecurityException {
    // since we don't send an Interest, ensure the transport is connected
    if (!getTransport().getIsConnected()) {
      getTransport().connect(node_.getConnectionInfo(), node_);
    }

    lastRegisteredId++;
    handlerMap.put(lastRegisteredId, new MockOnInterestHandler(prefix, onInterest, flags));
    return lastRegisteredId;
  }

  @Override
  public long registerPrefix(Name prefix, OnInterestCallback onInterest, OnRegisterFailed onRegisterFailed, ForwardingFlags flags, WireFormat wireFormat) throws IOException, SecurityException {
    // since we don't send an Interest, ensure the transport is connected
    if (!getTransport().getIsConnected()) {
      getTransport().connect(node_.getConnectionInfo(), node_);
    }

    lastRegisteredId++;
    handlerMap.put(lastRegisteredId, new MockOnInterestHandler(prefix, onInterest, flags));
    return lastRegisteredId;
  }

  /**
   * Process any packets to receive and call callbacks such as onData,
   * onInterest or onTimeout. This returns immediately if there is no data to
   * receive. This blocks while calling the callbacks. You should repeatedly
   * call this from an event loop, with calls to sleep as needed so that the
   * loop doesn’t use 100% of the CPU. Since processEvents modifies the pending
   * interest table, your application should make sure that it calls
   * processEvents in the same thread as expressInterest (which also modifies
   * the pending interest table). This may throw an exception for reading data
   * or in the callback for processing the data. If you call this from an main
   * event loop, you may want to catch and log/disregard all exceptions.
   */
  @Override
  public void processEvents() throws IOException, EncodingException {
    // Just call Node's processEvents.
    node_.processEvents();
  }

  @Override
  public void removeRegisteredPrefix(long registeredPrefixId) {
    handlerMap.remove(registeredPrefixId);
  }

  @Override
  public long setInterestFilter(InterestFilter filter, OnInterestCallback onInterest) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void unsetInterestFilter(long interestFilterId) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void putData(Data data, WireFormat wireFormat) throws IOException {
    node_.putData(data, wireFormat);
  }

  @Override
  public void send(ByteBuffer encoding) throws IOException {
    node_.send(encoding);
  }

  @Override
  public boolean isLocal() throws IOException {
    return true;
  }

  @Override
  public void shutdown() {
    node_.shutdown();
  }

}
