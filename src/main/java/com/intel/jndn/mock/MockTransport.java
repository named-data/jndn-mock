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
import java.util.List;
import net.named_data.jndn.Data;
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
  protected ByteBuffer inputBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
  protected ByteBuffer outputBuffer = ByteBuffer.allocate(BUFFER_CAPACITY);
  protected ElementReader elementReader;
  protected List<Data> outputPackets = new ArrayList<>();

  /**
   * Retrieve sent bytes
   *
   * @return
   */
  public ByteBuffer getSentBuffer() {
    return outputBuffer;
  }

  /**
   * 
   */
  public void clear() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  /**
   * 
   * @return 
   */
  public List<Data> getSentDataPackets() {
    return outputPackets;
  }

  /**
   * Retrieve received bytes; this is mocked by adding bytes to the buffer with
   * respondWith()
   *
   * @return
   */
  public ByteBuffer getReceivedBuffer() {
    return inputBuffer;
  }

  /**
   *
   * @param response
   */
  public void respondWith(ByteBuffer response) {
    inputBuffer.put(response);
  }

  /**
   *
   * @param response
   */
  public void respondWith(Data response) {
    respondWith(response.wireEncode().buf());
  }

  /**
   * Mock connection
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
   * getSentBuffer() or as packets with getSentDataPackets()
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
    try {
      Data packet = new Data();
      packet.wireDecode(data);
      outputPackets.add(new Data());
    } catch (EncodingException e) {
      logger.warn("Failed to parse bytes into a data packet");
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

    // pass data up to face
    inputBuffer.limit(inputBuffer.capacity());
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
