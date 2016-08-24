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

package com.intel.jndn.mock.forwarder;

import com.intel.jndn.mock.MockForwarder;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class PitImpl implements MockForwarder.Pit {

  private final Map<Name, List<MockForwarder.PitEntry>> pit = new ConcurrentHashMap<>();

  public List<MockForwarder.PitEntry> extract(Name name) {
    ArrayList<MockForwarder.PitEntry> entries = new ArrayList<>();
    for (int i = name.size(); i >= 0; i--) {
      Name prefix = name.getPrefix(i);
      List<MockForwarder.PitEntry> pendingInterests = pit.get(prefix);
      if (pendingInterests != null) {
        entries.addAll(pendingInterests);
        pendingInterests.clear(); // TODO is this necessary
      }
    }
    return entries;
  }

  public void add(MockForwarder.PitEntry entry) {
    if (!pit.containsKey(entry.getInterest().getName())) {
      pit.put(entry.getInterest().getName(), new ArrayList<MockForwarder.PitEntry>(1));
    }
    pit.get(entry.getInterest().getName()).add(entry);
  }

  public boolean has(Interest interest) {
    List<MockForwarder.PitEntry> entries = pit.get(interest.getName());

    // TODO simplify
    if (entries != null && entries.size() > 0) {
      return true;
//        for(int i = 0; i < entries.size(); i++){
//          if(entries.get(i).interest.equals(interest)){
//            return true;
//          }
//        }
    }
    return false;
  }
}
