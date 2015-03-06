package org.spotter.ext.measurement.jmsserver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.activemq.broker.jmx.BrokerViewMBean;

public class Test {
public static void main(String[] args) throws MalformedURLException, IOException, MalformedObjectNameException {
	
	JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL("service:jmx:rmi://deqkal278vm2.qkal.sap.corp:11119/jndi/rmi://deqkal278vm2.qkal.sap.corp:11099/jmxrmi"));
	connector.connect();
	MBeanServerConnection connection = connector.getMBeanServerConnection();

	ObjectName mbeanName = new ObjectName("org.apache.activemq:type=Broker,brokerName=myBroker");
	BrokerViewMBean mbean = MBeanServerInvocationHandler.newProxyInstance(connection, mbeanName, BrokerViewMBean.class, true);

	for (ObjectName queueName : mbean.getQueues()) {
		System.out.println(queueName.getCanonicalName());
	}
	
}
}
