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

import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;

import java.util.Collection;

/**
 * Provide API for measuring packet use on a given face.
 *
 * @author Andrew Brown, andrew.brown@intel.com
 */
public interface MeasurableFace {
  /**
   * @return all interest packets sent by the measured face
   */
  Collection<Interest> sentInterests();

  /**
   * @return all data packets sent by the measured face
   */
  Collection<Data> sentDatas();

  /**
   * @return all interest packets received by the measured face
   */
  Collection<Interest> receivedInterests();

  /**
   * @return all data packets received by the measured face
   */
  Collection<Data> receivedDatas();
}
