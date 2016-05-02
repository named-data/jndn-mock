package com.intel.jndn.mock.forwarder;

import com.intel.jndn.mock.MockForwarder;
import com.intel.jndn.mock.MockTransport;
import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;

import java.util.logging.Logger;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
final class PitEntryImpl implements MockForwarder.PitEntry {

  private static final Logger LOGGER = Logger.getLogger(PitEntryImpl.class.getName());
  public final Interest interest;
  private final MockTransport transport;
  private boolean satisfied = false;

  PitEntryImpl(Interest interest, MockTransport transport) {
    this.interest = interest;
    this.transport = transport;
  }

  public void forward(Data data) {
    LOGGER.info("Forwarding data on: " + this.transport);

    if (satisfied) {
      LOGGER.warning("Data already forwarded for PIT entry: " + interest.toUri());
    }

    transport.receive(data.wireEncode().buf());
    satisfied = true;
  }

  public Interest getInterest() {
    return new Interest(interest);
  }

  public boolean isSatisfied() {
    return satisfied;
  }
}
