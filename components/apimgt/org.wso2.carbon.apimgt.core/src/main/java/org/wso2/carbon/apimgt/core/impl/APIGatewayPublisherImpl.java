/*
* Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/
package org.wso2.carbon.apimgt.core.impl;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.andes.client.AMQConnectionFactory;
import org.wso2.andes.url.URLSyntaxException;
import org.wso2.carbon.apimgt.core.APIMConfigurations;
import org.wso2.carbon.apimgt.core.api.APIGatewayPublisher;
import org.wso2.carbon.apimgt.core.dao.ApiDAO;
import org.wso2.carbon.apimgt.core.dao.impl.DAOFactory;
import org.wso2.carbon.apimgt.core.exception.APIMgtDAOException;
import org.wso2.carbon.apimgt.core.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.core.models.API;
import org.wso2.carbon.apimgt.core.template.dto.GatewayConfigDTO;
import javax.jms.JMSException;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;

/**
 * API gateway related functions
 */
public class APIGatewayPublisherImpl implements APIGatewayPublisher {
    private static final Logger log = LoggerFactory.getLogger(APIGatewayPublisherImpl.class);
    private APIMConfigurations config;

    public APIGatewayPublisherImpl() {
        config = ServiceReferenceHolder.getInstance().getAPIMConfiguration();
    }

    /**
     * Publishing API configuration artifacts to the gateway
     *
     * @param api API model
     * @return is publishing success
     */
    @Override
    public boolean publishToGateway(API api) {
        try {
            ApiDAO apiDAO = DAOFactory.getApiDAO();
            String gatewayConfig = apiDAO.getGatewayConfig(api.getId());
            publishMessage(api, gatewayConfig);
            return true;
        } catch (JMSException e) {
            log.error("Error generating API configuration for API " + api.getName(), e);
        } catch (URLSyntaxException e) {
            log.error("Error generating API configuration for API " + api.getName(), e);
        } catch (APIMgtDAOException e) {
            log.error("Error getting API configuration for API " + api.getName(), e);
        }
        return false;
    }

    /**
     * Publishing the API config to gateway
     *
     * @param content API configuration
     * @throws JMSException       if JMS issue is occurred
     * @throws URLSyntaxException If connection String is invalid
     */
    private void publishMessage(API api, String content) throws JMSException, URLSyntaxException {
        // create connection factory
        TopicConnectionFactory connFactory = new AMQConnectionFactory(
                getTCPConnectionURL(config.getUsername(), config.getPassword()));
        TopicConnection topicConnection = connFactory.createTopicConnection();
        topicConnection.start();
        TopicSession topicSession = topicConnection.createTopicSession(false, TopicSession.AUTO_ACKNOWLEDGE);
        // Send message
        Topic topic = topicSession.createTopic(config.getTopicName());
        // create the message to send
        GatewayConfigDTO dto = new GatewayConfigDTO();
        dto.setApiName(api.getName());
        dto.setContext(api.getContext());
        dto.setVersion(api.getVersion());
        dto.setCreator(api.getCreatedBy());
        dto.setConfig(content);

        TextMessage textMessage = topicSession.createTextMessage(new Gson().toJson(dto));
        TopicPublisher topicPublisher = topicSession.createPublisher(topic);
        topicPublisher.publish(textMessage);

        topicPublisher.close();
        topicSession.close();
        topicConnection.stop();
        topicConnection.close();
    }

    /**
     * Get connection config
     *
     * @param username username
     * @param password password
     * @return connection string
     */
    private String getTCPConnectionURL(String username, String password) {
        // amqp://{username}:{password}@carbon/carbon?brokerlist='tcp://{hostname}:{port}'
        return new StringBuffer().append("amqp://").append(username).append(":").append(password).append("@")
                .append(config.getCarbonClientId()).append("/")
                .append(config.getCarbonVirtualHostName()).append("?brokerlist='tcp://")
                .append(config.getTopicServerHost()).append(":")
                .append(config.getTopicServerPort()).append("'").toString();

    }
}