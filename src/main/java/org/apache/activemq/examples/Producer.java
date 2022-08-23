/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.examples;

import java.util.Set;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Producer {
  
  private static final Logger log = LoggerFactory.getLogger(Producer.class);
  
  private final String queueName;
  
  private ConnectionFactory connectionFactory;
  private JMSContext context;
  private JMSProducer producer;
  private Destination queue;

  public Producer(String queueName) {
    this.queueName = queueName;
  }

  public String getQueueName() {
    return queueName;
  }
  
  public void send(String message) throws JMSException {
    if (message != null && !message.isBlank()) {
      producer.send(queue, message);
    }
  }
  
  private String brokerUrlsToBrokerUrl(Set<String> brokerUrls) {
    StringBuilder brokerUrl = new StringBuilder();
    brokerUrl.append("failover://(");
    brokerUrl.append(String.join(",", brokerUrls));
    brokerUrl.append(")?failover.randomize=true");
    brokerUrl.append("&failover.amqpOpenServerListAction=IGNORE");
    return brokerUrl.toString();
  }
  
  public void start(Set<String> brokerUrls) {
    log.debug("Starting producer.");
    connectionFactory = new JmsConnectionFactory(brokerUrlsToBrokerUrl(brokerUrls));
    context = connectionFactory.createContext();
    queue = context.createQueue(queueName);
    producer = context.createProducer();
    
    context.start();
  }
  
  public void stop() {
    log.debug("Stopping producer.");
    producer = null;
    queue = null;
    if (context != null) {
      context.stop();
      context.close();
    }
    context = null;
  }
}
