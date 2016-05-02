package com.intel.jndn.mock.forwarder;

import com.intel.jndn.mock.MockForwarder;
import com.intel.jndn.mock.MockTransport;
import net.named_data.jndn.ForwardingFlags;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.transport.Transport;

import java.util.logging.Logger;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
class ClientFibEntry implements MockForwarder.FibEntry {

  private static final Logger LOGGER = Logger.getLogger(ClientFibEntry.class.getName());
  private final Name prefix;
  private final MockTransport transport;
  private final ForwardingFlags flags;

  ClientFibEntry(Name prefix, MockTransport transport, ForwardingFlags flags) {
    this.prefix = prefix;
    this.transport = transport;
    this.flags = flags;
  }

  @Override
  public void forward(Interest interest, Transport sourceTransport) {
    LOGGER.info("Receiving interest on: " + this.transport);
    transport.receive(interest.wireEncode().buf());
  }

  @Override
  public Name getPrefix() {
    return new Name(prefix);
  }

  @Override
  public ForwardingFlags getFlags() {
    return flags;
  }
}
