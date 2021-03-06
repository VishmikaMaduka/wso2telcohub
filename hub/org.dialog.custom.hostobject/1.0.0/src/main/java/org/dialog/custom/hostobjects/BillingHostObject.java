/*
 * Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 */
package org.dialog.custom.hostobjects;

import org.dialog.custom.hostobjects.southbound.SbHostObjectUtils;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axis2.databinding.types.Year;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dialog.custom.dao.Approval;
import org.dialog.custom.hostobjects.northbound.NbHostObjectUtils;
import org.dialog.custom.hostobjects.util.ChargeRate;
import org.dialog.custom.hostobjects.util.RateKey;
import org.mozilla.javascript.*;
import org.wso2.carbon.apimgt.api.APIConsumer;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.Application;
import org.wso2.carbon.apimgt.api.model.Tier;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerFactory;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.usage.client.dto.APIVersionUserUsageDTO;
import org.wso2.carbon.apimgt.usage.client.exception.APIMgtUsageQueryServiceClientException;

public class BillingHostObject extends ScriptableObject {

    private static final Log log = LogFactory.getLog(BillingHostObject.class);
    private String hostobjectName = "DialogBilling";
    private APIConsumer apiConsumer;
    private static Map<String, Float> tierPricing = new HashMap<String, Float>();
    private static Map<String, Integer> tierMaximumCount = new HashMap<String, Integer>();
    private static Map<String, Integer> tierUnitTime = new HashMap<String, Integer>();

    public static Map<String, Float> getTierPricing() {
        return tierPricing;
    }

    public static Map<String, Integer> getTierMaximumCount() {
        return tierMaximumCount;
    }

    public static Map<String, Integer> getTierUnitTime() {
        return tierUnitTime;
    }

    public String getUsername() {
        return username;
    }
    private String username;

    @Override
    public String getClassName() {
        return hostobjectName;
    }

    public BillingHostObject(String username) throws APIManagementException {
        log.info("::: Initialized HostObject for : " + username);
        if (username != null) {
            this.username = username;
            apiConsumer = APIManagerFactory.getInstance().getAPIConsumer(username);
        } else {
            apiConsumer = APIManagerFactory.getInstance().getAPIConsumer();
        }
    }

    public BillingHostObject() {
        log.info("::: Initialized HostObject ");
    }

    public APIConsumer getApiConsumer() {
        return apiConsumer;
    }

    private static APIConsumer getAPIConsumer(Scriptable thisObj) {
        return ((BillingHostObject) thisObj).getApiConsumer();
    }

    public static String jsFunction_getReportFileContent(Context cx, Scriptable thisObj,
            Object[] args, Function funObj)
            throws APIManagementException {
        if (args == null || args.length == 0) {
            handleException("Invalid number of parameters.");
        }

        String subscriberName = (String) args[0];
        String period = (String) args[1];
        boolean isNorthbound = (Boolean) args[2];

        generateReport(subscriberName, period, true, isNorthbound, "__ALL__");

        String fileContent = (isNorthbound) ? NbHostObjectUtils.getReport(subscriberName, period) : SbHostObjectUtils.getReport(subscriberName, period);
        return fileContent;
    }

    public static NativeArray jsFunction_getCustomCareDataReport(Context cx, Scriptable thisObj,
            Object[] args, Function funObj)
            throws APIManagementException {
        if (args == null || args.length == 0) {
            handleException("Invalid number of parameters.");
        }

        String fromDate = (String) args[0];
        String toDate = (String) args[1];
        String msisdn = (String) args[2];
        String subscriberName = (String) args[3];
        String operator = (String) args[4];
        String app = (String) args[5];
        String api = (String) args[6];
        String limitStart = (String) args[7];
        String limitEnd = (String) args[8];
        String timeOffset = (String) args[9];

        NativeArray dataArray = new NativeArray(0);

        dataArray = getCustomCareDataReport(fromDate, toDate, msisdn, subscriberName, operator, app, api, limitStart, limitEnd, timeOffset);

        //String fileContent = SbHostObjectUtils.getCustomReport(fromDate, toDate, subscriberName, operator, app, api);
        return dataArray;
    }

    public static String jsFunction_getCustomCareDataRecordsCount(Context cx, Scriptable thisObj,
            Object[] args, Function funObj)
            throws APIManagementException {
        if (args == null || args.length == 0) {
            handleException("Invalid number of parameters.");
        }

        String fromDate = (String) args[0];
        String toDate = (String) args[1];
        String msisdn = (String) args[2];
        String subscriberName = (String) args[3];
        String operator = (String) args[4];
        String app = (String) args[5];
        String api = (String) args[6];

        String dataString = getCustomCareDataRecordCount(fromDate, toDate, msisdn, subscriberName, operator, app, api);

        //String fileContent = SbHostObjectUtils.getCustomReport(fromDate, toDate, subscriberName, operator, app, api);
        return dataString;
    }

    public static String jsFunction_getCustomApiTrafficReportFileContent(Context cx, Scriptable thisObj,
            Object[] args, Function funObj)
            throws APIManagementException {
        if (args == null || args.length == 0) {
            handleException("Invalid number of parameters.");
        }

        String fromDate = (String) args[0];
        String toDate = (String) args[1];
        String subscriberName = (String) args[2];
        String operator = (String) args[3];
        String api = (String) args[4];
        String timeOffset = (String) args[5];
        String responseType = (String) args[6];
        boolean isNorthbound = (Boolean) args[7];

        generateCustomApiTrafficReport(fromDate, toDate, subscriberName, operator, api, timeOffset, responseType, isNorthbound);

        String fileContent = SbHostObjectUtils.getCustomReport(fromDate, toDate, subscriberName, operator, api);
        return fileContent;
    }

    public static NativeArray jsFunction_getAPIUsageforSubscriber(Context cx, Scriptable thisObj,
            Object[] args, Function funObj)
            throws APIManagementException {
        List<APIVersionUserUsageDTO> list = null;
        if (args == null || args.length == 0) {
            handleException("Invalid number of parameters.");
        }
        NativeArray ret = null;
        try {
            NativeArray myn = new NativeArray(0);
            if (!SbHostObjectUtils.checkDataPublishingEnabled()) {
                return myn;
            }
            String subscriberName = (String) args[0];
            String period = (String) args[1];
            boolean isNorthbound = (Boolean) args[2];
            String operatorName = (String) args[3];

            ret = generateReport(subscriberName, period, false, isNorthbound, operatorName);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            ret = new NativeArray(0);
            NativeObject row = new NativeObject();
            row.put("error", row, true);
            row.put("message", row, e.getMessage());
            ret.put(ret.size(), ret, row);
        }

        return ret;

    }

