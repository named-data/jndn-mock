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
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.IdentityStorage;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.security.identity.PrivateKeyStorage;
import net.named_data.jndn.security.policy.SelfVerifyPolicyManager;
import net.named_data.jndn.security.SecurityException;

/**
 * Create an in-memory key chain for use in NDN-related tests.
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public final class MockKeyChain {
  /**
   * Do not allow instances of this key chain.
   */
  private MockKeyChain() {
  }

  /**
   * Build and configure an in-memory {@link KeyChain}.
   *
   * @param name the name of the default identity to create
   * @return an in-memory {@link KeyChain} configured with the name as the
   * default identity
   * @throws SecurityException if failed to create mock identity
   */
  public static KeyChain configure(final Name name) throws SecurityException {
    PrivateKeyStorage keyStorage = new MemoryPrivateKeyStorage();
    IdentityStorage identityStorage = new MemoryIdentityStorage();
    KeyChain keyChain = new KeyChain(new IdentityManager(identityStorage, keyStorage),
            new SelfVerifyPolicyManager(identityStorage));

    // create keys, certs if necessary
    if (!identityStorage.doesIdentityExist(name)) {
      keyChain.createIdentityAndCertificate(name);
    }

    // set default identity
    keyChain.getIdentityManager().setDefaultIdentity(name);

    return keyChain;
  }
}
