/*
 *  Copyright WSO2 Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.carbon.apimgt.axiata.dialog.verifier;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.NamingException;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axis2.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.rest.AbstractHandler;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.wso2.carbon.apimgt.gateway.handlers.Utils;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityConstants;
import org.json.XML;


//Handlers should extend AbstractHandler Class
//This is the Handler class for Payment Module
public class DialogPaymentHandler extends AbstractHandler implements ManagedLifecycle {

    private static final Log log = LogFactory.getLog(DialogPaymentHandler.class);
    
    //Entry point for the Payment Module
    public boolean handleRequest(MessageContext messageContext) {
        try {
            
            //Building JSON Payload
            Mediator sequence = messageContext.getSequence("_build_");
            sequence.mediate(messageContext);

            
            String msisdn = null;
            
            String chargingInformation = null;
            
            //Retriving the application name from message context
            String appName = messageContext.getProperty("api.ut.application.name").toString();
          
            //Retriving the username from message context
            String userName = messageContext.getProperty("api.ut.userId").toString();
            
            org.json.JSONObject jsonObj = null;
            
            try {
                jsonObj = XML.toJSONObject(messageContext.getEnvelope().getBody().toString());
            } catch (org.json.JSONException ex) {
                Logger.getLogger(DialogPaymentHandler.class.getName()).log(Level.SEVERE, null, ex);
            }
            
          
            try {
                org.json.JSONObject jsonBody = jsonObj.getJSONObject("soapenv:Body");
                
                try {
                    
                    //Retriving the msisdn from json payload
                    msisdn = jsonBody.getJSONObject("amountTransaction").getString("endUserId");
                   // msisdn = jsonBody.getJSONObject("amountTransaction").set
                    
                    JSONObject jsonObjAmount = new JSONObject(jsonBody.getJSONObject("amountTransaction").getString("paymentAmount"));
                    
                    //Retriving the Charging Amount
                    chargingInformation = jsonObjAmount.getJSONObject("chargingInformation").getString("amount");
 
                    
                } catch (JSONException ex) {
                    Logger.getLogger(DialogPaymentHandler.class.getName()).log(Level.SEVERE, null, ex);
                }
                
                
            } catch (org.json.JSONException ex) {
                Logger.getLogger(DialogPaymentHandler.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
            //Saving the amount to Database
            DatabaseUtils.writeAmount(userName,appName,chargingInformation,msisdn);
            
            } catch (SQLException ex) {
                Logger.getLogger(DialogPaymentHandler.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NamingException ex) {
                Logger.getLogger(DialogPaymentHandler.class.getName()).log(Level.SEVERE, null, ex);
            }
        return true;
    }
      
    public boolean handleResponse(MessageContext messageContext) {
        return true;
    }
    

    public void init(SynapseEnvironment synapseEnvironment) {
        
        
    }

    public void destroy() {
        
    }

}