    public static NativeArray jsFunction_getCostPerAPI(Context cx, Scriptable thisObj,
            Object[] args, Function funObj)
            throws APIManagementException {
        List<APIVersionUserUsageDTO> list = null;
        if (args == null || args.length == 0) {
            handleException("Invalid number of parameters.");
        }
        NativeArray myn = new NativeArray(0);
        if (!SbHostObjectUtils.checkDataPublishingEnabled()) {
            return myn;
        }
        String subscriberName = (String) args[0];
        String period = (String) args[1];

        String opcode = (String) args[2];
        String application = (String) args[3];
        if ((application == null) || (application.equalsIgnoreCase("0"))) {
            application = "__All__";
        }

        boolean isNorthbound = (Boolean) args[4];


        NativeArray ret = generateReport(subscriberName, period, false, isNorthbound, opcode);
        NativeArray arr = (NativeArray) ret;

        Map<String, Double> apiPriceMap = new HashMap<String, Double>();
        apiPriceMap.clear();

        NativeArray apiPriceResponse = new NativeArray(0);

        //Object[] array = new Object[(int) arr.getLength()];
        for (Object o : arr.getIds()) {
            int i = (Integer) o;
            NativeObject subs = (NativeObject) arr.get(i);
            String subscriber = subs.get("subscriber").toString();
            log.info(subscriber);
            NativeArray applicatons = (NativeArray) subs.get("applications");

            for (Object o2 : applicatons.getIds()) {
                int j = (Integer) o2;
                NativeObject app = (NativeObject) applicatons.get(j);
                String appname = app.get("applicationname").toString();

                if (application.equalsIgnoreCase("__All__")) {
                    NativeArray subscriptions = (NativeArray) app.get("subscriptions");
                    for (Object o3 : subscriptions.getIds()) {
                        int k = (Integer) o3;
                        NativeObject apis = (NativeObject) subscriptions.get(k);
                        String api = apis.get("subscriptionapi").toString();
                        Double price = 0.0;
                        NativeArray operators = (NativeArray) apis.get("operators");
                        for (Object o4 : operators.getIds()) {
                            int l = (Integer) o4;
                            NativeObject opis = (NativeObject) operators.get(l);
                            String operator = opis.get("operator").toString();
                            if ((opcode.equalsIgnoreCase("__All__")) || (operator.equalsIgnoreCase(opcode))) {
                                price = price + Double.valueOf(opis.get("price").toString());
                            }
                        }

                        if (apiPriceMap.containsKey(api)) {
                            apiPriceMap.put(api, (apiPriceMap.get(api) + Double.valueOf(price)));
                        } else {
                            apiPriceMap.put(api, Double.valueOf(price));
                        }
                    }
                } else {
                    try {
                        String applicationName = SbHostObjectUtils.getApplicationNameById(application);
                        if (appname.equalsIgnoreCase(applicationName)) {
                            NativeArray subscriptions = (NativeArray) app.get("subscriptions");
                            for (Object o3 : subscriptions.getIds()) {
                                int k = (Integer) o3;
                                NativeObject apis = (NativeObject) subscriptions.get(k);
                                String api = apis.get("subscriptionapi").toString();
                                Double price = 0.0;
                                NativeArray operators = (NativeArray) apis.get("operators");
                                for (Object o4 : operators.getIds()) {
                                    int l = (Integer) o4;
                                    NativeObject opis = (NativeObject) operators.get(l);
                                    String operator = opis.get("operator").toString();
                                    if ((opcode.equalsIgnoreCase("__All__")) || (operator.equalsIgnoreCase(opcode))) {
                                        price = price + Double.valueOf(opis.get("price").toString());
                                    }
                                }

                                if (apiPriceMap.containsKey(api)) {
                                    apiPriceMap.put(api, (apiPriceMap.get(api) + Double.valueOf(price)));
                                } else {
                                    apiPriceMap.put(api, Double.valueOf(price));
                                }
                            }
                        }
                    } catch (SQLException ex) {
                        Logger.getLogger(BillingHostObject.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (APIMgtUsageQueryServiceClientException ex) {
                        Logger.getLogger(BillingHostObject.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

            }
        }

        //if (opcode.equalsIgnoreCase("__All__")) {
        short i = 0;
        for (Map.Entry pairs : apiPriceMap.entrySet()) {
            NativeObject row = new NativeObject();
            String apiName = pairs.getKey().toString();
            //row.put("apiName", row, pairs.getKey().toString());
            row.put(apiName, row, pairs.getValue().toString());
            apiPriceResponse.put(i, apiPriceResponse, row);
            i++;
        }
        /*} else {
         short x = 0;

         String year = null;
         String month = null;
         if (period != null) {
         String[] periodArray = period.split("-");
         year = periodArray[0];
         month = periodArray[1];
         }

         String receivedAppId;
         if (application.equalsIgnoreCase("__All__")) {
         receivedAppId = null;
         } else {
         receivedAppId = application;
         }

         for (Map.Entry pairs : apiPriceMap.entrySet()) {
         NativeObject row = new NativeObject();
         String apiName = pairs.getKey().toString();

         double totApiIncome = Double.valueOf(pairs.getValue().toString());
         double totHits = 0;
         double currentOpcoHits = 0;

         try {
         Map<String, Integer> usagecount = SbHostObjectUtils.getOperatorbreakdown(receivedAppId, year, month, subscriberName, apiName);

         for (Map.Entry opCountPair : usagecount.entrySet()) {
         totHits = totHits + Double.valueOf(opCountPair.getValue().toString());
         }

         for (Map.Entry opCountPair : usagecount.entrySet()) {
         String currentOpco = opCountPair.getKey().toString();
         if (currentOpco.equals(opcode)) {
         currentOpcoHits = Double.valueOf(opCountPair.getValue().toString());
         }
         }

         } catch (APIMgtUsageQueryServiceClientException e) {
         handleException("Error occurred while executing the dummyQuery.", e);
         } catch (SQLException e) {
         handleException("Error occurred while retrieving data.", e);
         }

         double opcoIncomeRasio = (totHits > 0) ? (currentOpcoHits / totHits) : 0;
         double currentOpocoIncome = totApiIncome * opcoIncomeRasio;

         //row.put("apiName", row, pairs.getKey().toString());
         row.put(apiName, row, String.format("%.2f", currentOpocoIncome));
         apiPriceResponse.put(x, apiPriceResponse, row);
         x++;
         }
         } */

        return apiPriceResponse;

    }

    private static NativeArray generateReport(String subscriberName, String period, boolean persistReport, boolean isNorthbound, String operatorName) throws APIManagementException {

        //createTierPricingMap();
        Map<RateKey, ChargeRate> rateCard = (isNorthbound) ? NbHostObjectUtils.getRateCard() : SbHostObjectUtils.getRateCard();

        NativeArray ret = null;
        try {
            ret = (isNorthbound) ? NbHostObjectUtils.generateReportofSubscriber(persistReport, subscriberName, period, rateCard) : SbHostObjectUtils.generateReportofSubscriber(persistReport, subscriberName, period, rateCard, operatorName);
        } catch (APIMgtUsageQueryServiceClientException e) {
            handleException("Error occurred while executing the dummyQuery.", e);
        } catch (SQLException e) {
            handleException("Error occurred while retrieving data.", e);
        } catch (IOException e) {
            handleException("Error occurred while generating report.", e);
        }
        return ret;
    }

    private static NativeArray generateCustomApiTrafficReport(String fromDate, String toDate, String subscriberName, String operator, String api, String timeOffset, String resType, boolean isNorthbound) throws APIManagementException {

        NativeArray ret = null;
        try {
            if (isNorthbound) {
                ret = NbHostObjectUtils.generateCustomTrafficReport(true, fromDate, toDate, subscriberName, operator, api, timeOffset, resType);
            } else {
                ret = SbHostObjectUtils.generateCustomTrafficReport(true, fromDate, toDate, subscriberName, operator, api, timeOffset, resType);
            }
        } catch (APIMgtUsageQueryServiceClientException e) {
            handleException("Error occurred while executing the dummyQuery.", e);
        } catch (SQLException e) {
            handleException("Error occurred while retrieving data.", e);
        } catch (IOException e) {
            handleException("Error occurred while generating report.", e);
        }
        return ret;
    }

    private static NativeArray getCustomCareDataReport(String fromDate, String toDate, String msisdn, String subscriberName, String operator, String app, String api, String stLimit, String endLimit, String timeOffset) throws APIManagementException {

        NativeArray ret = null;
        try {
            ret = SbHostObjectUtils.generateCustomrCareDataReport(true, fromDate, toDate, msisdn, subscriberName, operator, app, api, stLimit, endLimit, timeOffset);
        } catch (APIMgtUsageQueryServiceClientException e) {
            handleException("Error occurred while executing the dummyQuery.", e);
        } catch (SQLException e) {
            handleException("Error occurred while retrieving data.", e);
        }
        return ret;
    }

    private static String getCustomCareDataRecordCount(String fromDate, String toDate, String msisdn, String subscriberName, String operator, String app, String api) throws APIManagementException {

        String ret = null;
        try {
            ret = SbHostObjectUtils.generateCustomrCareDataRecordCount(true, fromDate, toDate, msisdn, subscriberName, operator, app, api);
        } catch (APIMgtUsageQueryServiceClientException e) {
            handleException("Error occurred while executing the dummyQuery.", e);
        } catch (SQLException e) {
            handleException("Error occurred while retrieving data.", e);
        } catch (IOException e) {
            handleException("Error occurred while generating report.", e);
        }
        return ret;
    }

    private static NativeArray generateFinancialReport(String subscriberName, String period,
            String opcode, String application) throws APIManagementException {

        NativeArray ret = null;
        try {
            ret = SbHostObjectUtils.generateCostperApisummary(true, subscriberName, period, opcode, application);
        } catch (APIMgtUsageQueryServiceClientException e) {
            handleException("Error occurred while executing the dummyQuery.", e);
        } catch (SQLException e) {
            handleException("Error occurred while retrieving data.", e);
        } catch (IOException e) {
            handleException("Error occurred while generating report.", e);
        }
        return ret;
    }

    public static NativeArray jsFunction_getResponseTimeData(Context cx, Scriptable thisObj,
            Object[] args, Function funObj)
            throws APIManagementException {
        String subscriberName = (String) args[0];
        NativeArray nativeArray = null;
        log.debug("Starting getResponseTimeData funtion with " + subscriberName);
        try {
            Map<String, String> responseTimes = SbHostObjectUtils.getResponseTimesForSubscriber(subscriberName);
            short i = 0;
            log.debug(subscriberName + ", responseTimes " + responseTimes);
            if (responseTimes != null) {
                nativeArray = new NativeArray(0);
            }
            for (Map.Entry<String, String> timeEntry : responseTimes.entrySet()) {
                NativeObject row = new NativeObject();
                log.debug(subscriberName + ", timeEntry key " + timeEntry.getKey());
                log.debug(subscriberName + ", timeEntry value" + timeEntry.getValue());
                row.put("apiName", row, timeEntry.getKey().toString());
                row.put("responseTime", row, timeEntry.getValue().toString());
                nativeArray.put(i, nativeArray, row);
                i++;
            }

        } catch (Exception e) {
            log.error("Error occured getResponseTimeData ");
            log.error(e.getStackTrace());
            handleException("Error occurred while populating Response Time graph.", e);
        }
        log.info("End of getResponseTimeData");
        return nativeArray;
    }

    public static NativeArray jsFunction_getAllResponseTimes(Context cx, Scriptable thisObj, Object[] args, Function funObj)
            throws APIManagementException {
        String operatorName = (String) args[0];
        String subscriberName = (String) args[1];
        String appId = (String) args[2];
        String fromDate = (String) args[3];
        String toDate = (String) args[4];

        String appName = "";
        if (appId.equals("0") || appId.equalsIgnoreCase("__All__")) {
            appId = "__ALL__";
            appName = "__ALL__";
        } else {
            try {
                Application application = new ApiMgtDAO().getApplicationById(Integer.parseInt(appId));
                appName = application.getName();//HostObjectUtils.getApplicationNameById(appId);
            } catch (Exception ex) {
                Logger.getLogger(BillingHostObject.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        NativeArray apis = new NativeArray(0);
        log.debug("Starting getAllResponseTimes function for user- " + subscriberName + " app- " + appName);
        try {
            Map<String, List<APIResponseDTO>> responseMap = SbHostObjectUtils.getAllResponseTimes(operatorName, subscriberName,
                    appName, appId, fromDate, toDate);
            short i = 0;
            log.debug(subscriberName + ", responseMap " + responseMap);

            for (Map.Entry<String, List<APIResponseDTO>> timeEntry : responseMap.entrySet()) {

                NativeObject api = new NativeObject();
                api.put("apiName", api, timeEntry.getKey());

                NativeArray responseTimes = new NativeArray(0);
                for (APIResponseDTO dto : timeEntry.getValue()) {
                    NativeObject responseData = new NativeObject();
                    responseData.put("serviceTime", responseData, dto.getServiceTime());
                    responseData.put("responseCount", responseData, dto.getResponseCount());
                    responseData.put("date", responseData, dto.getDate().toString());
                    responseTimes.put(responseTimes.size(), responseTimes, responseData);
                }
                api.put("responseData", api, responseTimes);
                apis.put(i, apis, api);
                i++;
            }

        } catch (Exception e) {
            log.error("Error occured getAllResponseTimes ");
            log.error(e.getStackTrace());
            handleException("Error occurred while populating Response Time graph.", e);
        }

        return apis;
    }

    public static NativeArray jsFunction_getAllSubscribers(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        try {
            List<String> subscribers = SbHostObjectUtils.getAllSubscribers();

            if (subscribers != null) {
                int i = 0;
                for (String subscriber : subscribers) {
                    nativeArray.put(i, nativeArray, subscriber);
                    i++;
                }
            }

        } catch (Exception e) {
            log.error("Error occurred getAllSubscribers");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting the subscribers", e);
        }
        return nativeArray;
    }

    public static NativeArray jsFunction_getAllOperators() throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        try {
            List<String> operators = SbHostObjectUtils.getAllOperators();

            if (operators != null) {
                int i = 0;
                for (String op : operators) {
                    nativeArray.put(i, nativeArray, op);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getAllOperators");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting the operators", e);
        }
        return nativeArray;
    }

    public static NativeArray jsFunction_getTotalAPITrafficForPieChart(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String fromDate = args[0].toString();
        String toDate = args[1].toString();
        String subscriber = args[2].toString();
        int applicationId = Integer.parseInt(args[3].toString());
        String operator = args[4].toString();

        try {
            List<String[]> api_requests = SbHostObjectUtils.getTotalAPITrafficForPieChart(fromDate, toDate, subscriber, operator, applicationId);

            if (api_requests != null) {
                int i = 0;
                for (String[] api_request : api_requests) {
                    nativeArray.put(i, nativeArray, api_request);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getTotalTrafficForPieChart");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting total traffic for pie chart", e);
        }
        return nativeArray;
    }

    public static NativeArray jsFunction_getOperatorAppList(Context cx, Scriptable thisObj, Object[] args, Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String operator = args[0].toString();

        try {
            List<Integer> opertatorAppIds = AxiataDataAccessObject.getApplicationsByOperator(operator);
            int i = 0;
            if (opertatorAppIds != null) {
                for (Integer temp : opertatorAppIds) {
                    String appName = SbHostObjectUtils.getApplicationNameById(temp.toString());
                    if (appName != null) {
                        NativeObject appData = new NativeObject();
                        appData.put("id", appData, temp.toString());
                        appData.put("name", appData, appName);
                        nativeArray.put(i, nativeArray, appData);
                    }
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getOperatorAppList");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting Operator app list", e);
        }

        return nativeArray;
    }

    public static NativeArray jsFunction_getTotalAPITrafficForHistogram(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String fromDate = args[0].toString();
        String toDate = args[1].toString();
        String subscriber = args[2].toString();
        int applicationId = Integer.parseInt(args[3].toString());
        String operator = args[4].toString();
        String api = args[5].toString();

        try {
            List<String[]> api_requests = SbHostObjectUtils.getTotalAPITrafficForHistogram(fromDate, toDate, subscriber, operator, applicationId, api);
            List<String[]> apis = SbHostObjectUtils.getAllAPIs(fromDate, toDate, subscriber, operator, applicationId, api);
            NativeArray apiHits = null;
            NativeArray apiHitDates = null;

            if (api_requests != null && apis != null) {
                for (int i = 0; i < apis.size(); i++) {
                    apiHits = new NativeArray(0);
                    apiHitDates = new NativeArray(0);
                    int x = 0;
                    for (int j = 0; j < api_requests.size(); j++) {
                        if (apis.get(i)[0].toString().equals(api_requests.get(j)[0].toString())) {
                            apiHits.put(x, apiHits, api_requests.get(j)[2].toString());
                            apiHitDates.put(x, apiHitDates, api_requests.get(j)[1].toString());
                            x++;
                        }
                    }
                    NativeObject reqData = new NativeObject();
                    reqData.put("apiName", reqData, apis.get(i)[0].toString());
                    reqData.put("apiHits", reqData, apiHits);
                    reqData.put("apiHitDates", reqData, apiHitDates);
                    nativeArray.put(i, nativeArray, reqData);
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getTotalTrafficForHistogram");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting total traffic for histogram", e);
        }
        return nativeArray;
    }

    public static NativeArray jsFunction_getOperatorWiseAPITrafficForPieChart(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String fromDate = args[0].toString();
        String toDate = args[1].toString();
        String subscriber = args[2].toString();
        int applicationId = Integer.parseInt(args[3].toString());
        String api = args[4].toString();

        try {
            List<String[]> api_requests = SbHostObjectUtils.getOperatorWiseAPITrafficForPieChart(fromDate, toDate, subscriber, api, applicationId);

            if (api_requests != null) {
                int i = 0;
                for (String[] api_request : api_requests) {
                    nativeArray.put(i, nativeArray, api_request);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getTotalTrafficForHistogram");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting total traffic for histogram", e);
        }
        return nativeArray;
    }

    /*public static NativeArray jsFunction_getAPIWiseTrafficForHistogram(Context cx, Scriptable thisObj, Object[] args,
     Function funObj) throws APIManagementException {
     NativeArray nativeArray = new NativeArray(0);

     String fromDate = args[0].toString();
     String toDate = args[1].toString();
     String subscriber = args[2].toString();
     String operator = args[3].toString();
     String api = args[4].toString();

     try {
     List<String[]> api_requests = SbHostObjectUtils.getAPIWiseTrafficForHistogram(fromDate, toDate, subscriber, operator, api);

     if (api_requests != null) {
     int i = 0;
     for (String[] api_request : api_requests) {
     nativeArray.put(i, nativeArray, api_request);
     i++;
     }
     }
     } catch (Exception e) {
     log.error("Error occurred getAPIWiseTrafficForHistogram");
     log.error(e.getStackTrace());
     handleException("Error occurred while getting API wise traffic for histogram", e);
     }
     return nativeArray;
     }  */
    public static NativeArray jsFunction_getSubscribersByOperator(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String operatorName = args[0].toString();

        try {
            List<String> subscribers = SbHostObjectUtils.getSubscribersByOperator(operatorName);

            if (subscribers != null) {
                int i = 0;
                for (String subscriber : subscribers) {
                    nativeArray.put(i, nativeArray, subscriber);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getSubscribersByOperator");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting subscribers by operator", e);
        }
        return nativeArray;
    }

    /*public static NativeArray jsFunction_getApplicationsByOperator(Context cx, Scriptable thisObj, Object[] args,
     Function funObj) throws APIManagementException {
     NativeArray nativeArray = new NativeArray(0);

     String operatorName = args[0].toString();

     try {
     List<String[]> applications = SbHostObjectUtils.getApplicationsByOperator(operatorName);

     if (applications != null) {
     int i = 0;
     for (String[] application : applications) {
     nativeArray.put(i, nativeArray, application);
     i++;
     }
     }
     } catch (Exception e) {
     log.error("Error occurred getApplicationsByOperator");
     log.error(e.getStackTrace());
     handleException("Error occurred while getting applications by operator", e);
     }
     return nativeArray;
     }*/
    public static NativeArray jsFunction_getApplicationsBySubscriber(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String subscriberName = args[0].toString();

        try {
            List<String[]> applications = SbHostObjectUtils.getApplicationsBySubscriber(subscriberName);

            if (applications != null) {
                int i = 0;
                for (String[] application : applications) {
                    nativeArray.put(i, nativeArray, application);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getApplicationsBySubscriber");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting applications by subscriber", e);
        }
        return nativeArray;
    }

    public static NativeArray jsFunction_getOperatorsBySubscriber(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String subscriberName = args[0].toString();

        try {
            List<String> operators = SbHostObjectUtils.getOperatorsBySubscriber(subscriberName);

            if (operators != null) {
                int i = 0;
                for (String operator : operators) {
                    nativeArray.put(i, nativeArray, operator);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getOperatorsBySubscriber");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting operators by subscriber", e);
        }
        return nativeArray;
    }

    public static NativeArray jsFunction_getAPIsBySubscriber(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String subscriberName = args[0].toString();

        try {
            List<String> apis = SbHostObjectUtils.getAPIsBySubscriber(subscriberName);

            if (apis != null) {
                int i = 0;
                for (String api : apis) {
                    nativeArray.put(i, nativeArray, api);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getAPIsBySubscriber");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting APIs by subscriber", e);
        }
        return nativeArray;
    }

    // HIra added
    public static NativeArray jsFunction_getAllOperationTypes(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);
        List<String[]> opTypes;
        try {
            opTypes = SbHostObjectUtils.getOperationTypes();

            if (opTypes != null) {
                int i = 0;
                for (String[] operation : opTypes) {
                    nativeArray.put(i, nativeArray, operation);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getAllOperationTypes");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting AllOperationTypes", e);
        }
        return nativeArray;
    }

    public static NativeArray jsFunction_getApprovalHistory(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String fromDate = null;
        String toDate = null;
        String subscriber = args[2].toString();
        int applicationId = Integer.parseInt(args[3].toString());
        String operator = (String) args[4];
        String api = null;

        try {
            List<String[]> api_requests = SbHostObjectUtils.getApprovalHistory(fromDate, toDate, subscriber, api, applicationId, operator);

            if (api_requests != null) {
                int i = 0;
                for (String[] api_request : api_requests) {
                    nativeArray.put(i, nativeArray, api_request);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getTotalTrafficForHistogram");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting total traffic for histogram", e);
        }
        return nativeArray;
    }

    public static NativeObject jsFunction_getApprovalHistoryApp(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        int applicationId = Integer.parseInt(args[0].toString());
        String operator = args[1].toString();
        //String api = args[4].toString();

        NativeObject Application = new NativeObject();
        NativeArray opcoapps = new NativeArray(0);
        NativeArray Subscriptions = new NativeArray(0);
        NativeArray opcosubs = new NativeArray(0);

        try {

            List<Approval> api_requests = SbHostObjectUtils.getApprovalHistoryApp(applicationId, operator);

            if (api_requests != null) {
                //int i = 0;
                int j = 0;
                int k = 0;
                int ai = 0;
                for (Approval obj : api_requests) {
                    //set application status

                    String retstat = obj.getIsactive();
                    if ((obj.getIsactive().equalsIgnoreCase("CREATED")) || (obj.getIsactive().equalsIgnoreCase("ON_HOLD"))) {
                        retstat = "PENDING APPROVE";
                    } else if (obj.getIsactive().equalsIgnoreCase("UNBLOCKED")) {
                        retstat = "APPROVED";
                    }

                    if (obj.getType().equalsIgnoreCase("1")) {
                        NativeObject app = new NativeObject();
                        app.put("appid", app, obj.getApplication_id());
                        app.put("type", app, obj.getType());
                        app.put("name", app, obj.getName());
                        app.put("operatorid", app, obj.getOperatorid());
                        app.put("status", app, retstat);
                        Application.put("application", Application, app);
                    } else if (obj.getType().equalsIgnoreCase("2")) {
                        NativeObject appopco = new NativeObject();
                        appopco.put("type", appopco, obj.getType());
                        appopco.put("name", appopco, obj.getName());
                        appopco.put("operatorid", appopco, obj.getOperatorid());
                        appopco.put("status", appopco, retstat);
                        opcoapps.put(ai, opcoapps, appopco);
                        ai++;
                    } else if (obj.getType().equalsIgnoreCase("3")) {
                        NativeObject sub = new NativeObject();
                        sub.put("appid", sub, obj.getApplication_id());
                        sub.put("type", sub, obj.getType());
                        sub.put("name", sub, obj.getName());
                        sub.put("operatorid", sub, obj.getOperatorid());
                        sub.put("status", sub, retstat);
                        sub.put("tier", sub, obj.getTier_id());
                        sub.put("api", sub, obj.getApi_name());
                        sub.put("apiversion", sub, obj.getApi_version());
                        Subscriptions.put(j, Subscriptions, sub);
                        j++;
                    } else if (obj.getType().equalsIgnoreCase("4")) {
                        NativeObject subop = new NativeObject();
                        subop.put("appid", subop, obj.getApplication_id());
                        subop.put("type", subop, obj.getType());
                        subop.put("name", subop, obj.getName());
                        subop.put("operatorid", subop, obj.getOperatorid());
                        subop.put("status", subop, retstat);
                        subop.put("tier", subop, obj.getTier_id());
                        subop.put("api", subop, obj.getApi_name());
                        subop.put("apiversion", subop, obj.getApi_version());
                        subop.put("created", subop, obj.getCreated());
                        subop.put("lastupdated", subop, obj.getLast_updated());

                        opcosubs.put(k, opcosubs, subop);
                        k++;
                    }
                }
                Application.put("opcoapps", Application, opcoapps);
                Application.put("Subscriptions", Application, Subscriptions);
                Application.put("opcosubs", Application, opcosubs);

            }
        } catch (Exception e) {
            log.error("Error occurred getTotalTrafficForHistogram");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting total traffic for histogram", e);
        }
        return Application;
    }

    public static NativeArray jsFunction_getErrorResponseCodesForPieChart(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String fromDate = args[0].toString();
        String toDate = args[1].toString();
        String subscriber = args[2].toString();
        int applicationId = Integer.parseInt(args[3].toString());
        String operator = args[4].toString();
        String api = args[5].toString();

        try {
            List<String[]> api_requests = SbHostObjectUtils.getErrorResponseCodesForPieChart(fromDate, toDate, subscriber, operator, applicationId, api);

            if (api_requests != null) {
                int i = 0;
                for (String[] api_request : api_requests) {
                    nativeArray.put(i, nativeArray, api_request);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getErrorResponseCodesForPieChart");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting error response codes for pie chart", e);
        }
        return nativeArray;
    }

    public static NativeArray jsFunction_getErrorResponseCodesForHistogram(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String fromDate = args[0].toString();
        String toDate = args[1].toString();
        String subscriber = args[2].toString();
        int applicationId = Integer.parseInt(args[3].toString());
        String operator = args[4].toString();
        String api = args[5].toString();

        try {
            List<String[]> api_response_codes = SbHostObjectUtils.getErrorResponseCodesForHistogram(fromDate, toDate, subscriber, operator, applicationId, api);

            List<String[]> resCodes = SbHostObjectUtils.getAllErrorResponseCodes(fromDate, toDate, subscriber, operator, applicationId, api);
            NativeArray apiHits = null;
            NativeArray apiHitDates = null;

            if (api_response_codes != null && resCodes != null) {
                for (int i = 0; i < resCodes.size(); i++) {
                    apiHits = new NativeArray(0);
                    apiHitDates = new NativeArray(0);
                    int x = 0;
                    for (int j = 0; j < api_response_codes.size(); j++) {
                        if (resCodes.get(i)[0].equals(api_response_codes.get(j)[0])) {
                            apiHitDates.put(x, apiHitDates, api_response_codes.get(j)[1]);
                            apiHits.put(x, apiHits, api_response_codes.get(j)[2]);
                            x++;
                        }
                    }
                    NativeObject reqData = new NativeObject();
                    reqData.put("errorCode", reqData, resCodes.get(i)[0]);
                    reqData.put("apiHits", reqData, apiHits);
                    reqData.put("apiHitDates", reqData, apiHitDates);
                    nativeArray.put(i, nativeArray, reqData);
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getErrorResponseCodesForHistogram");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting error response codes for histogram", e);
        }
        return nativeArray;
    }

    private static int getMaxCount(Tier tier) throws XMLStreamException {
        OMElement policy = AXIOMUtil.stringToOM(new String(tier.getPolicyContent()));
        OMElement maxCount = policy.getFirstChildWithName(APIConstants.POLICY_ELEMENT)
                .getFirstChildWithName(APIConstants.THROTTLE_CONTROL_ELEMENT)
                .getFirstChildWithName(APIConstants.POLICY_ELEMENT)
                .getFirstChildWithName(APIConstants.THROTTLE_MAXIMUM_COUNT_ELEMENT);
        if (maxCount != null) {
            return Integer.parseInt(maxCount.getText());
        }

        return -1;
    }

    private static int getTimeUnit(Tier tier) throws XMLStreamException {
        OMElement policy = AXIOMUtil.stringToOM(new String(tier.getPolicyContent()));
        OMElement timeUnit = policy.getFirstChildWithName(APIConstants.POLICY_ELEMENT)
                .getFirstChildWithName(APIConstants.THROTTLE_CONTROL_ELEMENT)
                .getFirstChildWithName(APIConstants.POLICY_ELEMENT)
                .getFirstChildWithName(APIConstants.THROTTLE_UNIT_TIME_ELEMENT);
        if (timeUnit != null) {
            return Integer.parseInt(timeUnit.getText());
        }
        return -1;
    }

    private static void createTierPricingMap() throws APIManagementException {
        Map<String, Tier> tierMap = APIUtil.getTiers();
        for (Map.Entry<String, Tier> entry : tierMap.entrySet()) {
            Map<String, Object> attributes = entry.getValue().getTierAttributes();
            if (attributes != null && attributes.containsKey("Rate")) {
                tierPricing.put(entry.getKey(), Float.parseFloat(attributes.get("Rate").toString()));
            } else {
                tierPricing.put(entry.getKey(), 0f);
            }
            try {
                int maxCount = getMaxCount(entry.getValue());
                tierMaximumCount.put(entry.getKey(), maxCount);
            } catch (XMLStreamException e) {
            }

            try {
                int unitTime = getTimeUnit(entry.getValue());
                tierUnitTime.put(entry.getKey(), unitTime);
            } catch (XMLStreamException e) {
            }
        }
    }

    private static void printTierPricing() throws APIManagementException {
        createTierPricingMap();
        System.out.println("Print Tier Pricings");
        for (Map.Entry<String, Float> pricing : tierPricing.entrySet()) {
            System.out.println("Price for Tier : " + pricing.getKey() + " = " + pricing.getValue());
        }
    }

    private static void handleException(String msg) throws APIManagementException {
        log.error(msg);
        throw new APIManagementException(msg);
    }

    private static void handleException(String msg, Throwable t) throws APIManagementException {
        log.error(msg, t);
        throw new APIManagementException(msg, t);
    }

    //=======================================PRIYANKA_06608===============================
    @SuppressWarnings("null")
    public static NativeArray jsFunction_getSPforBlacklist(Context cx, Scriptable thisObj, Object[] args, Function funObj) throws APIManagementException, APIMgtUsageQueryServiceClientException {
        if (args == null || args.length == 0) {
            handleException("Invalid number of parameters.");
        }
        String operator = String.valueOf(args[2]);
        Boolean isadmin = Boolean.valueOf(String.valueOf(args[1]));

        List<SPObject> spList = BillingDataAccessObject.generateSPList();
        List<SPObject> spListoperator = AxiataDataAccessObject.getSPList(operator);
        NativeArray nativeArray = new NativeArray(0);
        NativeObject nativeObject;
        if (spList != null) {
            int i = 0;
            for (SPObject spObject : spList) {
                NativeObject row = new NativeObject();
                row.put("appId", row, spObject.getAppId().toString());
                row.put("spName", row, spObject.getSpName());

                if (!isadmin) {
                    for (SPObject SPObjectoperator : spListoperator) {
                        if (SPObjectoperator.getAppId() == spObject.getAppId()) {
                            nativeArray.put(i, nativeArray, row);
                            break;
                        }
                    }
                } else {
                    nativeArray.put(i, nativeArray, row);
                }
                i++;
            }
        }
        return nativeArray;
    }

    public static NativeObject jsFunction_getAppforBlacklist(Context cx, Scriptable thisObj, Object[] args, Function funObj) throws APIManagementException, APIMgtUsageQueryServiceClientException {
        if (args == null || args.length == 0) {
            handleException("Invalid number of parameters.");
        }
        String appId = args[0].toString();
        SPObject spObject = BillingDataAccessObject.generateSPObject(appId);
        NativeObject row = new NativeObject();
        if (spObject != null) {

            row.put("appId", row, spObject.getAppId().toString());
            row.put("spName", row, spObject.getSpName());
            row.put("userName", row, spObject.getUserName());
            row.put("token", row, spObject.getToken());
            row.put("secret", row, spObject.getSecret());
            row.put("key", row, spObject.getKey());
        }
        return row;
    }

    public static NativeArray jsFunction_getDashboardAPITrafficForPieChart(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String timeRange = args[0].toString();
        String operator = args[1].toString();
        String subscriber = args[2].toString();

        if (operator.equalsIgnoreCase("All")) {
            operator = HostObjectConstants.ALL_OPERATORS;
        } else {
            operator = operator.toUpperCase();
        }
        if (subscriber.equalsIgnoreCase("All")) {
            subscriber = HostObjectConstants.ALL_SUBSCRIBERS;
        }

        Calendar now = Calendar.getInstance();
        String toDate = getCurrentTime(now);
        String fromDate = subtractTimeRange(now, timeRange);


        int applicationId = HostObjectConstants.ALL_APPLICATIONS;

        try {
            List<String[]> api_requests = SbHostObjectUtils.getTotalAPITrafficForPieChart(fromDate, toDate, subscriber, operator, applicationId);

            if (api_requests != null) {
                //get the total requests first to calculate the percentage
                double totalRequests = 0;
                for (String[] api_request : api_requests) {
                    totalRequests = totalRequests + Integer.parseInt(api_request[1]);
                }
                int i = 0;
                for (String[] api_request : api_requests) {
                    String[] chartData = new String[3];
                    chartData[0] = api_request[0];
                    chartData[1] = api_request[1];
                    double percentage = Math.round((Integer.parseInt(api_request[1]) * 100) / totalRequests);
                    chartData[2] = String.valueOf((int) percentage);
                    nativeArray.put(i, nativeArray, chartData);
                    i++;
                }
            }
        } catch (Exception e) {
            log.error("Error occurred getDashboardAPITrafficForPieChart");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting total traffic for pie chart", e);
        }
        return nativeArray;
    }

    /**
     * Subtract the timerange from the current time and return in "yyyy-mm-dd"
     * format
     *
     * @param date
     * @param range
     * @return
     */
    private static String subtractTimeRange(Calendar date, String timeRange) {
        String fromDate = null;
        if (timeRange.equals(HostObjectConstants.DATE_LAST_DAY)) {
            date.add(Calendar.DATE, -1);
        } else if (timeRange.equals(HostObjectConstants.DATE_LAST_WEEK)) {
            date.add(Calendar.DATE, -7);
        } else if (timeRange.equals(HostObjectConstants.DATE_LAST_MONTH)) {
            date.add(Calendar.MONTH, -1);
        } else if (timeRange.equals(HostObjectConstants.DATE_LAST_YEAR)) {
            date.add(Calendar.YEAR, -1);
        }

        fromDate = date.get(Calendar.YEAR) + "-" + (date.get(Calendar.MONTH) + 1) + "-" + date.get(Calendar.DATE);
        return fromDate;
    }

    /**
     * Get the current time in "yyyy-mm-dd" as string
     *
     * @param date
     * @return
     */
    private static String getCurrentTime(Calendar date) {
        return date.get(Calendar.YEAR) + "-" + (date.get(Calendar.MONTH) + 1) + "-" + date.get(Calendar.DATE);
    }

    /**
     * create date string in miliseconds for date format "yyyy-mm-dd"
     *
     * @param date
     * @return
     */
    private static String getTimeInMilli(String date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String dateString = "";
        Date parsedDate;
        try {
            parsedDate = format.parse(date);
            dateString = String.valueOf(parsedDate.getTime());
        } catch (ParseException e) {
            log.error("error in parsing the date");
        }

        return dateString;
    }

    public static NativeArray jsFunction_getDashboardAPITrafficForLineChart(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeArray nativeArray = new NativeArray(0);

        String timeRange = args[0].toString();
        String operator = args[1].toString();
        String subscriber = args[2].toString();

        if (operator.equalsIgnoreCase("All")) {
            operator = HostObjectConstants.ALL_OPERATORS;
        } else {
            operator = operator.toUpperCase();
        }
        if (subscriber.equalsIgnoreCase("All")) {
            subscriber = HostObjectConstants.ALL_SUBSCRIBERS;
        }

        Calendar now = Calendar.getInstance();
        String toDate = getCurrentTime(now);
        String fromDate = subtractTimeRange(now, timeRange);


        int applicationId = HostObjectConstants.ALL_APPLICATIONS;
        String api = HostObjectConstants.ALL_APIS;

        try {
            List<String[]> api_requests = SbHostObjectUtils.getTotalAPITrafficForLineChart(fromDate, toDate, subscriber, operator, applicationId, api);
            NativeArray apiHits = null;
            NativeArray apiHitDates = null;

            apiHits = new NativeArray(0);
            apiHitDates = new NativeArray(0);
            int x = 0;

            for (int j = 0; j < api_requests.size(); j++) {
                apiHits.put(x, apiHits, api_requests.get(j)[1].toString());
                String hitDateInMilli = getTimeInMilli(api_requests.get(j)[0].toString());
                apiHitDates.put(x, apiHitDates, hitDateInMilli);
                x++;
            }


            NativeObject reqData = new NativeObject();
            reqData.put("apiHits", reqData, apiHits);
            reqData.put("apiHitDates", reqData, apiHitDates);
            reqData.put("startDate", reqData, getTimeInMilli(fromDate));
            nativeArray.put(0, nativeArray, reqData);
        } catch (Exception e) {
            log.error("Error occurred getTotalAPITrafficForLineChart");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting total traffic for line chart", e);
        }
        return nativeArray;
    }

    public static NativeObject jsFunction_getDashboardAPIResponseTimeForLineChart(Context cx, Scriptable thisObj, Object[] args,
            Function funObj) throws APIManagementException {
        NativeObject nativeObject = new NativeObject();

        String timeRange = args[0].toString();
        String operator = args[1].toString();
        String subscriber = args[2].toString();

        if (operator.equalsIgnoreCase("All")) {
            operator = HostObjectConstants.ALL_OPERATORS;
        } else {
            operator = operator.toUpperCase();
        }
        if (subscriber.equalsIgnoreCase("All")) {
            subscriber = HostObjectConstants.ALL_SUBSCRIBERS;
        }

        Calendar now = Calendar.getInstance();
        String toDate = getCurrentTime(now);
        String fromDate = subtractTimeRange(now, timeRange);


        try {
            List<APIResponseDTO> apiResponses = SbHostObjectUtils.getTotalAPIResponseTimeForLineChart(fromDate, toDate, subscriber, operator, timeRange);

            NativeArray apiServiceTimes = new NativeArray(0);
            NativeArray apiResponseCount = new NativeArray(0);
            NativeArray apiAvgServiceTime = new NativeArray(0);
            NativeArray apiHitDates = new NativeArray(0);
            int x = 0;


            int serviceTime = 0;
            int respCount = 0;
            int avgServiceTime = 0;

            for (int j = 0; j < apiResponses.size(); j++) {
                APIResponseDTO temp = apiResponses.get(j);

                serviceTime = temp.getServiceTime();
                respCount = temp.getResponseCount();
                avgServiceTime = serviceTime / respCount;

                String hitDateInMilli = getTimeInMilli(temp.getDate().toString());

                apiServiceTimes.put(x, apiServiceTimes, Integer.toString(serviceTime));
                apiResponseCount.put(x, apiResponseCount, Integer.toString(respCount));
                apiAvgServiceTime.put(x, apiAvgServiceTime, Integer.toString(avgServiceTime));
                apiHitDates.put(x, apiHitDates, hitDateInMilli);

                x++;
            }


            NativeObject respData = new NativeObject();
            respData.put("apiServiceTimes", respData, apiServiceTimes);
            respData.put("apiResponseCount", respData, apiResponseCount);
            respData.put("apiAvgServiceTime", respData, apiAvgServiceTime);
            respData.put("apiHitDates", respData, apiHitDates);

            nativeObject.put("responseTimes", nativeObject, respData);
            nativeObject.put("startDate", nativeObject, getTimeInMilli(fromDate));

        } catch (Exception e) {
            log.error("Error occurred getDashboardAPIResponseTimeForLineChart");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting total response times for line chart", e);
        }
        return nativeObject;
    }

    public static NativeArray jsFunction_getDashboardResponseTimesByAPI(Context cx, Scriptable thisObj, Object[] args, Function funObj)
            throws APIManagementException {

        String timeRange = args[0].toString();
        String operator = args[1].toString();
        String subscriber = args[2].toString();

        if (operator.equalsIgnoreCase("All")) {
            operator = HostObjectConstants.ALL_OPERATORS;
        } else {
            operator = operator.toUpperCase();
        }
        if (subscriber.equalsIgnoreCase("All")) {
            subscriber = HostObjectConstants.ALL_SUBSCRIBERS;
        }

        Calendar now = Calendar.getInstance();
        String toDate = getCurrentTime(now);
        String fromDate = subtractTimeRange(now, timeRange);

        NativeArray apis = new NativeArray(0);
        try {
            Map<String, List<APIResponseDTO>> responseMap = SbHostObjectUtils.getAllResponseTimesByDate(operator, subscriber, fromDate, toDate);
            short i = 0;
            int serviceTime = 0;
            int respCount = 0;
            int avgServiceTime = 0;

            for (Map.Entry<String, List<APIResponseDTO>> timeEntry : responseMap.entrySet()) {

                NativeObject api = new NativeObject();
                api.put("apiName", api, timeEntry.getKey());

                NativeArray responseTimes = new NativeArray(0);
                for (APIResponseDTO dto : timeEntry.getValue()) {
                    NativeObject responseData = new NativeObject();


                    serviceTime = dto.getServiceTime();
                    respCount = dto.getResponseCount();
                    avgServiceTime = serviceTime / respCount;

                    String hitDateInMilli = getTimeInMilli(dto.getDate().toString());
                    responseData.put("apiServiceTimes", responseData, serviceTime);
                    responseData.put("apiResponseCount", responseData, respCount);
                    responseData.put("apiAvgServiceTime", responseData, avgServiceTime);
                    responseData.put("apiHitDates", responseData, hitDateInMilli);

                    responseTimes.put(responseTimes.size(), responseTimes, responseData);
                }
                api.put("responseData", api, responseTimes);
                apis.put(i, apis, api);
                i++;
            }

        } catch (Exception e) {
            log.error("Error occured getAllResponseTimes ");
            log.error(e.getStackTrace());
            handleException("Error occurred while populating Response Time graph.", e);
        }

        return apis;
    }

    public static NativeObject jsFunction_getDashboardTimeConsumersByAPI(Context cx, Scriptable thisObj, Object[] args, Function funObj)
            throws APIManagementException {

        String timeRange = args[0].toString();
        String operator = args[1].toString();
        String subscriber = args[2].toString();

        if (operator.equalsIgnoreCase("All")) {
            operator = HostObjectConstants.ALL_OPERATORS;
        } else {
            operator = operator.toUpperCase();
        }
        if (subscriber.equalsIgnoreCase("All")) {
            subscriber = HostObjectConstants.ALL_SUBSCRIBERS;
        }

        Calendar now = Calendar.getInstance();
        String toDate = getCurrentTime(now);
        String fromDate = subtractTimeRange(now, timeRange);

        NativeObject apiConsumpData = new NativeObject();
        NativeArray slowestApis = new NativeArray(0);
        NativeArray chartData = new NativeArray(0);

        try {

            Map<String, String[]> responseMap = SbHostObjectUtils.getTimeConsumptionForAllAPIs(operator, subscriber, fromDate, toDate);
            short i = 0;

            for (Map.Entry<String, String[]> timeEntry : responseMap.entrySet()) {
                NativeObject slowestApiInfo = new NativeObject();
                NativeObject chartDataForApi = new NativeObject();



                String[] data = timeEntry.getValue();
                slowestApiInfo.put("apiName", slowestApiInfo, timeEntry.getKey());
                slowestApiInfo.put("highestAvgConsumption", slowestApiInfo, data[1]);

                chartDataForApi.put("apiName", chartDataForApi, timeEntry.getKey());
                chartDataForApi.put("totalAvgConsumption", chartDataForApi, data[2]);


                slowestApis.put(i, slowestApis, slowestApiInfo);
                chartData.put(i, chartData, chartDataForApi);
                i++;
            }

            apiConsumpData.put("slowestApis", apiConsumpData, slowestApis);
            apiConsumpData.put("chartData", apiConsumpData, chartData);


        } catch (Exception e) {
            log.error("Error occured getAllResponseTimes ");
            log.error(e.getStackTrace());
            handleException("Error occurred while populating Response Time graph.", e);
        }

        return apiConsumpData;
    }
}
