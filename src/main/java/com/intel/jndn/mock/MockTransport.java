/*
 * File name: MockTransport.java
 * 
 * Purpose: Provide testing functionality for running NDN unit tests without
 * connecting to the network or requiring an NFD installed.
 * 
 * © Copyright Intel Corporation. All rights reserved.
 * Intel Corporation, 2200 Mission College Boulevard,
 * Santa Clara, CA 95052-8119, USA
 */
package com.intel.jndn.mock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.encoding.ElementListener;
import net.named_data.jndn.encoding.ElementReader;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.transport.Transport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mock the transport class Example: ...
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockTransport extends Transport {

  public final static int BUFFER_CAPACITY = 8000;
  private static final Logger logger = LogManager.getLogger();
  protected boolean connected;
  protected ElementReader elementReader;
  protected ByteBuffer inputBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
  protected ByteBuffer outputBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
  protected List<Data> outputDataPackets = new ArrayList<>();
  protected List<Interest> outputInterestPackets = new ArrayList<>();

  /**
   * Place data in the receive queue; when processEvents() is called, the
   * calling application will receive these bytes.
   *
   * @param response
   */
  public void respondWith(ByteBuffer response) {
    inputBuffer.put(response);
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
    return outputBuffer;
  }

  /**
   * Inspect the list of data packets sent using this transport; the
   * alternative is to inspect getSentBuffer().
   *
   * @return
   */
  public List<Data> getSentDataPackets() {
    return outputDataPackets;
  }

  /**
   * Inspect the list of interest packets sent using this transport; the
   * alternative is to inspect getSentBuffer().
   *
   * @return
   */
  public List<Interest> getSentInterestPackets() {
    return outputInterestPackets;
  }
  
  /**
   * Clear all sent and to-be-received data
   */
  public void clear() {
    inputBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    outputBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    outputDataPackets.clear();
    outputInterestPackets.clear();
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
    logger.debug("Connecting...");
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
    logger.debug("Sending " + (data.capacity() - data.position()) + " bytes");

    // add to sent bytes
    outputBuffer.put(data);
    data.flip();

    // add to sent packets
    byte first = data.get();
    if (first == 5) {
      data.position(0);
      addInterest(data);
    } else if (first == 6) {
      data.position(0);
      addData(data);
    } else {
      logger.warn("Unknown TLV packet type; cannot parse");
    }
  }

  /**
   * Helper method to parse Data packets.
   * 
   * @param data 
   */
  protected void addData(ByteBuffer data) {
    Data packet = new Data();
    try {
      packet.wireDecode(data);
      outputDataPackets.add(new Data());
    } catch (EncodingException e) {
      logger.warn("Failed to parse bytes into a data packet");
    }
  }

  /**
   * Helper method to parse Interest packets.
   * 
   * @param data 
   */
  protected void addInterest(ByteBuffer data) {
    Interest packet = new Interest();
    try {
      packet.wireDecode(data);
      outputDataPackets.add(new Data());
    } catch (EncodingException e) {
      logger.warn("Failed to parse bytes into an interest packet");
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
      logger.warn("Not connnected, aborting...");
      return;
    }

    // trace data sent
    logger.trace(String.format("Sending buffer (position: %s, limit: %s, capacity: %s): %s", inputBuffer.position(), inputBuffer.limit(), inputBuffer.capacity(), Arrays.toString(inputBuffer.array())));

    // pass data up to face
    inputBuffer.limit(inputBuffer.position());
    inputBuffer.position(0);
    elementReader.onReceivedData(inputBuffer);

    // reset buffer
    inputBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
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
    logger.debug("Closing...");
    connected = false;
  }
}
