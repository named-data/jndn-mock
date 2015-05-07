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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.encoding.ElementListener;
import net.named_data.jndn.encoding.ElementReader;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.transport.Transport;

/**
 * Mock the transport class Example: ...
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockTransport extends Transport {

  public final static int BUFFER_CAPACITY = 8000;
  private static final Logger logger = Logger.getLogger(MockTransport.class.getName());
  protected boolean connected;
  protected ElementReader elementReader;
  private ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
  protected ByteBuffer sentBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
  protected List<Data> sentDataPackets = new ArrayList<>();
  protected List<Interest> sentInterestPackets = new ArrayList<>();

  /**
   * This transport is always local
   *
   * @param connectionInfo
   * @return
   */
  public boolean isLocal(Transport.ConnectionInfo connectionInfo) {
    return true;
  }

  /**
   * Place data in the receive queue; when processEvents() is called, the
   * calling application will receive these bytes.
   *
   * @param response
   */
  public void respondWith(ByteBuffer response) {
    buffer.put(response);
  }

  /**
   * Place data in the receive queue; when processEvents() is called, the
   * calling application will receive this packet.
   *
   * @param response
   */
  public void respondWith(Data response) {
    respondWith(response.wireEncode().buf());
  }

  /**
   * Place data in the receive queue; when processEvents() is called, the
   * calling application will receive this packet.
   *
   * @param request
   */
  public void respondWith(Interest request) {
    respondWith(request.wireEncode().buf());
  }

  /**
   * Inspect the bytes sent using this transport.
   *
   * @return
   */
  public ByteBuffer getSentBuffer() {
    return sentBuffer;
  }

  /**
   * Inspect the list of data packets sent using this transport; the alternative
   * is to inspect getSentBuffer().
   *
   * @return
   */
  public List<Data> getSentDataPackets() {
    return sentDataPackets;
  }

  /**
   * Inspect the list of interest packets sent using this transport; the
   * alternative is to inspect getSentBuffer().
   *
   * @return
   */
  public List<Interest> getSentInterestPackets() {
    return sentInterestPackets;
  }

  /**
   * Clear all sent and to-be-received data
   */
  public void clear() {
    buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    sentBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    sentDataPackets.clear();
    sentInterestPackets.clear();
  }

  /**
   * Mock the connection startup; calls this processEvents().
   *
   * @param connectionInfo
   * @param elementListener
   * @throws IOException
   */
  @Override
  public void connect(Transport.ConnectionInfo connectionInfo,
          ElementListener elementListener) throws IOException {
    logger.fine("Connecting...");
    connected = true;
    elementReader = new ElementReader(elementListener);
  }

  /**
   * Mock sending data to the host; access the data as bytes using
   * getSentBuffer() or as packets with getSentDataPackets().
   *
   * @param data The buffer of data to send. This reads from position() to
   * limit(), but does not change the position.
   * @throws IOException For I/O error.
   */
  @Override
  public void send(ByteBuffer data) throws IOException {
    logger.fine("Sending " + (data.capacity() - data.position()) + " bytes");

    // add to sent bytes
    buffer.put(data);
    data.flip();
    sentBuffer.put(data);
    data.flip();

    // add to sent packets
    byte first = data.get();
    if (first == 5) {
      data.position(0);
      addSentInterest(data);
    } else if (first == 6) {
      data.position(0);
      addSentData(data);
    } else {
      logger.warning("Unknown TLV packet type; cannot parse.");
    }
  }

  /**
   * Helper method to parse Data packets.
   *
   * @param data
   */
  protected void addSentData(ByteBuffer data) {
    Data packet = new Data();
    try {
      packet.wireDecode(data);
      sentDataPackets.add(packet);
    } catch (EncodingException e) {
      logger.warning("Failed to parse bytes into a data packet");
    }
  }

  /**
   * Helper method to parse Interest packets.
   *
   * @param data
   */
  protected void addSentInterest(ByteBuffer data) {
    Interest packet = new Interest();
    try {
      packet.wireDecode(data);
      sentInterestPackets.add(packet);
    } catch (EncodingException e) {
      logger.warning("Failed to parse bytes into an interest packet");
    }
  }

  /**
   * Process any data to receive and clear the input buffer; to mock incoming
   * Data packets, add data to the buffer with respondWith().
   *
   * @throws IOException For I/O error.
   * @throws EncodingException For invalid encoding.
   */
  @Override
  public void processEvents() throws IOException, EncodingException {
    if (!getIsConnected()) {
      logger.warning("Not connnected, aborting...");
      return;
    }

    // trace data sent
    logger.finer(String.format("Processing buffer (position: %s, limit: %s, capacity: %s): %s", buffer.position(), buffer.limit(), buffer.capacity(), Arrays.toString(buffer.array())));

    // pass data up to face
    ByteBuffer temp = copy(buffer);
    temp.flip();
    
    // reset buffer
    buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    
    elementReader.onReceivedData(temp);
  }
  
  /**
   * Copy one buffer to a new buffer, preserving the source buffer's position
   * and limit.
   * @param source the source buffer
   * @return a copied buffer
   */
  private ByteBuffer copy(ByteBuffer source){
    ByteBuffer dest = ByteBuffer.allocate(source.capacity());
    
    int saveLimit = source.limit();
    int savePosition = source.position();
    source.flip();
    
    dest.put(source);
    
    source.limit(saveLimit);
    source.position(savePosition);
    
    return dest;
  }

  /**
   * Check if the transport is connected.
   *
   * @return true if connected.
   */
  @Override
  public boolean getIsConnected() {
    return connected;
  }

  /**
   * Close the connection.
   *
   * @throws IOException For I/O error.
   */
  @Override
  public void close() throws IOException {
    logger.fine("Closing...");
    connected = false;
  }
}
