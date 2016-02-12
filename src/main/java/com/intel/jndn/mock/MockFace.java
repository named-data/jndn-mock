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
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.encoding.TlvWireFormat;
import net.named_data.jndn.encoding.tlv.Tlv;
import net.named_data.jndn.encoding.tlv.TlvDecoder;
import net.named_data.jndn.encoding.tlv.TlvEncoder;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.transport.Transport;
import net.named_data.jndn.util.Blob;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A client-side face for unit testing
 *
 * @author Alexander Afanasyev, <aa@cs.ucla.edu>
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockFace extends Face {

  /**
   * API for handling {@link Interest}s
   */
  public interface SignalOnSendInterest {
    void emit(Interest interest) throws EncodingException, SecurityException;
  }

  /**
   * API for handling {@link Data}s
   */
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

  /**
   * Default options
   */
  public static final Options DEFAULT_OPTIONS = new Options() {
    {
      enablePacketLogging = true;
      enableRegistrationReply = true;
    }
  };

  /**
   * Create MockFace that logs packets in {@link #sentInterests} and
   * {@link #sentData} and emulates NFD prefix registration
   *
   * @throws SecurityException should not be thrown by this test class
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
   *   new MockFace(new Options(){ enablePacketLogging = true; });
   * </pre>
   *
   * @param options see {@link Options}
   */
  public MockFace(Options options) {
    super(new MockFaceTransport(), null);
    transport = (MockFaceTransport) node_.getTransport();
    transport.setOnSendBlock(new OnIncomingPacket());

    try {
      keyChain = MockKeyChain.configure(new Name("/mock/key"));
      setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
    } catch (SecurityException ex) {
      LOGGER.log(Level.SEVERE, "Unexpected error in MockKeyChain; this class should never throw", ex);
      throw new Error(ex);
    }

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
      onSendInterest.add(new OnPrefixRegistration());
    }
  }

  /**
   * Route incoming packets to the correct callbacks
   */
  private class OnIncomingPacket implements MockFaceTransport.OnSendBlockSignal {

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
        LOGGER.info("Received an unknown packet");
      }
    }
  }

  /**
   * Handle prefix registration requests
   */
  private class OnPrefixRegistration implements SignalOnSendInterest {

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

      if ("register".equals(interest.getName().get(3).toString())) {
        params.setCost(0);
      }

      // TODO: replace with jNDN ControlResponse encoding when available
      // http://redmine.named-data.net/issues/3455
      TlvEncoder encoder = new TlvEncoder(256);
      int saveLength = encoder.getLength();
      encoder.writeBuffer(params.wireEncode().buf());
      encoder.writeBlobTlv(Tlv.NfdCommand_StatusText, new Blob("OK").buf());
      encoder.writeNonNegativeIntegerTlv(Tlv.NfdCommand_StatusCode, 200);
      encoder.writeTypeAndLength(Tlv.NfdCommand_ControlResponse, encoder.getLength() - saveLength);

      Data data = new Data();
      data.setName(interest.getName());
      data.setContent(new Blob(encoder.getOutput(), false));
      keyChain.sign(data);

      receive(data);
    }
  }

  /**
   * Mock reception of the Interest packet on the Face (from transport)
   *
   * @param interest the mock-remote interest to add to the PIT
   * @throws EncodingException if packet encoding fails (it should not)
   */
  public void receive(Interest interest) throws EncodingException {
    transport.receive(interest.wireEncode().buf());
  }

  /**
   * Mock reception of the Data packet on the Face (from transport)
   *
   * @param data the mock-remote data to add to the CS
   * @throws EncodingException if packet encoding fails (it should not)
   */
  public void receive(Data data) throws EncodingException {
    transport.receive(data.wireEncode().buf());
  }

  /**
   * @return the transport for this face
   */
  public Transport getTransport() {
    return transport;
  }

  /**
   * Interests sent out of this MockFace
   * <p>
   * Sent Interests are appended to this container if options.enablePacketLogger
   * is true. User of this class is responsible for cleaning up the container,
   * if necessary. After .expressInterest, .processEvents must be called before
   * the Interest would show up here.
   */
  public final List<Interest> sentInterests = new ArrayList<>();

  /**
   * Data sent out of this MockFace
   * <p>
   * Sent Data are appended to this container if options.enablePacketLogger is
   * true. User of this class is responsible for cleaning up the container, if
   * necessary. After .put, .processEvents must be called before the Data would
   * show up here.
   */
  public final List<Data> sentData = new ArrayList<>();

  /**
   * Emits whenever an Interest is sent
   * <p>
   * After .expressInterest, .processEvents must be called before this signal
   * would be emitted.
   */
  public final List<SignalOnSendInterest> onSendInterest = new ArrayList<>();

  /**
   * Emits whenever a Data packet is sent
   * <p>
   * After .putData, .processEvents must be called before this signal would be
   * emitted.
   */
  public final List<SignalOnSendData> onSendData = new ArrayList<>();

  private static final Logger LOGGER = Logger.getLogger(MockFace.class.getName());
  private MockFaceTransport transport;
  private KeyChain keyChain;
}
