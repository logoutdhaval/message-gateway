package org.fineract.messagegateway.zeebe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.checkerframework.checker.units.qual.A;
import org.fineract.messagegateway.provider.config.ProviderConfig;
import org.fineract.messagegateway.sms.api.SmsApiResource;
import org.fineract.messagegateway.sms.domain.SMSMessage;
import org.fineract.messagegateway.sms.util.SmsMessageStatusType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.fineract.messagegateway.camel.config.CamelProperties.*;

import static org.fineract.messagegateway.zeebe.ZeebeVariables.*;
import static org.fineract.messagegateway.zeebe.ZeebeVariables.TRANSACTION_ID;

@Component
public class ZeebeWorkers {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @Autowired
    private SmsApiResource smsApiResource;
    @Value("${provider.telerivet.id}")
    private Long bridgeId;

    @Autowired
    private ProviderConfig providerConfig;

    @Value("${zeebe.worker.timer}")
    private String timer;


    @PostConstruct
    public void setupWorkers() {
        zeebeClient.newWorker()
                .jobType("mg_sendmessage")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());

                    Map<String, Object> variables = job.getVariablesAsMap();
                    String instanceKey = String.valueOf(job.getProcessInstanceKey());
                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(MOBILE_NUMBER,variables.get(PHONE_NUMBER));
                    exchange.setProperty(CORRELATION_ID, instanceKey);
                    exchange.setProperty(INTERNAL_ID,instanceKey);
                    exchange.setProperty(DELIVERY_MESSAGE,variables.get(MESSAGE_TO_SEND));
                    exchange.setProperty(TIMER,timer);
                    variables.put(TIMER,timer);
                    producerTemplate.send("direct:send-messages", exchange);

                    client.newCompleteCommand(job.getKey()).variables(variables)
                            .send()
                            .join()
                    ;
                })
                .name("mg_sendmessage")
                .maxJobsActive(workerMaxJobs)
                .open();

//        zeebeClient.newWorker()
//                .jobType("mg-sendmessage")
//                .handler((client, job) -> {
//                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
//                    String instanceKey = String.valueOf(job.getProcessInstanceKey());
//                    Map<String, Object> variables = job.getVariablesAsMap();
//                    Exchange exchange = new DefaultExchange(camelContext);
//
//                    exchange.setProperty(MOBILE_NUMBER,variables.get(PHONE_NUMBER));
////                    exchange.setProperty(CORRELATION_ID, variables.get(TRANSACTION_ID));
//                    exchange.setProperty(CORRELATION_ID, instanceKey);
////                    exchange.setProperty(INTERNAL_ID,variables.get(MESSAGE_INTERNAL_ID));
//                    exchange.setProperty(INTERNAL_ID,instanceKey);
//                    exchange.setProperty(DELIVERY_MESSAGE,variables.get(MESSAGE_TO_SEND));
//
////                    producerTemplate.send("direct:send-messages", exchange);
////                    producerTemplate.stop();
//
//                    logger.info("------------------------------------send-message-------{}",instanceKey);
//                    String mobile = exchange.getProperty(MOBILE_NUMBER).toString();
//                    Long internalId = Long.parseLong(exchange.getProperty(INTERNAL_ID).toString());
//                    int providerId = providerConfig.getProviderConfig();
//                    SMSMessage smsMessage = new SMSMessage(null,internalId,null,null,null, SmsMessageStatusType.PENDING,null,null,mobile,exchange.getProperty(DELIVERY_MESSAGE).toString(),bridgeId);
//
//                    List<SMSMessage> payload = new ArrayList<SMSMessage>();
//                    payload.add(smsMessage);
//                    smsApiResource.sendShortMessagesToProvider("default","123456543234abdkdkdkd",payload);
//                    logger.info("---------worker-----------{} ---- instance key = {}",job.getKey(), instanceKey);
////                    String messageIdVar = job.getVariables();
////                    String msgExtId = (String) messageIdVar.get("MsgExtId");
////                    if (messageIdVar == null) {
////                        client.newFailCommand(job.getKey())
////                                .retries(job.getRetries() - 1)
////                                .errorMessage("Failed to process job because MsgExtId is not set")
////                                .send()
////                                .join();
////                    } else {
//                        client.newCompleteCommand(job.getKey())
//                                .send()
//                                .join()
//                        ;
////                    }
//                })
//                .name("mg-sendmessage")
////                .requestTimeout(Duration.ofSeconds(10))
//                .maxJobsActive(workerMaxJobs)
//                .open();

        zeebeClient.newWorker()
                .jobType("mg_get_status")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();
                    variables.put(CALLBACK_RETRY_COUNT, 1 + (Integer) variables.getOrDefault(CALLBACK_RETRY_COUNT, 0));
                    String transactionId = (String) variables.get(TRANSACTION_ID);
                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(INTERNAL_ID,variables.get(MESSAGE_INTERNAL_ID));
                    exchange.setProperty(CORRELATION_ID, variables.get(INTERNAL_ID));
                    exchange.setProperty(RETRY_COUNT_CALLBACK, variables.get(CALLBACK_RETRY_COUNT));
                    producerTemplate.send("direct:delivery-message-status", exchange);
                   client.newCompleteCommand(job.getKey())
                           .send()
                           .join()
                   ;
                })
                .name("mg_get_status")
                .maxJobsActive(workerMaxJobs)
                .open();

    }
}