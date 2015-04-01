/*
 * jndn-mock
 * Copyright (c) 2015, Intel Corporation.
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
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.IdentityStorage;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.identity.PrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;

/**
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class MockKeyChain {

  /**
   * Build and configure an in-memory {@link KeyChain}.
   *
   * @param name the name of the default identity to create
   * @return an in-memory {@link KeyChain} configured with the name as the
   * default identity
   * @throws net.named_data.jndn.security.SecurityException
   */
  public static KeyChain configure(Name name) throws net.named_data.jndn.security.SecurityException {
    // access key chain in ~/.ndn; create if necessary 
    PrivateKeyStorage keyStorage = new MemoryPrivateKeyStorage();
    IdentityStorage identityStorage = new MemoryIdentityStorage();
    KeyChain keyChain = new KeyChain(new IdentityManager(identityStorage, keyStorage),
            new SelfVerifyPolicyManager(identityStorage));

    // create keys, certs if necessary
    if (!identityStorage.doesIdentityExist(name)) {
      Name keyName = keyChain.createIdentity(name);
      keyChain.setDefaultKeyForIdentity(keyName, name);
    }

    // set default identity
    keyChain.getIdentityManager().setDefaultIdentity(name);

    return keyChain;
  }
}
