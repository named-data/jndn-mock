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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.named_data.jndn.encoding.ElementListener;
import net.named_data.jndn.encoding.ElementReader;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.transport.Transport;

/**
 * Non-public class for handling data buffering in NDN unit tests; works in
 * conjunction with {@link MockFace}.
 *
 * @author Alexander Afanasyev, <aa@cs.ucla.edu>
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockTransport extends Transport {
  private OnSendBlockSignal onSendBlock;
  private static final Logger LOGGER = Logger.getLogger(MockTransport.class.getName());
  private boolean connected;
  private ElementReader elementReader;
  private final List<ByteBuffer> receiveBuffer = new LinkedList<>();

  /////////////////////////////////////////////////////////////////////////////

  /**
   * API for buffer handling.
   */
  public interface OnSendBlockSignal {
    /**
     * Callback called when an ByteBuffer is sent (from NFD towards app).
     *
     * @param buffer buffer being sent thourgh face (from NFD towards app)
     */
    void emit(ByteBuffer buffer);
  }

  /**
   * Receive some bytes to add to the mock socket.
   *
   * @param block the byte buffer
   */
  public void receive(final ByteBuffer block) {
    synchronized (receiveBuffer) {
      receiveBuffer.add(block.duplicate());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isLocal(final Transport.ConnectionInfo connectionInfo) {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAsync() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void connect(final Transport.ConnectionInfo connectionInfo,
                      final ElementListener elementListener, final Runnable onConnected) {
    LOGGER.fine("Connecting...");
    connected = true;
    elementReader = new ElementReader(elementListener);
    if (onConnected != null) {
      onConnected.run();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void send(final ByteBuffer buffer) throws IOException {
    LOGGER.log(Level.FINE, "Sending {0} bytes", buffer.capacity() - buffer.position());

    onSendBlock.emit(buffer);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processEvents() throws IOException, EncodingException {
    if (!getIsConnected()) {
      LOGGER.warning("Not connnected...");
    }

    while (true) {
      ByteBuffer block = null;
      synchronized (receiveBuffer) {
        if (!receiveBuffer.isEmpty()) {
          block = receiveBuffer.remove(0);
        }
      }
      if (block == null) {
        break;
      }
      elementReader.onReceivedData(block);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean getIsConnected() {
    return connected;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() throws IOException {
    LOGGER.fine("Closing...");
    connected = false;
  }

  /**
   * @param onSendBlock the handler to execute when packets are received;
   * ideally this would be hidden completely but the super() call in MockFace
   * requires this callback to be set after the parent constructor is called
   */
  public void setOnSendBlock(final OnSendBlockSignal onSendBlock) {
    this.onSendBlock = onSendBlock;
  }
}
