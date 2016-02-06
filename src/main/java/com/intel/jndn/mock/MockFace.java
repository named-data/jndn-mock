/*
 * jndn-mock
 * Copyright (c) 2013-2015 Regents of the University of California.
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

import net.named_data.jndn.*;
import net.named_data.jndn.encoding.ElementListener;
import net.named_data.jndn.encoding.ElementReader;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.encoding.TlvWireFormat;
import net.named_data.jndn.encoding.tlv.Tlv;
import net.named_data.jndn.encoding.tlv.TlvDecoder;
import net.named_data.jndn.encoding.tlv.TlvEncoder;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.transport.Transport;
import net.named_data.jndn.util.Blob;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A client-side face for unit testing
 */
public class MockFace extends Face {

  public interface SignalOnSendInterest {

    void emit(Interest interest) throws EncodingException, SecurityException;
  }

  public interface SignalOnSendData {

    void emit(Data data);
  }

  /**
   * Options for MockFace
   */
  public static class Options {

    /**
     * If true, packets sent out of MockFace will be appended to a container
     */
    boolean enablePacketLogging = false;

    /**
     * If true, prefix registration command will be automatically replied with a
     * successful response
     */
    boolean enableRegistrationReply = false;
  }

  final public static Options DEFAULT_OPTIONS = new Options() {
    {
      enablePacketLogging = true;
      enableRegistrationReply = true;
    }
  };

  /**
   * Create MockFace that logs packets in sentInterests and sentData and
   * emulates NFD prefix registration
   */
  public MockFace() throws SecurityException {
    this(DEFAULT_OPTIONS);
  }

  /**
   * Create MockFace with the specified options
   * <p>
   * To create Face that does not log packets:
   * <pre>
   *   new MockFace(new Options());
   *   // use onSendInterest.add(handler) and onSendData.add(handler)
   *   // to add custom logic when Interest or Data packet are sent
   *   // from the upper level (to transport)
   * </pre>
   *
   * To create Face that just logs packets in sentInterests and sentData:
   * <pre>
   *   new MockFace(new Options(){{ enablePacketLogging=true; }});
   * </pre>
   */
  public MockFace(Options options) throws SecurityException {
    super(new MockFaceTransport(), null);
    m_transport = (MockFaceTransport) node_.getTransport();
    m_keychain = MockKeyChain.configure(new Name("/mock/key"));
    setCommandSigningInfo(m_keychain, m_keychain.getDefaultCertificateName());

    m_transport.onSendBlock = new MockFaceTransport.OnSendBlockSignal() {
      @Override
      public void emit(ByteBuffer buffer) throws EncodingException, SecurityException {
        // @todo Implement NDNLP processing

        if (buffer.get(0) == Tlv.Interest || buffer.get(0) == Tlv.Data) {
          TlvDecoder decoder = new TlvDecoder(buffer);
          if (decoder.peekType(Tlv.Interest, buffer.remaining())) {
            Interest interest = new Interest();
            interest.wireDecode(buffer, TlvWireFormat.get());

            for (SignalOnSendInterest signal : onSendInterest) {
              signal.emit(interest);
            }
          } else if (decoder.peekType(Tlv.Data, buffer.remaining())) {
            Data data = new Data();
            data.wireDecode(buffer, TlvWireFormat.get());

            for (SignalOnSendData signal : onSendData) {
              signal.emit(data);
            }
          }
        } else {
          logger.info("Received an unknown packet");
        }
      }
    };

    if (options.enablePacketLogging) {
      onSendInterest.add(new SignalOnSendInterest() {
        @Override
        public void emit(Interest interest) {
          sentInterests.add(interest);
        }
      });

      onSendData.add(new SignalOnSendData() {
        @Override
        public void emit(Data data) {
          sentData.add(data);
        }
      });
    }

    if (options.enableRegistrationReply) {
      onSendInterest.add(new SignalOnSendInterest() {
        @Override
        public void emit(Interest interest) throws EncodingException, SecurityException {
          final Name localhostRegistration = new Name("/localhost/nfd/rib");
          if (!interest.getName().getPrefix(3).equals(localhostRegistration)) {
            return;
          }

          ControlParameters params = new ControlParameters();
          params.wireDecode(interest.getName().get(-5).getValue());
          params.setFaceId(1);
          params.setOrigin(0);

          if (interest.getName().get(3).toString().equals("register")) {
            params.setCost(0);
          }

          // TODO: replace with jNDN ControlResponse encoding when available
          //       http://redmine.named-data.net/issues/3455
          TlvEncoder encoder = new TlvEncoder(256);
          int saveLength = encoder.getLength();
          encoder.writeBuffer(params.wireEncode().buf());
          encoder.writeBlobTlv(Tlv.NfdCommand_StatusText, new Blob("OK").buf());
          encoder.writeNonNegativeIntegerTlv(Tlv.NfdCommand_StatusCode, 200);
          encoder.writeTypeAndLength(Tlv.NfdCommand_ControlResponse, encoder.getLength() - saveLength);

          Data data = new Data();
          data.setName(interest.getName());
          data.setContent(new Blob(encoder.getOutput(), false));
          m_keychain.sign(data);

          receive(data);
        }
      });
    }
  }

