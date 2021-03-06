package com.axiata.dialog.mife.mediator.impl.sms;

import com.axiata.dialog.mife.mediator.OperatorEndpoint;
import com.axiata.dialog.mife.mediator.entity.sb.InboundSMSMessage;
import com.axiata.dialog.mife.mediator.entity.sb.SBRetrieveResponse;
import com.axiata.dialog.mife.mediator.internal.APICall;
import com.axiata.dialog.mife.mediator.internal.ApiUtils;
import com.axiata.dialog.mife.mediator.internal.AxiataType;
import com.axiata.dialog.mife.mediator.internal.AxiataUID;
import com.axiata.dialog.mife.mediator.mediationrule.OriginatingCountryCalculatorIDD;
import com.axiata.dialog.oneapi.validation.AxiataException;
import com.axiata.dialog.oneapi.validation.IServiceValidate;
import com.axiata.dialog.oneapi.validation.impl.ValidateRetrieveSms;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.json.JSONArray;
import org.json.JSONObject;

public class RetrieveSMSHandler implements SMSHandler {

    private static Log log = LogFactory.getLog(RetrieveSMSHandler.class);
    private static final String API_TYPE = "sms";
    private OriginatingCountryCalculatorIDD occi;
    private ApiUtils apiUtil;
    private SMSExecutor executor;

    public RetrieveSMSHandler(SMSExecutor executor) {
        this.executor = executor;
        occi = new OriginatingCountryCalculatorIDD();
        apiUtil = new ApiUtils();
    }

    @Override
    public boolean handle(MessageContext context) throws AxiataException, AxisFault, Exception {

        SOAPBody body = context.getEnvelope().getBody();
        Gson gson = new GsonBuilder().serializeNulls().create();
        Properties prop = new Properties();
        //SOAPHeader soapHeader = context.getEnvelope().getHeader();
        //System.out.println(soapHeader.toString());

        String reqType = "retrive_sms";
        String requestid = AxiataUID.getUniqueID(AxiataType.SMSRETRIVE.getCode(), context, executor.getApplicationid());
        //String appID = apiUtil.getAppID(context, reqType);

        int batchSize = 100;

        URL retrieveURL = new URL("http://example.com/smsmessaging/v1" + executor.getSubResourcePath());
        String urlQuery = retrieveURL.getQuery();

        if (urlQuery != null) {
            if (urlQuery.contains("maxBatchSize")) {
                String queryParts[] = urlQuery.split("=");
                if (queryParts.length > 1) {
                    if (Integer.parseInt(queryParts[1]) < 100) {
                        batchSize = Integer.parseInt(queryParts[1]);
                    }
                }
            }
        }

        List<OperatorEndpoint> endpoints = occi.getAPIEndpointsByApp(API_TYPE, executor.getSubResourcePath(),
                executor.getValidoperators());

        log.debug("Endpoints size: " + endpoints.size());

        Collections.shuffle(endpoints);
        int perOpCoLimit = batchSize / (endpoints.size());

        log.debug("Per OpCo limit :" + perOpCoLimit);

        JSONArray results = new JSONArray();

        /**
         * TODO FIX
         */
        int count = 0;
        /**
         * TODO FIX
         */
        ArrayList<String> responses = new ArrayList<String>();
        while (results.length() < batchSize) {
            OperatorEndpoint aEndpoint = endpoints.remove(0);
            endpoints.add(aEndpoint);
            String url = aEndpoint.getEndpointref().getAddress();
            APICall ac = apiUtil.setBatchSize(url, body.toString(), reqType, perOpCoLimit);
            //String url = aEndpoint.getAddress()+getResourceUrl().replace("test_api1/1.0.0/", "");//aEndpoint
            // .getAddress() + ac.getUri();
            JSONObject obj = ac.getBody();
            String retStr = null;
            log.debug("Retrieving messages of operator: " + aEndpoint.getOperator());

            if (context.isDoingGET()) {
                log.debug("Doing makeGetRequest");
                retStr = executor.makeGetRequest(aEndpoint, ac.getUri(), null, true, context);
            } else {
                log.debug("Doing makeRequest");
                retStr = executor.makeRequest(aEndpoint, ac.getUri(), obj.toString(), true, context);
            }

            log.debug("Retrieved messages of " + aEndpoint.getOperator() + " operator: " + retStr);

            if (retStr == null) {
                count++;
                if (count == endpoints.size()) {
                    log.debug("Break because count == endpoints.size() ------> count :" + count + " endpoints.size() :" + endpoints.size());
                    break;
                } else {
                    continue;
                }
            }

            JSONArray resList = apiUtil.getResults(reqType, retStr);
            if (resList != null) {
                for (int i = 0; i < resList.length(); i++) {
                    results.put(resList.get(i));
                }
                responses.add(retStr);
            }

            count++;
            if (count == (endpoints.size() * 2)) {
                log.debug("Break because count == (endpoints.size() * 2) ------> count :" + count + " (endpoints.size() * 2) :" + endpoints.size() * 2);
                break;
            }
        }

        log.debug("Final value of count :" + count);
        log.debug("Results length of retrieve messages: " + results.length());

        JSONObject paylodObject = apiUtil.generateResponse(context, reqType, results, responses, requestid);
        String strjsonBody = paylodObject.toString();

        /*add resourceURL to the southbound response*/
        SBRetrieveResponse sbRetrieveResponse = gson.fromJson(strjsonBody, SBRetrieveResponse.class);
        if (sbRetrieveResponse != null) {
            String resourceURL = sbRetrieveResponse.getInboundSMSMessageList().getResourceURL();            
            InboundSMSMessage[] inboundSMSMessageResponses = sbRetrieveResponse.getInboundSMSMessageList().getInboundSMSMessage();

            for (int i = 0; i < inboundSMSMessageResponses.length; i++) {   
                String messageId = inboundSMSMessageResponses[i].getMessageId();                
                inboundSMSMessageResponses[i].setResourceURL(resourceURL + "/" + messageId);
            }
            sbRetrieveResponse.getInboundSMSMessageList().setInboundSMSMessage(inboundSMSMessageResponses);
        }

        executor.removeHeaders(context);
        executor.setResponse(context, gson.toJson(sbRetrieveResponse));

        ((Axis2MessageContext) context).getAxis2MessageContext().setProperty("messageType", "application/json");

        return true;
    }

     @Override
    public boolean validate(String httpMethod, String requestPath, JSONObject jsonBody, MessageContext context) throws Exception {
        if (!httpMethod.equalsIgnoreCase("GET")) {
            ((Axis2MessageContext) context).getAxis2MessageContext().setProperty("HTTP_SC", 405);
            throw new Exception("Method not allowed");
        }

        IServiceValidate validator;
        String appID = apiUtil.getAppID(context, "retrive_sms");
        String[] params = {appID, ""};
        validator = new ValidateRetrieveSms();
        validator.validateUrl(requestPath);
        validator.validate(params);
        return true;
    }
}
