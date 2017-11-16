package com.solace.openshift.demo.converter;

import java.util.concurrent.Semaphore;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.Endpoint;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SpringJCSMPFactory;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class XML_To_JSON_Converter_JCSMP  {


	String[] arg = null;
	static JCSMPSession session = null;
	static XMLMessageConsumer cons = null;
	static XMLMessageProducer prod = null;
	static Topic topic;
	static Topic topicResend;
	int counter = 0;
	private static boolean stopping = false;



	public static void main(String[] args) {


		SpringApplication.run(XML_To_JSON_Converter_JCSMP.class, args);


	}


	@Component
	static class Runner implements CommandLineRunner, XMLMessageListener{

		private static final Logger logger = LoggerFactory.getLogger(Runner.class);
		public static int PRETTY_PRINT_INDENT_FACTOR = 4;
		private Semaphore sem = null;
		TextMessage textMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);

		@Autowired
		SpringJCSMPFactory solaceFactory;



		@Override
		public void run(String... strings) throws Exception {

			// Initialize Semaphore
			sem = new Semaphore(0);

			logger.info("In runner");

			try {
				session = solaceFactory.createSession();
				session.connect();
			} catch (InvalidPropertiesException e1) {
				logger.info(e1.getLocalizedMessage());
				e1.printStackTrace();
			}

			if( System.getenv("MSG_TOPIC") !=null) {
				topic = JCSMPFactory.onlyInstance().createTopic(System.getenv("SOL_TOPIC"));
				logger.info("Subscribing Topic is set to: " + topic);
			} else {
				topic = JCSMPFactory.onlyInstance().createTopic("bank/data/xml");
				logger.info("Subscribing Topic is set to: " + topic);
			}
			
			if( System.getenv("MSG_TOPIC_RESEND") !=null) {
				topicResend = JCSMPFactory.onlyInstance().createTopic(System.getenv("SOL_TOPIC_RESEND"));
				logger.info("Publishing Topic is set to: " + topicResend);
			} else {
				topicResend = JCSMPFactory.onlyInstance().createTopic("bank/data/json");
				logger.info("Publish Topic is set to: " + topicResend);
			}

			try {
				prod = session.getMessageProducer(new PubCallback());
			} catch (JCSMPException e) {
				//possible there was a DR fail-over
				logger.info(e.getLocalizedMessage());
				e.printStackTrace();
			}

			cons = session.getMessageConsumer(this);
			session.addSubscription(topic);

			ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
			flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_AUTO);
			flowProps.setNoLocal(true);
			flowProps.setTransportWindowSize(120);
			Endpoint queue = JCSMPFactory.onlyInstance().createQueue(System.getenv("SOL_QUEUE"));
			flowProps.setEndpoint(queue);
			FlowReceiver flow = session.createFlow(this, flowProps);
			flow.start();
			
			
			cons.start();


			blockClientFromEnding();

		}

		@Override
		public void onException(JCSMPException arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onReceive(BytesXMLMessage msg) {
			if (msg instanceof TextMessage) {
				convertAndSend(((TextMessage) msg).getText(), msg);
			} else {
				//System.out.println("=======================not text");
				logger.info("Received non-Text messages");
			}

			


		}

		public boolean convertAndSend(String msgText, BytesXMLMessage _msg ) {
			boolean status = true;
			
			JSONObject jsonObject = null;
			String jsonText = null;
			try {
				 jsonObject = XML.toJSONObject(msgText);
				 jsonText = jsonObject.toString(PRETTY_PRINT_INDENT_FACTOR);
				 
			} catch (JSONException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			//System.out.println(msgText);
			textMsg.clearAttachment();
			textMsg.clearContent();
			textMsg.setText(jsonText);
			textMsg.setDeliveryMode(DeliveryMode.PERSISTENT);
			textMsg.setDeliverToOne(true);
			
			
		

			// Set the message's correlation key. This reference
			// is used when calling back to responseReceivedEx().
			if (_msg.getCorrelationId() != null) {
				textMsg.setCorrelationId(_msg.getCorrelationId());
			}
		
			
			try {
				prod.send(textMsg, topicResend);
			} catch (JCSMPException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return status;

		}

		//Makes sure asynchronous call backs continue to be available by prevent application from ending
		void blockClientFromEnding()
		{
			while(!stopping)
			{
				//block waiting for semaphore
				try
				{
					sem.acquire();
				}
				catch(InterruptedException e)
				{
					//System.err.println("Failed to create Solace Connection");
					logger.error("Failed to create Solace Connection");
					logger.warn("Caught Interrupted Exception on Semaphore");
					stopping = true;
					sem.release();
				}

			}

			System.out.println("Everything is down...");
			System.out.flush();
		}

		void stopSolace()
		{
			logger.info("Stopping consumer and session");
			if(session != null)
			{
				System.out.println("FinalStats:\n" + session.getSessionStats().toString());
				System.out.flush();
				cons.close();
				session.closeSession();
				System.out.println("Stopping consumer and session...");
				System.out.flush();
				logger.info("Shuttting Down!");
				stopping = true;
				sem.release();
			}
		}

		//Class that is called when JVM is interrupted. The thread is called
		//to run when the JVM is interrupted.
		class SolaceShutdownHook extends Thread
		{
			//JCSMPSession h_session = null;
			//XMLMessageConsumer h_cons = null;

			public void run()
			{
				logger.info("\nCaught shutdown interrupt....");
				stopSolace();

			}

		}

	}









}
