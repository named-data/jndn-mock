package com.intel.jndn.mock;

import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;

import java.util.Collection;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
public interface MeasurableFace {
  Collection<Interest> sentInterests();
  Collection<Data> sentDatas();
  Collection<Interest> receivedInterests();
  Collection<Data> receivedDatas();
}
