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

import net.named_data.jndn.ControlParameters;
import net.named_data.jndn.ControlResponse;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.encoding.TlvWireFormat;
import net.named_data.jndn.encoding.tlv.Tlv;
import net.named_data.jndn.encoding.tlv.TlvDecoder;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.pib.PibImpl;
import net.named_data.jndn.security.tpm.TpmBackEnd;
import net.named_data.jndn.transport.Transport;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A client-side face for unit testing.
 *
 * @author Alexander Afanasyev, aa@cs.ucla.edu
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class MockFace extends Face {
  /**
   * Interests sent out of this MockFace.
   * <p/>
   * Sent Interests are appended to this container if options.enablePacketLogger
   * is true. User of this class is responsible for cleaning up the container,
   * if necessary. After .expressInterest, .processEvents must be called before
   * the Interest would show up here.
   */
  public final List<Interest> sentInterests = new ArrayList<>();

  /**
   * Data sent out of this MockFace.
   * <p/>
   * Sent Data are appended to this container if options.enablePacketLogger is
   * true. User of this class is responsible for cleaning up the container, if
   * necessary. After .put, .processEvents must be called before the Data would
   * show up here.
   */
  public final List<Data> sentData = new ArrayList<>();

  /**
   * Emits whenever an Interest is sent.
   * <p/>
   * After .expressInterest, .processEvents must be called before this signal
   * would be emitted.
   */
  public final List<SignalOnSendInterest> onSendInterest = new ArrayList<>();

  /**
   * Emits whenever a Data packet is sent.
   * <p/>
   * After .putData, .processEvents must be called before this signal would be
   * emitted.
   */
  public final List<SignalOnSendData> onSendData = new ArrayList<>();

  private static final Logger LOGGER = Logger.getLogger(MockFace.class.getName());
  private MockTransport transport;
  private KeyChain keyChain;

  /////////////////////////////////////////////////////////////////////////////

  /**
   * API for handling {@link Interest}s.
   */
  public interface SignalOnSendInterest {
    /**
     * Callback called when an Interest is sent out through face (towards NFD).
     * @param interest interest being sent out
     */
    void emit(Interest interest);
  }

  /**
   * API for handling {@link Data}s.
   */
  public interface SignalOnSendData {
    /**
     * Callback called when a Data is sent out through face (towards NFD).
     *
     * @param data data being sent out
     */
    void emit(Data data);
  }

  /**
   * Options for MockFace.
   */
  public static class Options {
    private boolean enablePacketLogging = false;
    private boolean enableRegistrationReply = false;

    /**
     * @return true if packet logging is enabled
     */
    public boolean isEnablePacketLogging() {
      return enablePacketLogging;
    }

    /**
     * Enable/disable packet logging.
     *
     * @param enablePacketLogging If true, packets sent out of MockFace will be appended to a container
     * @return this
     */
    public Options setEnablePacketLogging(final boolean enablePacketLogging) {
      this.enablePacketLogging = enablePacketLogging;
      return this;
    }

    /**
     * @return true if prefix registration mocking is enabled
     */
    public boolean isEnableRegistrationReply() {
      return enableRegistrationReply;
    }

    /**
     * Enable/disable prefix registration mocking.
     *
     * @param enableRegistrationReply If true, prefix registration command will be automatically replied with a
     *                                successful response
     * @return this
     */
    public Options setEnableRegistrationReply(final boolean enableRegistrationReply) {
      this.enableRegistrationReply = enableRegistrationReply;
      return this;
    }
  }

  /**
   * Default options.
   */
  public static final Options DEFAULT_OPTIONS = new Options()
                                                  .setEnablePacketLogging(true)
                                                  .setEnableRegistrationReply(true);

  /**
   * Create MockFace that logs packets in {@link #sentInterests} and
   * {@link #sentData} and emulates NFD prefix registration.
   *
   * @throws SecurityException should not be thrown by this test class
   */
  public MockFace() {
    this(DEFAULT_OPTIONS);
  }

  /**
   * Create MockFace with the specified options.
   * <p>
   * To create Face that does not log packets:
   * <pre>
   *   new MockFace(new Options());
   *   // use onSendInterest.add(handler) and onSendData.add(handler)
   *   // to add custom logic when Interest or Data packet are sent
   *   // from the upper level (to callback)
   * </pre>
   *
   * To create Face that just logs packets in sentInterests and sentData:
   * <pre>
   *   new MockFace(new Options(){ enablePacketLogging = true; });
   * </pre>
   *
   * @param options see {@link Options}
   */
  public MockFace(final Options options) {
    super(new MockTransport(), null);
    transport = (MockTransport) node_.getTransport();
    transport.setOnSendBlock(new OnIncomingPacket());

    try {
      keyChain = MockKeyChain.configure(new Name("/mock/key"));
      setCommandSigningInfo(keyChain, keyChain.getDefaultCertificateName());
    } catch (SecurityException ex) {
      LOGGER.log(Level.SEVERE, "Unexpected error in MockKeyChain; this class should never throw", ex);
      throw new Error(ex);
    }

    if (options.isEnablePacketLogging()) {
      onSendInterest.add(new SignalOnSendInterest() {
        @Override
        public void emit(final Interest interest) {
          sentInterests.add(interest);
        }
      });

      onSendData.add(new SignalOnSendData() {
        @Override
        public void emit(final Data data) {
          sentData.add(data);
        }
      });
    }

    if (options.isEnableRegistrationReply()) {
      onSendInterest.add(new OnPrefixRegistration());
    }
  }

  /**
   * Route incoming packets to the correct callbacks.
   */
  private class OnIncomingPacket implements MockTransport.OnSendBlockSignal {
    /**
     * {@inheritDoc}
     */
    @Override
    public void emit(final ByteBuffer buffer) {
      // @todo Implement NDNLP processing

      try {
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
      } catch (EncodingException e) {
        LOGGER.log(Level.INFO, "Failed to decodeParameters incoming packet", e);
      }
    }
  }

  /**
   * Handle prefix registration requests.
   */
  private class OnPrefixRegistration implements SignalOnSendInterest {
    private static final int STATUS_CODE_OK = 200;
    private static final int CONTROL_PARAMETERS_NAME_OFFSET = -5;
    private static final int CONTROL_COMMAND_NAME_OFFSET = 3;

    /**
     * {@inheritDoc}
     */
    @Override
    public void emit(final Interest interest) {
      final Name localhostRegistration = new Name("/localhost/nfd/rib");
      if (!interest.getName().getPrefix(localhostRegistration.size()).equals(localhostRegistration) ||
          interest.getName().get(CONTROL_COMMAND_NAME_OFFSET).toString().equals("register")) {
        return;
      }

      ControlParameters params = new ControlParameters();
      try {
        params.wireDecode(interest.getName().get(CONTROL_PARAMETERS_NAME_OFFSET).getValue());
        params.setFaceId(1);
        params.setOrigin(0);
        params.setCost(0);
      } catch (EncodingException e) {
        throw new IllegalArgumentException("", e);
      }

      ControlResponse response = new ControlResponse();
      response.setStatusCode(STATUS_CODE_OK);
      response.setStatusText("OK");
      response.setBodyAsControlParameters(params);

      Data data = new Data();
      data.setName(interest.getName());
      data.setContent(response.wireEncode());
      try {
        keyChain.sign(data);
      } catch (SecurityException | KeyChain.Error | TpmBackEnd.Error | PibImpl.Error e) {
        LOGGER.log(Level.FINE, "MockKeyChain signing failed", e);
      }

      try {
        receive(data);
      } catch (EncodingException e) {
        LOGGER.log(Level.INFO, "Failed to encode ControlReposnse data", e);
      }
    }
  }

  /**
   * Mock reception of the Interest packet on the Face (from callback).
   *
   * @param interest the mock-remote interest to add to the PIT
   * @throws EncodingException if packet encoding fails (it should not)
   */
  public void receive(final Interest interest) throws EncodingException {
    transport.receive(interest.wireEncode().buf());
  }

  /**
   * Mock reception of the Data packet on the Face (from callback).
   *
   * @param data the mock-remote data to add to the CS
   * @throws EncodingException if packet encoding fails (it should not)
   */
  public void receive(final Data data) throws EncodingException {
    transport.receive(data.wireEncode().buf());
  }

  /**
   * @return the callback for this face
   */
  public Transport getTransport() {
    return transport;
  }
}
