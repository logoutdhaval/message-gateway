package org.fineract.messagegateway.camel.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.fineract.messagegateway.provider.config.ProviderConfig;
import org.fineract.messagegateway.zeebe.ZeebeVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static org.fineract.messagegateway.camel.config.CamelProperties.*;

@Component
public class SendMessageRoute extends RouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ProviderConfig providerConfig;

    @Autowired
    private ZeebeVariables zeebeVariables;

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${zeebe.client.ttl}")
    private int timeToLive;

    @Value("${messagegatewayconfig.protocol}")
    private String protocol;

    @Value("${messagegatewayconfig.host}")
    private String address;

    @Value("${messagegatewayconfig.port}")
    private int port;

    @Value("${operationsconfig.tenantid}")
    private String tenantId;

    @Value("${operationsconfig.tenantidvalue}")
    private String tenantIdValue;

    @Value("${operationsconfig.tenantappkey}")
    private String tenantAppKey;

    @Value("${operationsconfig.tenantappvalue}")
    private String tenantAppKeyValue;


    @Override
    public void configure() throws Exception {

        System.out.println("WWWWWWWWWWWWWWWW");
        from("direct:send-messages")
                .id("send-messages")
                .log(LoggingLevel.INFO, "Sending success for ${exchangeProperty."+PROVIDER_ID+"}")
                .setHeader(tenantId, constant(tenantIdValue))
                .setHeader(tenantAppKey, constant(tenantAppKeyValue))
                .process(exchange ->{
                    log.info("------------------------------------send-message");
                    String mobile = exchange.getProperty(MOBILE_NUMBER).toString();
                    Long internalId = Long.parseLong(exchange.getProperty(INTERNAL_ID).toString());
                    int providerId = providerConfig.getProviderConfig();

                    JSONObject request = new JSONObject();
                    JSONArray jArray = new JSONArray();
                    request.put("internalId", internalId);
                    request.put("mobileNumber", mobile);
                    request.put("message",  exchange.getProperty(DELIVERY_MESSAGE));
                    request.put("providerId", providerId);
                    exchange.getIn().setBody(jArray.put(request).toString());

                })
                .log("${body}")
                .setHeader(Exchange.HTTP_METHOD, simple("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .to(String.format("%s://%s:%s/sms/send?bridgeEndpoint=true", protocol, address, port))
                .log(LoggingLevel.INFO, "Sending sms to message gateway completed")
        ;

    }
}