  /**
   * Mock reception of the Interest packet on the Face (from transport)
   * @param interest the mock-remote interest to add to the PIT
   * @throws EncodingException if packet encoding fails (it should not)
   */
  public void receive(Interest interest) throws EncodingException {
    m_transport.receive(interest.wireEncode().buf());
  }

  /**
   * Mock reception of the Data packet on the Face (from transport)
   * @param data the mock-remote data to add to the CS
   * @throws EncodingException if packet encoding fails (it should not)
   */
  public void receive(Data data) throws EncodingException {
    m_transport.receive(data.wireEncode().buf());
  }

  /**
   * @return the transport for this face
   */
  public Transport getTransport() {
    return m_transport;
  }

  /**
   * Internal transport for {@link MockFace}
   */
  private static class MockFaceTransport extends Transport {

    public interface OnSendBlockSignal {

      void emit(ByteBuffer buffer) throws EncodingException, SecurityException;
    }

    /**
     * Receive some bytes to add to the mock socket
     * @param block the byte buffer
     * @throws EncodingException 
     */
    public void receive(ByteBuffer block) throws EncodingException {
      synchronized (receiveBuffer) {
        receiveBuffer.add(block.duplicate());
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLocal(ConnectionInfo connectionInfo) {
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
    public void connect(Transport.ConnectionInfo connectionInfo,
            ElementListener elementListener, Runnable onConnected) {
      logger.fine("Connecting...");
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
    public void send(ByteBuffer data) throws IOException {
      logger.log(Level.FINE, "Sending {0} bytes", (data.capacity() - data.position()));

      try {
        onSendBlock.emit(data);
      } catch (EncodingException e) {
        logger.log(Level.WARNING, "Failed to decode packet", e);
      } catch (SecurityException e) {
        logger.log(Level.WARNING, "Failed signature", e);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processEvents() throws IOException, EncodingException {
      if (!getIsConnected()) {
        logger.warning("Not connnected...");
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
      logger.fine("Closing...");
      connected = false;
    }

    public OnSendBlockSignal onSendBlock;

    private static final Logger logger = Logger.getLogger(MockFaceTransport.class.getName());
    private boolean connected;
    private ElementReader elementReader;
    private final List<ByteBuffer> receiveBuffer = new LinkedList<>();
  }

  /**
   * Interests sent out of this MockFace
   * <p>
   * Sent Interests are appended to this container if options.enablePacketLogger
   * is true. User of this class is responsible for cleaning up the container,
   * if necessary. After .expressInterest, .processEvents must be called before
   * the Interest would show up here.
   */
  public List<Interest> sentInterests = new ArrayList<>();

  /**
   * Data sent out of this MockFace
   * <p>
   * Sent Data are appended to this container if options.enablePacketLogger is
   * true. User of this class is responsible for cleaning up the container, if
   * necessary. After .put, .processEvents must be called before the Data would
   * show up here.
   */
  public List<Data> sentData = new ArrayList<>();

  /**
   * Emits whenever an Interest is sent
   * <p>
   * After .expressInterest, .processEvents must be called before this signal
   * would be emitted.
   */
  public List<SignalOnSendInterest> onSendInterest = new ArrayList<>();

  /**
   * Emits whenever a Data packet is sent
   * <p>
   * After .putData, .processEvents must be called before this signal would be
   * emitted.
   */
  public List<SignalOnSendData> onSendData = new ArrayList<>();

  private static final Logger logger = Logger.getLogger(MockFace.class.getName());
  private MockFaceTransport m_transport;
  private KeyChain m_keychain;
}
