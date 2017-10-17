package com.solace.openshift.demo.converter;

import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;

class PubCallback implements JCSMPStreamingPublishCorrelatingEventHandler {

	@Override
	public void handleError(String arg0, JCSMPException arg1, long arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void responseReceived(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleErrorEx(Object arg0, JCSMPException arg1, long arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void responseReceivedEx(Object arg0) {
		// TODO Auto-generated method stub

	}


}
