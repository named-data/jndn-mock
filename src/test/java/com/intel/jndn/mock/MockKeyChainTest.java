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

import net.named_data.jndn.Name;
import net.named_data.jndn.security.KeyChain;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test MockKeyChain.
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockKeyChainTest {

  /**
   * Test of configure method, of class MockKeyChain.
   */
  @Test
  public void testConfigure() throws Exception {
    String identity = "/test/name";
    KeyChain keyChain = MockKeyChain.configure(new Name(identity));
    assertEquals(identity, keyChain.getDefaultIdentity().toUri());
    assertTrue(keyChain.getDefaultCertificateName().toUri().startsWith(identity));
  }
}
