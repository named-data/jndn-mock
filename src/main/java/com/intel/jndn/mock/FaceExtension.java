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

import java.io.IOException;
import java.nio.ByteBuffer;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.ForwardingFlags;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterest;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.encoding.WireFormat;
import net.named_data.jndn.util.Blob;

/**
 * Provides help for extending {@link Face}; this should be an interface but
 * until it is we need a way to know what is the minimal set of methods to
 * override.
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public abstract class FaceExtension extends Face {

	public long expressInterest(Interest interest, OnData onData, OnTimeout onTimeout,
			WireFormat wireFormat) throws IOException {
		return expressInterest(interest, onData, onTimeout, wireFormat);
	}

	public long expressInterest(Interest interest, OnData onData, OnTimeout onTimeout) throws IOException {
		return expressInterest(interest, onData, onTimeout, WireFormat.getDefaultWireFormat());
	}

	public long expressInterest(Interest interest, OnData onData, WireFormat wireFormat) throws IOException {
		return expressInterest(interest, onData, null, wireFormat);
	}

	public long expressInterest(Interest interest, OnData onData) throws IOException {
		return expressInterest(interest, onData, null, WireFormat.getDefaultWireFormat());
	}

	public long expressInterest(Name name, Interest interestTemplate, OnData onData, OnTimeout onTimeout,
			WireFormat wireFormat) throws IOException {
		Interest interest = new Interest(name);
		if (interestTemplate != null) {
			interest.setMinSuffixComponents(interestTemplate.getMinSuffixComponents());
			interest.setMaxSuffixComponents(interestTemplate.getMaxSuffixComponents());
			interest.setKeyLocator(interestTemplate.getKeyLocator());
			interest.setExclude(interestTemplate.getExclude());
			interest.setChildSelector(interestTemplate.getChildSelector());
			interest.setMustBeFresh(interestTemplate.getMustBeFresh());
			interest.setScope(interestTemplate.getScope());
			interest.setInterestLifetimeMilliseconds(
					interestTemplate.getInterestLifetimeMilliseconds());
		} else {
			interest.setInterestLifetimeMilliseconds(4000.0);
		}

		return expressInterest(interest, onData, onTimeout, wireFormat);
	}

	public abstract long registerPrefix(Name prefix,
			OnInterestCallback onInterest, OnRegisterFailed onRegisterFailed, ForwardingFlags flags,
			WireFormat wireFormat) throws IOException, net.named_data.jndn.security.SecurityException;

	public abstract long registerPrefix(Name prefix, final OnInterest onInterest,
			OnRegisterFailed onRegisterFailed, ForwardingFlags flags,
			WireFormat wireFormat) throws IOException, net.named_data.jndn.security.SecurityException;

	public abstract void removeRegisteredPrefix(long registeredPrefixId);

	public abstract long setInterestFilter(InterestFilter filter, OnInterestCallback onInterest);

	public long setInterestFilter(Name prefix, OnInterestCallback onInterest) {
		return setInterestFilter(new InterestFilter(prefix), onInterest);
	}

	public abstract void unsetInterestFilter(long interestFilterId);

	public abstract void putData(Data data, WireFormat wireFormat) throws IOException;

	public void putData(Data data) throws IOException {
		putData(data, WireFormat.getDefaultWireFormat());
	}

	public abstract void send(ByteBuffer encoding) throws IOException;

	public void send(Blob encoding) throws IOException {
		send(encoding.buf());
	}

	public abstract void processEvents() throws IOException, EncodingException;

	public abstract boolean isLocal() throws IOException;

	public abstract void shutdown();
}
