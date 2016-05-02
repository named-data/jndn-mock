package com.intel.jndn.mock.forwarder;

import com.intel.jndn.mock.MockForwarder;
import net.named_data.jndn.Face;
import net.named_data.jndn.ForwardingFlags;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.transport.Transport;

import java.util.logging.Logger;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class LocalFibEntry implements MockForwarder.FibEntry {

  private static final Logger LOGGER = Logger.getLogger(LocalFibEntry.class.getName());
  private final Name prefix;
  private final MockForwarder.OnInterestReceived callback;
  private final Face registrationFace;
  private final ForwardingFlags flags;

  public LocalFibEntry(Name prefix, MockForwarder.OnInterestReceived callback, Face registrationFace, ForwardingFlags flags) {
    this.prefix = prefix;
    this.callback = callback;
    this.registrationFace = registrationFace;
    this.flags = flags;
  }

  public void forward(Interest interest, Transport sourceTransport) {
    LOGGER.info("Forwarding interest on: " + this.callback);
    callback.in(interest, sourceTransport, registrationFace);
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
