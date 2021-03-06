package org.dialog.custom.hostobjects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dialog.custom.dao.Approval;
import org.dialog.custom.hostobjects.internal.HostObjectComponent;
import org.dialog.custom.hostobjects.southbound.BilledCharge;
import org.dialog.custom.hostobjects.southbound.CategoryCharge;
import org.dialog.custom.hostobjects.util.CommissionPercentagesDTO;
import org.dialog.custom.hostobjects.util.OperatorDetailsEntity;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import org.json.JSONObject;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.Application;
import org.wso2.carbon.apimgt.impl.APIManagerConfiguration;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowConstants;
import org.wso2.carbon.apimgt.usage.client.exception.APIMgtUsageQueryServiceClientException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

import static org.dialog.custom.hostobjects.AxiataDataAccessObject.getApprovedOperatorsByApplication;

public class BillingDataAccessObject {

    private static volatile DataSource statDatasource = null;
    private static volatile DataSource apimgtDatasource = null;
    private static final String API_USAGE_TRACKING = "APIUsageTracking.";
    private static final String STAT_SOURCE_NAME = API_USAGE_TRACKING + "DataSourceName";
    private static final String APIMGT_SOURCE_NAME = "DataSourceName";
    private static final Log log = LogFactory.getLog(BillingDataAccessObject.class);
    private static TableDataUtils db = new TableDataUtils();
    private static String stats_db = db.getStatsDB();
    private static String am_db = db.getAmDB();

    public static void initializeDataSource() throws APIMgtUsageQueryServiceClientException {
        if (statDatasource != null && apimgtDatasource != null) {
            return;
        }
        APIManagerConfiguration config = HostObjectComponent.getAPIManagerConfiguration();
        String statdataSourceName = config.getFirstProperty(STAT_SOURCE_NAME);
        String apimgtdataSourceName = config.getFirstProperty(APIMGT_SOURCE_NAME);

        if (statdataSourceName != null) {
            try {
                Context ctx = new InitialContext();
                statDatasource = (DataSource) ctx.lookup(statdataSourceName);
            } catch (NamingException e) {
                throw new APIMgtUsageQueryServiceClientException(
                        "Error while looking up the data "
                        + "source: "
                        + statdataSourceName);
            }

        }

        if (apimgtdataSourceName != null) {
            try {
                Context ctx = new InitialContext();
                apimgtDatasource = (DataSource) ctx.lookup(apimgtdataSourceName);
            } catch (NamingException e) {
                throw new APIMgtUsageQueryServiceClientException(
                        "Error while looking up the data "
                        + "source: "
                        + apimgtdataSourceName);
            }

        }
    }

    public static void printAPIRequestSummary() throws SQLException, APIManagementException,
            APIMgtUsageQueryServiceClientException {
        String sql = "select * from API_REQUEST_SUMMARY";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                // api| api_version| version | apiPublisher | consumerKey|
                // userId| context | max_request_time |
                // \total_request_count | hostName | year | month | day | time
                System.out.println("=== Results for api :" + rs.getString("api") + " , ck :  "
                        + rs.getString("consumerKey") + " , count : "
                        + rs.getInt("total_request_count"));
            }

        } catch (SQLException e) {
            handleException("Error occurred while querying Request Summary", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }

    }

    public static void printSouthboundTraffic() throws SQLException, APIManagementException,
            APIMgtUsageQueryServiceClientException {
        String sql = "select * from TEST_DB";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            log.debug("Print Southbound Reporting");
            while (rs.next()) {
                log.debug("=== Results for southbound Traffic :" + rs.getString("ENDPOINT") + " , endpoint :  "
                        + rs.getString("CONSUMER_KEY") + " , key : "
                        + rs.getString("TIME_CREATED"));
            }

        } catch (SQLException e) {
            handleException("Error occurred while querying Request Summary", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }

    }

    public static String getResponseTimeForAPI(String apiVersion) throws APIMgtUsageQueryServiceClientException, APIManagementException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "select * from SB_API_RESPONSE_SUMMARY where api_version=? order by time desc limit 1;";
        Map<String, Integer> apiCount = new HashMap<String, Integer>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            log.debug("getResponseTimeForAPI for apiVersion---> " + apiVersion);
            ps.setString(1, apiVersion);
            log.debug("SQL (PS) ---> " + ps.toString());
            log.error("SQL (PS) ---> " + ps.toString());
            results = ps.executeQuery();
            while (results.next()) {
                return results.getString("serviceTime");
            }
        } catch (SQLException e) {
            log.error("SQL Error in getResponseTimeForAPI");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting Invocation count for Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return null;
    }

    public static List<APIResponseDTO> getAllResponseTimesForAPI(String operator, String appId, String apiVersion, String fromDate,
            String toDate) throws APIMgtUsageQueryServiceClientException, APIManagementException {

        String appConsumerKey = "%";
        if (operator.contains("__ALL__")) {
            operator = "%";
        }

        if (!appId.contains("__ALL__")) {
            appConsumerKey = getConsumerKeyByAppId(appId);
        }

        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "select api_version,response_count AS count, serviceTime,STR_TO_DATE(time,'%Y-%m-%d') as date "
                + "FROM SB_API_RESPONSE_SUMMARY WHERE api_version=? AND (time BETWEEN ? AND ?) AND operatorId LIKE ? AND consumerKey LIKE ?;";
        List<APIResponseDTO> responseTimes = new ArrayList<APIResponseDTO>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            log.debug("getAllResponseTimesForAPI for apiVersion---> " + apiVersion);
            ps.setString(1, apiVersion);
            ps.setString(2, fromDate + " 00:00:00");
            ps.setString(3, toDate + " 23:59:59");
            ps.setString(4, operator);
            ps.setString(5, appConsumerKey);
            log.debug("SQL (PS) ---> " + ps.toString());
            results = ps.executeQuery();
            while (results.next()) {
                APIResponseDTO resp = new APIResponseDTO();
                resp.setApiVersion(results.getString("api_version"));
                resp.setResponseCount(results.getInt("count"));
                resp.setServiceTime(results.getInt("serviceTime"));
                resp.setDate(results.getDate("date"));

                responseTimes.add(resp);
            }
        } catch (SQLException e) {
            log.error("SQL Error in getAllResponseTimesForAPI");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting all response times for API", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return responseTimes;
    }

    public static Map<String, Integer> getAPICountsForApplication(String consumerKey, String year,
            String month, String userId)
            throws APIManagementException,
            APIMgtUsageQueryServiceClientException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;

        /*String sql
         = "select api_version,total_request_count as total "
         + "from  API_REQUEST_MONTHLY_SUMMARY where " + "month=? AND "
         + "consumerKey=?  AND " + "userId=?;";
         */
        String sql = "select api_version,sum(response_count) as total"
                + " from SB_API_RESPONSE_SUMMARY where month= ? AND year = ?"
                + " and consumerKey=? and userId=?"
                + " and responseCode like '2%'"
                + " group by api_version;";

        Map<String, Integer> apiCount = new HashMap<String, Integer>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setString(1, month);
            ps.setString(2, year);
            ps.setString(3, consumerKey);
            ps.setString(4, userId);

            results = ps.executeQuery();
            while (results.next()) {
                apiCount.put(results.getString("api_version"), results.getInt("total"));
            }
        } catch (SQLException e) {
            handleException("Error occurred while getting Invocation count for Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }

        return apiCount;
    }

    public static String getApplicationconsumer(Integer applicationid, String keytype)
            throws APIManagementException,
            APIMgtUsageQueryServiceClientException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String consumerKey = null;
        String sql = /*"select api_version,total_request_count as total "
                 + "from  API_REQUEST_MONTHLY_SUMMARY where " + "month=? AND "
                 + "consumerKey=?  AND " + "userId=?;"; */ "select consumer_key from AM_APPLICATION_KEY_MAPPING where key_type = ? and application_id = ?;";

        Map<String, Integer> apiCount = new HashMap<String, Integer>();
        try {
            connection = getApiMgtDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setString(1, keytype);
            ps.setInt(2, applicationid);

            results = ps.executeQuery();
            while (results.next()) {
                consumerKey = results.getString("consumer_key");
            }
        } catch (SQLException e) {
            handleException("Error occurred while getting Invocation count for Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }

        return consumerKey;
    }

    public static String getApplicationName(Integer applicationid, String keytype)
            throws APIManagementException,
            APIMgtUsageQueryServiceClientException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String appName = null;
        String sql = /*"select api_version,total_request_count as total "
                 + "from  API_REQUEST_MONTHLY_SUMMARY where " + "month=? AND "
                 + "consumerKey=?  AND " + "userId=?;"; */ "select consumer_key, name from AM_APPLICATION_KEY_MAPPING ap, AM_APPLICATION am where ap.APPLICATION_ID = am.APPLICATION_ID and key_type = ? and ap.application_id = ?;";

        Map<String, Integer> apiCount = new HashMap<String, Integer>();
        try {
            connection = getApiMgtDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setString(1, keytype);
            ps.setInt(2, applicationid);

            results = ps.executeQuery();
            while (results.next()) {
                appName = results.getString("name");
            }
        } catch (SQLException e) {
            handleException("Error occurred while getting Invocation count for Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }

        return appName;
    }

    public static List<OperatorDetailsEntity> getOperatorDetailsOfSubscription(int applicationId, int apiId) throws APIManagementException,
            APIMgtUsageQueryServiceClientException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT operator_name,rate_id_sb,operation_id FROM " + HostObjectConstants.SUBSCRIPTION_OPCO_RATES_TABLE +
                " WHERE application_id=? AND api_id=? ";

        //Map<String,String> operatorMap = new HashMap<String, String>();
        List<OperatorDetailsEntity> operatorDetails=new ArrayList<OperatorDetailsEntity>();
        try {
            connection = BillingDataAccessObject.getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            log.debug("getOperatorDetailsOfSubscription for applicationId---> " + applicationId + " apiId--> " + apiId);
            ps.setInt(1, applicationId);
            ps.setInt(2, apiId);
            log.debug("SQL (PS) ---> " + ps.toString());
            results = ps.executeQuery();
            while (results.next()) {
                OperatorDetailsEntity detailsEntity=new OperatorDetailsEntity();

                detailsEntity.setRateName(results.getString("rate_id_sb"));
                detailsEntity.setOperationId(results.getInt("operation_id"));
                detailsEntity.setOperatorName(results.getString("operator_name"));

                operatorDetails.add(detailsEntity);

            }
        } catch (SQLException e) {
            log.error("SQL Error in getTaxesForSubscription");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting Taxes for Subscription", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return operatorDetails;
    }
    
    public static List<OperatorDetailsEntity> getDetailsOfSubscription(int applicationId, int apiId) throws APIManagementException,
            APIMgtUsageQueryServiceClientException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT rate_id_nb,operation_id FROM " + HostObjectConstants.SUBSCRIPTION_RATES_TABLE +
                " WHERE application_id=? AND api_id=? ";

        //Map<String,String> operatorMap = new HashMap<String, String>();
        List<OperatorDetailsEntity> operatorDetails=new ArrayList<OperatorDetailsEntity>();
        try {
            connection = BillingDataAccessObject.getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            log.debug("getOperatorDetailsOfSubscription for applicationId---> " + applicationId + " apiId--> " + apiId);
            ps.setInt(1, applicationId);
            ps.setInt(2, apiId);
            log.debug("SQL (PS) ---> " + ps.toString());
            results = ps.executeQuery();
            
            log.debug("REPORTS DEBUG LOGS 00 : ps = "+ps);
            
            while (results.next()) {
                OperatorDetailsEntity detailsEntity=new OperatorDetailsEntity();

                detailsEntity.setRateName(results.getString("rate_id_nb"));
                detailsEntity.setOperationId(Integer.parseInt(results.getString("operation_id")));
                detailsEntity.setOperatorName("__default__");

                operatorDetails.add(detailsEntity);

            }
        } catch (SQLException e) {
            log.error("SQL Error in getTaxesForSubscription");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting Taxes for Subscription", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return operatorDetails;
    }

    public static Map<CategoryCharge,BilledCharge> getAPICountsForSubscription (String consumerKey, short year, short month, String apiName, String apiVersion, String operatorId, int operationId)
            throws APIManagementException, APIMgtUsageQueryServiceClientException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT category,subcategory,sum(response_count) as total, sum(chargeAmount) as totalPayAmount "
                + "from " + HostObjectConstants.SB_RESPONSE_SUMMARY_TABLE
                + " where year=? and month=? and api=? and version=? and consumerKey=? and operatorId=? "
                + "and responseCode like '2%' "
                + "and operationType = ? "
                + "group by category,subcategory";

        
        Map<CategoryCharge,BilledCharge> apiCount =new HashMap<CategoryCharge,BilledCharge>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setShort(1, year);
            ps.setShort(2, month);
            ps.setString(3, apiName);
            ps.setString(4, apiVersion);
            ps.setString(5, consumerKey);
            ps.setString(6, operatorId);
            ps.setInt(7, operationId);

            results = ps.executeQuery();
            
            CategoryCharge categoryCharge = null;
            while (results.next()) {
                categoryCharge = new CategoryCharge(operationId,results.getString("category"),results.getString("subcategory"));                
                apiCount.put(categoryCharge,new BilledCharge(results.getInt("total")));
            }
        } catch (SQLException e) {
            handleException("Error occurred while getting Invocation count for Subscription", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }

        return apiCount;
    }
    
        public static Map<CategoryCharge,BilledCharge> getNorthboundAPICountsForSubscription (String consumerKey, short year, short month, String apiName, String apiVersion,int operationId)
            throws APIManagementException, APIMgtUsageQueryServiceClientException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT category,subcategory,sum(response_count) as total, sum(chargeAmount) as totalPayAmount "
                + "from " + HostObjectConstants.NB_RESPONSE_SUMMARY_TABLE
                + " where year=? and month=? and api=? and version=? and consumerKey=? "
                + "and responseCode like '2%' "
                + "and operationType = ? "
                + "group by category,subcategory";

        
        Map<CategoryCharge,BilledCharge> apiCount =new HashMap<CategoryCharge,BilledCharge>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setShort(1, year);
            ps.setShort(2, month);
            ps.setString(3, apiName);
            ps.setString(4, apiVersion);
            ps.setString(5, consumerKey);
            ps.setInt(6, operationId);

            results = ps.executeQuery();
            log.debug("REPORTS DEBUG LOGS 01 : ps = "+ps);
            CategoryCharge categoryCharge = null;
            while (results.next()) {
                categoryCharge = new CategoryCharge(operationId,results.getString("category"),results.getString("subcategory"));                
                apiCount.put(categoryCharge,new BilledCharge(results.getInt("total")));
            }
        } catch (SQLException e) {
            handleException("Error occurred while getting Invocation count for Subscription", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }

        return apiCount;
    }
    

    public static Map<String, Integer> getAPICountsForApplicationOpco(String consumerKey, String year, String month, String userId, String api)
            throws APIManagementException,
            APIMgtUsageQueryServiceClientException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = /*"select api_version,total_request_count as total "
                 + "from  API_REQUEST_MONTHLY_SUMMARY where " + "month=? AND "
                 + "consumerKey=?  AND " + "userId=?;"; */ "select operatorId,sum(response_count) as total "
                + "from SB_API_RESPONSE_SUMMARY "
                + "where year=? and month=? and api=? and userid like ? and consumerKey like ?"
                + " and responseCode like '2%' "
                + "group by operatorId";

        if (userId.contains("__ALL__")) {
            userId = "%";
        }

        Map<String, Integer> apiCount = new HashMap<String, Integer>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setString(1, year);
            ps.setString(2, month);
            ps.setString(3, api);
            ps.setString(4, userId);
            ps.setString(5, consumerKey);

            results = ps.executeQuery();
            while (results.next()) {
                apiCount.put(results.getString("operatorId"), results.getInt("total"));
            }
        } catch (SQLException e) {
            handleException("Error occurred while getting Invocation count for Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }

        return apiCount;
    }

    public static void printAPISubscriberTable() throws SQLException,
            APIMgtUsageQueryServiceClientException {
        String sql = "select * from AM_SUBSCRIBER";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getApiMgtDBConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                // api| api_version| version | apiPublisher | consumerKey|
                // userId| context | max_request_time |
                // \total_request_count | hostName | year | month | day | time
                System.out.println("=== Results for SUBSCRIBER_ID  :" + rs.getInt("SUBSCRIBER_ID")
                        + " , USER_ID :  " + rs.getString("USER_ID")
                        + " , DATE_SUBSCRIBED : " + rs.getDate("DATE_SUBSCRIBED"));
            }

        } catch (SQLException e) {
            log.error("Error occured while querying Request Summary", e);
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }

    }

    public static Connection getStatsDBConnection() throws SQLException,
            APIMgtUsageQueryServiceClientException {
        initializeDataSource();
        if (statDatasource != null) {
            return statDatasource.getConnection();
        } else {
            throw new SQLException("Statistics Datasource not initialized properly");
        }
    }

    public static Connection getApiMgtDBConnection() throws SQLException,
            APIMgtUsageQueryServiceClientException {
        initializeDataSource();
        if (apimgtDatasource != null) {
            return apimgtDatasource.getConnection();
        } else {
            throw new SQLException("Statistics Datasource not initialized properly");
        }
    }

    public static List<String> getAllSubscriptions() throws APIMgtUsageQueryServiceClientException,
            SQLException {
        String sql = "select USER_ID from AM_SUBSCRIBER";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<String> subscriber = new ArrayList<String>();
        try {
            conn = getApiMgtDBConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                subscriber.add(rs.getString("USER_ID"));
            }

        } catch (SQLException e) {
            log.error("Error occured while querying Request Summary", e);
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return subscriber;
    }

    public static int getSubscriberkey(String userid) throws APIMgtUsageQueryServiceClientException,
            SQLException {
        String sql = "select subscriber_id from AM_SUBSCRIBER WHERE USER_ID = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        int subscriber = 0;
        try {
            conn = getApiMgtDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, userid);
            rs = ps.executeQuery();
            if (rs.next()) {
                subscriber = rs.getInt("subscriber_id");
            }

        } catch (SQLException e) {
            log.error("Error occured while querying Request Summary", e);
            throw e;
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return subscriber;
    }

    private static void handleException(String msg, Throwable t) throws APIManagementException {
        log.error(msg, t);
        throw new APIManagementException(msg, t);
    }

    //Retriving number of Subscribers for each API
    public static int getNoOfSubscribers(String subscriber, String app, String apiName) throws APIMgtUsageQueryServiceClientException, APIManagementException {
        int noOfSubscribers = 0;

        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "select subscriptionCount "
                + "from SUBSCRIPTIONCOUNT where "
                + "userId=? AND "
                + "api=? AND "
                + "applicationName=?;";
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setString(1, subscriber);
            ps.setString(2, apiName);
            ps.setString(3, app);

            results = ps.executeQuery();

            while (results.next()) {
                noOfSubscribers = noOfSubscribers + Integer.parseInt(results.getString("subscriptionCount"));
            }
        } catch (SQLException e) {
            handleException("Error occurred while getting Invocation count for Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }

        return noOfSubscribers;
    }

    //Retriving amount charged from end-user through payment API
    public static Set<PaymentRequestDTO> getPaymentAmounts(short year, short month, String consumerKey,
                                                           String api_version, String operatorId, int operation, String category, String subcategory) throws
            APIMgtUsageQueryServiceClientException, APIManagementException {

        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT api,userId,consumerKey,chargeAmount,category,subcategory,merchantId,time FROM " + HostObjectConstants.SB_RESPONSE_SUMMARY_TABLE +
                " WHERE year=? and month=? and api_version =? and consumerKey=? and operatorId =? and operationType=? and category=? and subcategory=? and responseCode like '2%' AND operatorRef NOT IN " +
                " (SELECT distinct operatorRef FROM "+ HostObjectConstants.SB_RESPONSE_SUMMARY_TABLE + 
                " WHERE year=? and month=? and consumerKey=? and operatorId =? and operationType=101 and responseCode like '2%')";
        Set<PaymentRequestDTO> requestSet = new HashSet<PaymentRequestDTO>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setShort(1, year);
            ps.setShort(2, month);
            ps.setString(3, api_version);
            ps.setString(4, consumerKey);
            ps.setString(5, operatorId);
            ps.setInt(6, operation);
            ps.setString(7, category);
            ps.setString(8, subcategory);
            ps.setShort(9, year);
            ps.setShort(10, month);
            ps.setString(11, consumerKey);
            ps.setString(12, operatorId);


            results = ps.executeQuery();

            while (results.next()) {
                PaymentRequestDTO paymentRequest = new PaymentRequestDTO();
                paymentRequest.setUserId(results.getString("userId"));
                paymentRequest.setConsumerKey(results.getString("consumerKey"));
                String value = results.getString("chargeAmount");
                paymentRequest.setAmount(new BigDecimal(value.replaceAll(",", "")));
                paymentRequest.setCategory(results.getString("category"));
                paymentRequest.setSubcategory(results.getString("subcategory"));
                paymentRequest.setMerchant(results.getString("merchantId"));
                String rqdate = results.getString("time");
                paymentRequest.setDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(rqdate));
                requestSet.add(paymentRequest);
            }
        } catch (SQLException e) {
            handleException("Error occurred while getting Payment API count for Application", e);
        } catch (ParseException ep) {
            handleException("Error occurred while getting Payment API count for Application", ep);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }

        return requestSet;
    }
    
    //Retriving amount charged from end-user through payment API
    public static Set<PaymentRequestDTO> getNbPaymentAmounts(short year, short month, String consumerKey,
                                                           String api_version, int operation, String category, String subcategory) throws
            APIMgtUsageQueryServiceClientException, APIManagementException {

        
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT api,userId,consumerKey,chargeAmount,category,subcategory,merchantId,time FROM " + HostObjectConstants.NB_RESPONSE_SUMMARY_TABLE +
                " WHERE year=? and month=? and api_version =? and consumerKey=? and operationType=? and category=? and subcategory=? and responseCode like '2%' AND operatorRef NOT IN " +
                " (SELECT distinct operatorRef from " + HostObjectConstants.NB_RESPONSE_SUMMARY_TABLE +
                " WHERE year=? and month=? and consumerKey=? and operationType=101 and responseCode like '2%')";
       
        Set<PaymentRequestDTO> requestSet = new HashSet<PaymentRequestDTO>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setShort(1, year);
            ps.setShort(2, month);
            ps.setString(3, api_version);
            ps.setString(4, consumerKey);
            ps.setInt(5, operation);
            ps.setString(6, category);
            ps.setString(7, subcategory);
            ps.setShort(8, year);
            ps.setShort(9, month);
            ps.setString(10, consumerKey);                        

            results = ps.executeQuery();

            while (results.next()) {
                PaymentRequestDTO paymentRequest = new PaymentRequestDTO();
                paymentRequest.setUserId(results.getString("userId"));
                paymentRequest.setConsumerKey(results.getString("consumerKey"));
                String value = results.getString("chargeAmount");
                paymentRequest.setAmount(new BigDecimal(value.replaceAll(",", "")));
                paymentRequest.setCategory(results.getString("category"));
                paymentRequest.setSubcategory(results.getString("subcategory"));
                paymentRequest.setMerchant(results.getString("merchantId"));
                String rqdate = results.getString("time");
                paymentRequest.setDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(rqdate));
                requestSet.add(paymentRequest);
            }
        } catch (SQLException e) {
            handleException("Error occurred while getting Payment API count for Application", e);
        } catch (ParseException ep) {
            handleException("Error occurred while getting Payment API count for Application", ep);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }

        return requestSet;
    }

    public static int getApiId(APIIdentifier apiIdent) throws APIMgtUsageQueryServiceClientException,
            APIManagementException {
        Connection conn = null;
        int apiId = -1;
        try {
            conn = getApiMgtDBConnection();
            apiId = ApiMgtDAO.getAPIID(apiIdent, conn);
        } catch (SQLException e) {
            handleException("Error occured while getting API ID of API: " + apiIdent + " from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(null, conn, null);
        }
        return apiId;
    }

    public static int getSubscriptionIdForApplicationAPI(int appId, APIIdentifier apiIdent) throws
            APIMgtUsageQueryServiceClientException, APIManagementException {
        String sql = "select SUBSCRIPTION_ID from AM_SUBSCRIPTION where APPLICATION_ID=? AND API_ID=?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<String> subscriber = new ArrayList<String>();
        int subscriptionId = -1;
        try {
            conn = getApiMgtDBConnection();
            int apiId = ApiMgtDAO.getAPIID(apiIdent, conn);
            ps = conn.prepareStatement(sql);
            ps.setInt(1, appId);
            ps.setInt(2, apiId);
            rs = ps.executeQuery();
            if (rs.next()) {
                subscriptionId = rs.getInt("SUBSCRIPTION_ID");
            }
            if (subscriptionId == -1) {
                String msg = "Unable to find the subscription ID for API: " + apiIdent + " in the database";
                log.error(msg);
                throw new APIManagementException(msg);
            }

        } catch (SQLException e) {
            handleException("Error occured while getting subscription ID for API: " + apiIdent + " from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return subscriptionId;
    }

    public static List<String[]> getTotalAPITrafficForPieChart(String fromDate, String toDate, String subscriber, String operator, int applicationId) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        String consumerKey = null;
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }
        if (applicationId == 0) {
            consumerKey = "%";
        } else {
            consumerKey = AMDataAccessObject.getConsumerKeyByApplication(applicationId);
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT api, SUM(response_count) AS api_request_count FROM SB_API_RESPONSE_SUMMARY WHERE userId LIKE ? AND consumerKey LIKE ? AND operatorId LIKE ? AND (STR_TO_DATE(SB_API_RESPONSE_SUMMARY.time, '%Y-%m-%d') BETWEEN  STR_TO_DATE(?, '%Y-%m-%d') AND STR_TO_DATE(?, '%Y-%m-%d')) GROUP BY  api";
        List<String[]> api_request = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, subscriber);
            ps.setString(2, consumerKey);
            ps.setString(3, operator);
            ps.setString(4, fromDate);
            ps.setString(5, toDate);
            log.debug("getTotalTrafficForPieChart");
            results = ps.executeQuery();
            while (results.next()) {
                String[] temp = {results.getString("api"), results.getString("api_request_count")};
                api_request.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting total traffic for pie chart from the database" + e);
            handleException("Error occured while getting total traffic for pie chart from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return api_request;
    }

    public static List<String[]> getTotalAPITrafficForHistogram(String fromDate, String toDate, String subscriber, String operator, int applicationId, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        String consumerKey = null;
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }
        if (applicationId == 0) {
            consumerKey = "%";
        } else {
            consumerKey = AMDataAccessObject.getConsumerKeyByApplication(applicationId);
        }
        if (api.equals("__ALL__")) {
            api = "%";
        }

        if (consumerKey == null) {
            return null;
        }
        List<String[]> api_list = AMDataAccessObject.getAPIListForAPITrafficHistogram(fromDate, toDate, api);

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "select api,date(time) as date, sum(response_count) hits from SB_API_RESPONSE_SUMMARY\n"
                + "where DATE(time) between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND operatorId LIKE ? AND userId LIKE ? AND API LIKE ? AND consumerKey LIKE ? \n"
                + "group by api, date";
        List<String[]> api_request = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            ps.setString(3, operator);
            ps.setString(4, subscriber);
            ps.setString(5, api);
            ps.setString(6, consumerKey);

            log.debug("getTotalTrafficForHistogram");
            results = ps.executeQuery();
            while (results.next()) {
                String[] temp = {results.getString(1), results.getDate(2).toString(), results.getString(3)};
                api_request.add(temp);
            }

            for (int i = 0; i < api_request.size(); i++) {
                String apiRequestNameNDate = api_request.get(i)[0].toString() + "_" + api_request.get(i)[1].toString();
                for (int j = 0; j < api_list.size(); j++) {
                    String apiNameNDate = api_list.get(j)[0].toString() + "_" + api_list.get(j)[1].toString();
                    if (apiRequestNameNDate.equals(apiNameNDate)) {
                        api_list.get(j)[2] = api_request.get(i)[2];
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Error occured while getting total traffic for histogram from the database" + e);
            handleException("Error occured while getting total traffic for histogram from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return api_list;
    }

    public static List<String[]> getOperatorWiseAPITrafficForPieChart(String fromDate, String toDate, String subscriber, String api, int applicationId) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        String consumerKey = null;
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (api.equals("__ALL__")) {
            api = "%";
        }
        if (applicationId == 0) {
            consumerKey = "%";
        } else {
            consumerKey = AMDataAccessObject.getConsumerKeyByApplication(applicationId);
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT operatorId, SUM(response_count) AS api_request_count "
                + "FROM SB_API_RESPONSE_SUMMARY "
                + "WHERE userId LIKE ? AND consumerKey LIKE ? AND api LIKE ? AND (STR_TO_DATE(SB_API_RESPONSE_SUMMARY.time, '%Y-%m-%d') BETWEEN  STR_TO_DATE(?, '%Y-%m-%d') AND STR_TO_DATE(?, '%Y-%m-%d')) "
                + "GROUP BY operatorId";
        List<String[]> api_request = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, subscriber);
            ps.setString(2, consumerKey);
            ps.setString(3, api);
            ps.setString(4, fromDate);
            ps.setString(5, toDate);
            log.debug("getOperarorWiseAPITrafficForPieChart");
            results = ps.executeQuery();
            while (results.next()) {
                String[] temp = {results.getString("operatorId"), results.getString("api_request_count")};
                api_request.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting opertaor wise api traffic for pie chart from the database" + e);
            handleException("Error occured while getting opertaor wise api traffic for pie chart from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return api_request;
    }

    public static List<String[]> getApprovalHistory(String fromDate, String toDate, String subscriber, String api, int applicationId, String operator) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String sql = "select application_id,name,if(description is null,'Not Specified',description) as description,"
                + "ELT(FIELD(application_status,'CREATED','APPROVED','REJECTED'),'PENDING APPROVE','APPROVED','REJECTED') as app_status "
                + "from AM_APPLICATION "
                + "where application_id like ? and subscriber_id like ?";

        List<String[]> applist = new ArrayList<String[]>();

        if (!subscriber.equals("__ALL__")) {
            subscriber = String.valueOf(getSubscriberkey(subscriber));
        }

        if (operator == null) {
            operator = "__ALL__";
        }

        try {
            conn = getApiMgtDBConnection();
            ps = conn.prepareStatement(sql);
            if (applicationId == 0) {
                ps.setString(1, "%");
            } else {
                ps.setInt(1, applicationId);
            }

            if (subscriber.equals("__ALL__")) {
                ps.setString(2, "%");
            } else {
                ps.setInt(2, Integer.parseInt(subscriber));
            }

            log.debug("getOperarorWiseAPITrafficForPieChart");
            rs = ps.executeQuery();
            while (rs.next()) {
                
                //Does not consider default application
                if (!rs.getString("name").equalsIgnoreCase("DefaultApplication")) {
                    //get operator list
                    String operators = getApprovedOperatorsByApplication(rs.getInt("application_id"), operator);
                    if (operator.equals("__ALL__")) {
                        String[] temp = {String.valueOf(rs.getInt("application_id")), rs.getString("name"), rs.getString("description"), rs.getString("app_status"), operators};
                        applist.add(temp);
                    } else {
                        if (operators.contains(operator)) {
                            String[] temp = {String.valueOf(rs.getInt("application_id")), rs.getString("name"), rs.getString("description"), rs.getString("app_status"), operators};
                            applist.add(temp);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting opertaor wise api traffic for pie chart from the database" + e);
            handleException("Error occured while getting opertaor wise api traffic for pie chart from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return applist;
    }

    public static List<Approval> getApprovalHistoryApp(int applicationId, String operatorid) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        /*String sql = "select application_id, type, name, operatorid, isactive,tier_id,api_name, api_version from ("+
         "select application_id, 1 as type, name,'0' as operatorid, if(application_status='CREATED',0,1) as isactive,'' as tier_id,'' as api_name,'' as api_version from am_application "+
         "union all select applicationid as application_id ,2 as type, 'APPO' as name, operatorid, isactive,'' as tier_id,'' as api_name,'' as api_version from axiatadb.operatorapps where operatorid like ? "+
         "union all select application_id, 3 as type,'SUBS' as name,'0' as operatorid, if(sub_status='ON_HOLD',0,1) isactive, tier_id,api_name, api_version from am_subscription, am_api where am_subscription.api_id = am_api.api_id "+
         "union all (select applicationid as application_id, 4 as type, 'SUBO' as name, openp.operatorid as operatorid, enp.isactive as isactive,'' as tier_id, openp.api as api_name,'' as api_version from axiatadb.endpointapps enp,axiatadb.operatorendpoints openp where "+
         "enp.endpointid = openp.id and openp.operatorid like ?) "+
         ") a where application_id like ? "+
         "order by application_id, type,api_name, operatorid"; */

        String sql = "SELECT application_id, sub_status,tier_id, api_name,api_version "
                + "FROM AM_SUBSCRIPTION, AM_API WHERE AM_SUBSCRIPTION.api_id = AM_API.api_id "
                + "AND application_id = ?";

        List<Approval> applist = new ArrayList<Approval>();

        try {

            //populate application
            ApiMgtDAO apiMgtDAO = new ApiMgtDAO();
            Application application = apiMgtDAO.getApplicationById(applicationId);
            
            String appstatus = application.getStatus();
            
            applist.add(new Approval(String.valueOf(applicationId), "1", application.getName(), 0, appstatus, "", "", "", null, null));

            AxiataDataAccessObject.fillOperatorTrace(applicationId, operatorid, applist);

            conn = getApiMgtDBConnection();
            ps = conn.prepareStatement(sql);

            ps.setInt(1, applicationId);

            log.debug("getOperarorWiseAPITrafficForPieChart");
            rs = ps.executeQuery();
            while (rs.next()) {
                Approval temp = new Approval(rs.getString("application_id"), "3", rs.getString("api_name"), 0,
                        rs.getString("sub_status"), rs.getString("tier_id"), rs.getString("api_name"), rs.getString("api_version"), null, null);
                applist.add(temp);
            }

        } catch (Exception e) {
            System.out.println("Error occured while getting opertaor wise api traffic for pie chart from the database" + e);
            handleException("Error occured while getting opertaor wise api traffic for pie chart from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return applist;
    }
    
    public static List<String[]> getAllOperationTypes() throws APIManagementException{
        
        List<String[]> txTypes = new ArrayList<String[]>();
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT `operation_id`, `operation` FROM api_operation_types";
        
        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            log.debug("getAllOperationTypes");
            results = ps.executeQuery();
            while(results.next()){
                String[] temp = {results.getString(1), results.getString(2)};
                txTypes.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting All Operation Types from the database" + e);
            handleException("Error occured while getting All Operation Types from the database", e);
        }finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return txTypes;
    }

    public static List<String[]> getAllAPIs(String fromDate, String toDate, String subscriber, String operator, int applicationId, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        String consumerKey = null;
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }
        if (applicationId == 0) {
            consumerKey = "%";
        } else {
            consumerKey = AMDataAccessObject.getConsumerKeyByApplication(applicationId);
        }
        if (api.equals("__ALL__")) {
            api = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT DISTINCT api FROM SB_API_RESPONSE_SUMMARY WHERE userId LIKE ? AND consumerKey LIKE ? AND operatorId LIKE ? AND api LIKE ? AND (STR_TO_DATE(SB_API_RESPONSE_SUMMARY.time, '%Y-%m-%d') BETWEEN  STR_TO_DATE(?, '%Y-%m-%d') AND STR_TO_DATE(?, '%Y-%m-%d'))";
        List<String[]> apis = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, subscriber);
            ps.setString(2, consumerKey);
            ps.setString(3, operator);
            ps.setString(4, api);
            ps.setString(5, fromDate);
            ps.setString(6, toDate);
            log.debug("getAllAPIs");
            results = ps.executeQuery();
            while (results.next()) {
                String[] temp = {results.getString("api")};
                apis.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting all APIs from the database" + e);
            handleException("Error occured while getting all APIs from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return apis;
    }

    public static List<String[]> getAllErrorResponseCodes(String fromDate, String toDate, String subscriber, String operator, int applicationId, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        String consumerKey = null;
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }
        if (applicationId == 0) {
            consumerKey = "%";
        } else {
            consumerKey = AMDataAccessObject.getConsumerKeyByApplication(applicationId);
        }
        if (api.equals("__ALL__")) {
            api = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT DISTINCT IFNULL(exceptionId, 'SVC1000') AS exceptionId FROM SB_API_RESPONSE_SUMMARY \n"
                + "WHERE userId LIKE ? AND consumerKey LIKE ? AND operatorId LIKE ? AND api LIKE ? AND (STR_TO_DATE(SB_API_RESPONSE_SUMMARY.time, '%Y-%m-%d') BETWEEN  STR_TO_DATE(?, '%Y-%m-%d') \n"
                + "AND STR_TO_DATE(?, '%Y-%m-%d')) AND responseCode NOT IN ('200' , '201', '202', '204')";
        List<String[]> resCodes = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, subscriber);
            ps.setString(2, consumerKey);
            ps.setString(3, operator);
            ps.setString(4, api);
            ps.setString(5, fromDate);
            ps.setString(6, toDate);
            log.debug("getAllErrorResponseCodes");
            results = ps.executeQuery();
            while (results.next()) {
                String[] temp = {results.getString("exceptionId")};
                resCodes.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting all error response codes from the database" + e);
            handleException("Error occured while getting all all error response codes from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return resCodes;
    }

    /*public static List<String[]> getAPIWiseTrafficForHistogram(String fromDate, String toDate, String subscriber, String operator, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
     if (subscriber.equals("__ALL__")) {
     subscriber = "%";
     }
     if (operator.equals("__ALL__")) {
     operator = "%";
     }
     if (api.equals("__ALL__")) {
     api = "%";
     }

     Connection conn = null;
     PreparedStatement ps = null;
     ResultSet results = null;
     String sql = "select a.API_NAME, a.Date, IFNULL(b.HITS,0) HITS "
     + "from (select aa.API_NAME, a.Date from "+am_db+".am_api aa cross join (select DATE(?) - INTERVAL (a.a + (10 * b.a) + (100 * c.a)) DAY as Date "
     + "from (select 0 as a union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) as a "
     + "cross join (select 0 as a union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) as b "
     + "cross join (select 0 as a union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) as c ) a "
     + "where a.Date between ? and ? AND aa.API_NAME LIKE ?) a left join (select api, SUM(response_count) HITS, time from SB_API_RESPONSE_SUMMARY "
     + "where DATE(time) between DATE(?) and DATE(?) AND operatorId LIKE ? AND userId LIKE ? "
     + "group by DATE(time),api) b on (a.API_NAME = b.api AND a.Date = DATE(b.time));";
     List<String[]> api_request = new ArrayList<String[]>();

     try {
     conn = getStatsDBConnection();
     ps = conn.prepareStatement(sql);
     ps.setString(1, toDate);
     ps.setString(2, fromDate);
     ps.setString(3, toDate);
     ps.setString(4, api);
     ps.setString(5, fromDate);
     ps.setString(6, toDate);
     ps.setString(7, operator);
     ps.setString(8, subscriber);
     log.debug("getAPIWiseTrafficForHistogram");
     results = ps.executeQuery();
     while (results.next()) {
     String[] temp = {results.getString(1), results.getDate(2).toString(), results.getString(3)};
     api_request.add(temp);
     }
     } catch (Exception e) {
     System.out.println("Error occured while getting API wise traffic for histogram from the database" + e);
     handleException("Error occured while getting API wise traffic for histogram from the database", e);
     } finally {
     APIMgtDBUtil.closeAllConnections(ps, conn, results);
     }
     return api_request;
     } */
    public static List<String[]> getCustomerCareReportData(String fromDate, String toDate, String msisdn, String subscriber, String operator, String app, String api, String startLimit, String endLimit, String timeOffset) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {

        String consumerKey = "";

        if (subscriber.equalsIgnoreCase("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equalsIgnoreCase("__ALL__")) {
            operator = "%";
        }
        if ((app.equalsIgnoreCase("__ALL__")) || (app.equalsIgnoreCase("0"))) {
            consumerKey = "%";
        } else {
            consumerKey = getConsumerKeyByAppId(app);
        }
        if (api.equalsIgnoreCase("__ALL__")) {
            api = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT x.time, x.jsonBody, x.api FROM SB_API_RESPONSE_SUMMARY x WHERE STR_TO_DATE(x.time,'%Y-%m-%d') between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND operatorId LIKE ? AND userid LIKE ? AND api LIKE ? AND consumerKey LIKE ? AND msisdn LIKE ? LIMIT "+startLimit+" ,"+endLimit;
        List<String[]> api_request_data = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            ps.setString(3, operator);
            ps.setString(4, subscriber);
            ps.setString(5, api);
            ps.setString(6, consumerKey);
            ps.setString(7, "%" + msisdn);
            //ps.setString(8, startLimit);
            //ps.setString(9, endLimit);

            log.debug("getCustomerCareReportData");
            results = ps.executeQuery();
            while (results.next()) {
                String localTime = convertToLocalTime(timeOffset, results.getString(1));
                String[] temp = {localTime, results.getString(2), results.getString(3)};
                api_request_data.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting API wise traffic for report (Charging) from the database" + e);
            handleException("Error occured while getting API wise traffic for report (Charging) from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return api_request_data;
    }
    
     public static String getCustomerCareReportDataCount(String fromDate, String toDate, String msisdn, String subscriber, String operator, String app, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {

        String consumerKey = "";
        String count = "";

        if (subscriber.equalsIgnoreCase("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equalsIgnoreCase("__ALL__")) {
            operator = "%";
        }
        if ((app.equalsIgnoreCase("__ALL__")) || (app.equalsIgnoreCase("0"))) {
            consumerKey = "%";
        } else {
            consumerKey = getConsumerKeyByAppId(app);
        }
        if (api.equalsIgnoreCase("__ALL__")) {
            api = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT COUNT(*) FROM SB_API_RESPONSE_SUMMARY x WHERE STR_TO_DATE(x.time,'%Y-%m-%d') between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND operatorId LIKE ? AND userid LIKE ? AND api LIKE ? AND consumerKey LIKE ? AND msisdn LIKE ?";
        List<String[]> api_request_data = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            ps.setString(3, operator);
            ps.setString(4, subscriber);
            ps.setString(5, api);
            ps.setString(6, consumerKey);
            ps.setString(7, "%" + msisdn);

            log.debug("getCustomerCareReportData");
            results = ps.executeQuery();
            results.next();
            count = results.getString(1);
        } catch (Exception e) {
            System.out.println("Error occured while getCustomerCareReportDataCount" + e);
            handleException("Error occured while getCustomerCareReportDataCount", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return count;
    }

    public static List<String[]> getAPIWiseTrafficForReport(String fromDate, String toDate, String subscriber, String operator, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }
        if (api.equals("__ALL__")) {
            api = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
//        String sql = "SELECT `userid`, `api`, `response_count`, `responseCode`, `jsonBody`, `resourcePath`, `method`\n"
//                + "FROM SB_API_RESPONSE_SUMMARY\n"
//                + "WHERE STR_TO_DATE(time,'%Y-%m-%d') between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND operatorId LIKE ? AND userid LIKE ? AND api LIKE ?";

        String sql = "SELECT time, userId, operatorId, requestId, msisdn, response_count, responseCode, jsonBody, resourcePath, method,api "
                + "FROM SB_API_RESPONSE_SUMMARY "
                + "WHERE STR_TO_DATE(time,'%Y-%m-%d') between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND operatorId LIKE ? AND userid LIKE ? AND api LIKE ?";

        List<String[]> api_request = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            ps.setString(3, operator);
            ps.setString(4, subscriber);
            ps.setString(5, api);
            log.debug("getAPIWiseTrafficForReport");
            results = ps.executeQuery();
            while (results.next()) {
                
                String dateTime = results.getString(1);
                if(dateTime == null){
                    dateTime = "";
                }
                
                String jsonBody = results.getString(8);
                String requestUrl = results.getString(9);
                String requestMethod = results.getString(10);
                String requestapi = results.getString(11);

                String event_type = "";
                String clientCorelator = "";

                if (!jsonBody.isEmpty()) {
                    
                    try {
                        JSONObject homejson = new JSONObject(jsonBody);
                        if (!homejson.isNull("outboundSMSMessageRequest")) {
                            JSONObject smsTransactionObject = (JSONObject) homejson.get("outboundSMSMessageRequest");
                            if (!smsTransactionObject.isNull("clientCorrelator")) {
                                clientCorelator = smsTransactionObject.getString("clientCorrelator");
                            }
                        }
                    } catch(Exception ex){
                        System.out.println("Unable to read JSON body stored in DB :: "+ex);
                        clientCorelator = "";
                    }
                    
                }

                if (!requestUrl.isEmpty() && !requestapi.isEmpty()) {
                    String apitype = findAPIType(requestUrl ,requestapi);
                    if (apitype.equalsIgnoreCase("send_sms")) {
                        event_type = "Outbound";
                    } else if (apitype.equalsIgnoreCase("retrive_sms_subscriptions")) {
                        if (requestMethod.equalsIgnoreCase("DELETE")) {
                            event_type = "CancelReceiptService";
                        } else {
                            event_type = "ReceiptService";
                        }
                    } else if (apitype.equalsIgnoreCase("retrive_sms")) {
                        event_type = "Inbound ";
                    } else if (apitype.equalsIgnoreCase("payment")) {
                        event_type = "Charge";
                    } else if (apitype.equalsIgnoreCase("ussd_send")) {
                        event_type = "USSD Outbound";
                    }else if (apitype.equalsIgnoreCase("ussd_subscription")) {
                        event_type = "USSD Subscription";
                    }else if (apitype.equalsIgnoreCase("stop_ussd_subscription")) {
                        if (requestMethod.equalsIgnoreCase("DELETE")) {
                            event_type = "Stop ussd subscription";
                        };
                    }else if (apitype.equalsIgnoreCase("location")) {
                        event_type = "Location";
                    }
                }

                //String[] temp = {results.getString(1), event_type, clientCorelator, results.getString(3), results.getString(4)};
                String[] temp = {dateTime, results.getString(2), results.getString(3), event_type, results.getString(4), clientCorelator, results.getString(5), results.getString(6), results.getString(7)};
                api_request.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting API wise traffic for report from the database" + e);
            handleException("Error occured while getting API wise traffic for report from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return api_request;
    }

    //Hira Created
    public static ResultSet getTxLogData(String fromDate, String toDate, String subscriber, String operator, int opType, String dataType)
    		throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT *"
                + "FROM SB_API_RESPONSE_SUMMARY "
                + "WHERE STR_TO_DATE(time,'%Y-%m-%d') between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND operatorId LIKE ? AND userid LIKE ? AND operationType LIKE ?";
        
        if(dataType.equals("1")){
            sql = sql+" AND (responseCode = 201 OR responseCode = 200)";
        } else if (dataType.equals("2")){
            sql = sql+" AND (responseCode != 201 AND responseCode != 200)";
        }

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            ps.setString(3, operator);
            ps.setString(4, subscriber);
            ps.setInt(5, opType);
            log.debug("getAPIWiseTrafficForReport");
            results = ps.executeQuery();
            
        } catch (Exception e) {
            System.out.println("Error occured while getting API wise traffic for report from the database" + e);
            handleException("Error occured while getting API wise traffic for report from the database", e);
        } finally {
            //APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return results;
    }

    //Hira Created
    public static ResultSet getTxLogDataNB(String fromDate, String toDate, String subscriber, String operator, int opType, String dataType)
    		throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT *"
                + "FROM NB_API_RESPONSE_SUMMARY "
                + "WHERE STR_TO_DATE(time,'%Y-%m-%d') between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND userid LIKE ? AND operationType LIKE ?";
        
        if(dataType.equals("1")){
            sql = sql+" AND (responseCode = 201 OR responseCode = 200)";
        } else if (dataType.equals("2")){
            sql = sql+" AND (responseCode != 201 AND responseCode != 200)";
        }

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            ps.setString(3, subscriber);
            ps.setInt(4, opType);
            log.debug("getAPIWiseTrafficForReport");
            results = ps.executeQuery();
            
        } catch (Exception e) {
            System.out.println("Error occured while getting API wise traffic for report from the database" + e);
            handleException("Error occured while getting API wise traffic for report from the database", e);
        } finally {
            //APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return results;
    }
    
    public static List<String[]> getAPIWiseTrafficForReportCharging(String fromDate, String toDate, String subscriber, String operator, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }
        if (api.equals("__ALL__")) {
            api = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
//        String sql = "SELECT x.time,x.userId, x.consumerKey, x.api,x.requestId, x.operatorRef, x.chargeAmount, x. responseCode, x.jsonBody, x.resourcePath, x.method "
//                + "FROM SB_API_RESPONSE_SUMMARY x "
//                + "WHERE STR_TO_DATE(time,'%Y-%m-%d') between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND operatorId LIKE ? AND userid LIKE ? AND api LIKE ?;";

        String sql = "SELECT time, userId, operatorId, requestId, msisdn, chargeAmount, responseCode, jsonBody, resourcePath, method, purchaseCategoryCode,api "
                + "FROM SB_API_RESPONSE_SUMMARY "
                + "WHERE STR_TO_DATE(time,'%Y-%m-%d') between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND operatorId LIKE ? AND userid LIKE ? AND api LIKE ?;";

        List<String[]> api_request = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            ps.setString(3, operator);
            ps.setString(4, subscriber);
            ps.setString(5, api);

            log.debug("getAPIWiseTrafficForReportCharging");
            results = ps.executeQuery();
            while (results.next()) {
                String jsonBody = results.getString(8);
                String requestUrl = results.getString(9);
                String requestMethod = results.getString(10);
                String requestapi = results.getString(12);
                
                String dateTime = results.getString(1);
                if(dateTime == null){
                    dateTime = "";
                }

                String msisdn = "";
                String clientCorelator = "";
                String currency = "";
                String event_type = "";

                if (!jsonBody.isEmpty()) {
                    try {
                        
                    JSONObject homejson = new JSONObject(jsonBody);
                    if (!homejson.isNull("amountTransaction")) {
                        JSONObject transactionObject = (JSONObject) homejson.get("amountTransaction");
                        if (!transactionObject.isNull("endUserId")) {
                            msisdn = transactionObject.getString("endUserId");
                        }
                        if (!transactionObject.isNull("clientCorrelator")) {
                            clientCorelator = transactionObject.getString("clientCorrelator");
                        }
                        if (!transactionObject.isNull("paymentAmount")) {
                            JSONObject paymentAmountoObj = (JSONObject) transactionObject.get("paymentAmount");
                            if (!paymentAmountoObj.isNull("chargingInformation")) {
                                JSONObject chargingInfoObj = (JSONObject) paymentAmountoObj.get("chargingInformation");
                                if (!chargingInfoObj.isNull("currency")) {
                                    currency = chargingInfoObj.getString("currency");
                                }
                            }
                        }
                    }
                    
                    } catch(Exception ex){
                        System.out.println("Unable to read JSON body stored in DB :: "+ex);
                        clientCorelator = "";
                    }
                    
                }

                if (!requestUrl.isEmpty()) {
                    String apitype = findAPIType(requestUrl ,requestapi);
                    if (apitype.equalsIgnoreCase("send_sms")) {
                        event_type = "Outbound";
                    } else if (apitype.equalsIgnoreCase("retrive_sms_subscriptions")) {
                        if (requestMethod.equalsIgnoreCase("DELETE")) {
                            event_type = "CancelReceiptService";
                        } else {
                            event_type = "ReceiptService";
                        }
                    } else if (apitype.equalsIgnoreCase("retrive_sms")) {
                        event_type = "Inbound ";
                    } else if (apitype.equalsIgnoreCase("payment")) {
                        event_type = "Charge";
                    } else if (apitype.equalsIgnoreCase("location")) {
                        event_type = "Location";
                    }
                }

//                String consumerKey = results.getString(3);
//                String[] temp = {results.getString(1), results.getString(2), getAppNameByConsumerKey(consumerKey) , event_type, results.getString(5), results.getString(6), clientCorelator, results.getString(7), msisdn, currency, results.getString(8)};
                String[] temp = {dateTime, results.getString(2), results.getString(3), event_type, results.getString(4), clientCorelator, results.getString(5), results.getString(6), currency, results.getString(7), results.getString(11)};
                api_request.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting API wise traffic for report (Charging) from the database" + e);
            handleException("Error occured while getting API wise traffic for report (Charging) from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return api_request;
    }

    public static List<String[]> getErrorResponseCodesForPieChart(String fromDate, String toDate, String subscriber, String operator, int applicationId, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        String consumerKey = null;
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }
        if (applicationId == 0) {
            consumerKey = "%";
        } else {
            consumerKey = AMDataAccessObject.getConsumerKeyByApplication(applicationId);
        }
        if (api.equals("__ALL__")) {
            api = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT IFNULL(exceptionId,'SVC1000') AS exceptionId, SUM(response_count) AS api_response_count \n"
                + "FROM SB_API_RESPONSE_SUMMARY \n"
                + "WHERE userId LIKE ? AND consumerKey LIKE ? AND operatorId LIKE ? AND (STR_TO_DATE(SB_API_RESPONSE_SUMMARY.time, '%Y-%m-%d') BETWEEN  STR_TO_DATE(?, '%Y-%m-%d') AND STR_TO_DATE(?, '%Y-%m-%d')) AND api LIKE ? AND responseCode NOT IN ('200' , '201', '202', '204') \n"
                + "GROUP BY exceptionId";
        List<String[]> api_response_codes = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, subscriber);
            ps.setString(2, consumerKey);
            ps.setString(3, operator);
            ps.setString(4, fromDate);
            ps.setString(5, toDate);
            ps.setString(6, api);
            log.debug("getErrorResponseCodesForPieChart");
            results = ps.executeQuery();
            while (results.next()) {
                String[] temp = {results.getString("exceptionId"), results.getString("api_response_count")};
                api_response_codes.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting error response codes for pie chart from the database" + e);
            handleException("Error occured while getting error response codes for pie chart from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return api_response_codes;
    }

    public static List<String[]> getErrorResponseCodesForHistogram(String fromDate, String toDate, String subscriber, String operator, int applicationId, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        String consumerKey = null;
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }
        if (applicationId == 0) {
            consumerKey = "%";
        } else {
            consumerKey = AMDataAccessObject.getConsumerKeyByApplication(applicationId);
        }
        if (api.equals("__ALL__")) {
            api = "%";
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "select a.exceptionId, a.Date, IFNULL(b.HITS,0) HITS\n"
                + "from (select IFNULL(aa.exceptionId, 'SVC1000') exceptionId, a.Date from SB_API_RESPONSE_SUMMARY aa cross join (select DATE(?) - INTERVAL (a.a + (10 * b.a) + (100 * c.a)) DAY as Date \n"
                + "from (select 0 as a union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) as a \n"
                + "cross join (select 0 as a union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) as b \n"
                + "cross join (select 0 as a union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) as c ) a \n"
                + "where a.Date between ? and ? AND aa.responseCode NOT IN ('200' , '201', '202', '204') group by a.date, exceptionId) a \n"
                + "left join (SELECT IFNULL(exceptionId,'SVC1000') exceptionId, SUM(response_count) HITS, time FROM SB_API_RESPONSE_SUMMARY\n"
                + "WHERE DATE(time) between DATE(?) and DATE(?) AND operatorId LIKE ? AND userId LIKE ? AND API LIKE ? AND consumerKey LIKE ? \n"
                + "AND responseCode NOT IN ('200' , '201', '202', '204')\n"
                + "GROUP BY exceptionId, DATE(time)) b on (a.Date = DATE(b.time) and a.exceptionId = b.exceptionId)";
        List<String[]> api_response_codes = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, toDate);
            ps.setString(2, fromDate);
            ps.setString(3, toDate);
            ps.setString(4, fromDate);
            ps.setString(5, toDate);
            ps.setString(6, operator);
            ps.setString(7, subscriber);
            ps.setString(8, api);
            ps.setString(9, consumerKey);
            log.debug("getErrorResponseCodesForHistogram");
            results = ps.executeQuery();
            while (results.next()) {
                String[] temp = {results.getString(1), results.getDate(2).toString(), results.getString(3)};
                api_response_codes.add(temp);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting error response codes for histogram from the database" + e);
            handleException("Error occured while getting error response codes for histogram from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return api_response_codes;
    }

    private static String findAPIType(String ResourceURL , String requested_api) {

        String apiType = null;

        String paymentKeyString = "transactions";
        String outboundkeyString = "outbound";
        String sendSMSkeyStringRequest = "requests";
        String retriveSMSString = "inbound";
        String subscriptionKeyString = "subscriptions";
        String regKeyString = "registrations";
        String delivaryInfoKeyString = "deliveryInfos";
        String delivaryNotifyString = "DeliveryInfoNotification";
        String locationString = "location";
        String ussdKeyString = "ussd";

        String lastWord = ResourceURL.substring(ResourceURL.lastIndexOf("/") + 1);

        if (ResourceURL.toLowerCase().contains(outboundkeyString.toLowerCase())
                && ResourceURL.toLowerCase().contains(sendSMSkeyStringRequest.toLowerCase())
                && (!lastWord.equals(delivaryInfoKeyString))) {
            apiType = "send_sms";
        } else if (ResourceURL.toLowerCase().contains(outboundkeyString.toLowerCase())
                && lastWord.equals(subscriptionKeyString)) {
            apiType = "start_outbound_subscription";
        } else if (ResourceURL.toLowerCase().contains(outboundkeyString.toLowerCase())
                && ResourceURL.toLowerCase().contains(subscriptionKeyString.toLowerCase())
                && (!lastWord.equals(subscriptionKeyString))) {
            apiType = "stop_outbound_subscription";
        } else if (lastWord.equals(delivaryInfoKeyString)) {
            apiType = "query_sms";
        } else if (ResourceURL.toLowerCase().contains(retriveSMSString.toLowerCase())
                && ResourceURL.toLowerCase().contains(regKeyString.toLowerCase())) {
            apiType = "retrive_sms";
        } else if ( !requested_api.isEmpty() && !requested_api.toLowerCase().contains(ussdKeyString.toLowerCase())
                && ResourceURL.toLowerCase().contains(retriveSMSString.toLowerCase())
                && ResourceURL.toLowerCase().contains(subscriptionKeyString.toLowerCase())) {
            apiType = "retrive_sms_subscriptions";
        } else if (ResourceURL.toLowerCase().contains(paymentKeyString.toLowerCase())) {
            apiType = "payment";
        } else if (ResourceURL.toLowerCase().contains(delivaryNotifyString.toLowerCase())) {
            apiType = "sms_inbound_notifications";
        }else if (!requested_api.isEmpty() && requested_api.toLowerCase().contains(ussdKeyString.toLowerCase())){
            if(ResourceURL.toLowerCase().contains("outbound")){
                apiType = "ussd_send";
            } else if (!ResourceURL.toLowerCase().contains(subscriptionKeyString.toLowerCase())){
                apiType = "ussd_receive";
            } else {
                if (lastWord.equals(subscriptionKeyString.toLowerCase())){
                    apiType = "ussd_subscription";
                } else {
                    apiType = "stop_ussd_subscription";
                }
            }

        } else if (ResourceURL.toLowerCase().contains(locationString.toLowerCase())) {
            apiType = "location";
        } else {
            return null;
        }

        return apiType;
    }

    private static String getAppNameByConsumerKey(String key) throws APIManagementException {
        String appName = "";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT `name` FROM AM_APPLICATION ap, AM_APPLICATION_KEY_MAPPING mp WHERE mp.CONSUMER_KEY =? AND mp.KEY_TYPE ='PRODUCTION' AND ap.APPLICATION_ID = mp.APPLICATION_ID";
        try {
            conn = getApiMgtDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, key);

            log.debug("getAPIWiseTrafficForReportCharging");
            results = ps.executeQuery();
            while (results.next()) {
                appName = results.getString(1);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting API wise traffic for report (Charging) from the database" + e);
            handleException("Error occured while getting API wise traffic for report (Charging) from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }

        return appName;
    }

    private static String getConsumerKeyByAppId(String appId) throws APIManagementException {
        String key = "";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "SELECT `CONSUMER_KEY` FROM AM_APPLICATION_KEY_MAPPING WHERE `APPLICATION_ID` = ? and `KEY_TYPE` = 'PRODUCTION'";
        try {
            conn = getApiMgtDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, appId);

            log.debug("getAPIWiseTrafficForReportCharging");
            results = ps.executeQuery();
            while (results.next()) {
                key = results.getString(1);
            }
        } catch (Exception e) {
            System.out.println("Error occured while getting API wise traffic for report (Charging) from the database" + e);
            handleException("Error occured while getting API wise traffic for report (Charging) from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }

        return key;
    }
    
    //==========================PRIYANKA_06608==========================================
    public static ArrayList<SPObject> generateSPList() throws APIMgtUsageQueryServiceClientException, APIManagementException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "select distinct(application_id),name,authz_user from (select am.application_id,am.name,ac.authz_user "
        		+ "from IDN_OAUTH2_ACCESS_TOKEN ac,IDN_OAUTH_CONSUMER_APPS ca,AM_APPLICATION am,AM_APPLICATION_KEY_MAPPING km where ac.consumer_key=ca.consumer_key "
        		+ "and km.application_id=am.application_id and km.consumer_key=ca.consumer_key and ac.authz_user=ca.username and user_type='APPLICATION' and token_State='Active') as dummy";
        ArrayList<SPObject> spList = new ArrayList<SPObject>();
        try {
            connection = getApiMgtDBConnection();
            ps = connection.prepareStatement(sql);
            results = ps.executeQuery();
            while (results.next()) {
            	SPObject spObject=new SPObject();
            	spObject.setAppId(results.getInt("application_id"));
            	spObject.setSpName(results.getString("name"));
            	spList.add(spObject);                
            }
            return spList;
        } catch (SQLException e) {
            log.error("SQL Error in getResponseTimeForAPI");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting Invocation count for Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return null;
    }
    
    
    
    public static SPObject generateSPObject(String appId) throws APIMgtUsageQueryServiceClientException, APIManagementException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "select am.application_id,am.name,ac.authz_user,ac.ACCESS_TOKEN,ca.consumer_key,ca.consumer_secret "
        		+ "from IDN_OAUTH2_ACCESS_TOKEN ac,IDN_OAUTH_CONSUMER_APPS ca,AM_APPLICATION am,AM_APPLICATION_KEY_MAPPING km "
        		+ "where ac.consumer_key=ca.consumer_key and km.application_id=am.application_id "
        		+ "and km.consumer_key=ca.consumer_key and ac.authz_user=ca.username and user_type='APPLICATION' and token_State='Active' and am.application_id='"+appId+"' limit 1";
        SPObject spObject = new SPObject();
        try {
            connection = getApiMgtDBConnection();
            ps = connection.prepareStatement(sql);
            results = ps.executeQuery();
            while (results.next()) {
            	spObject.setAppId(results.getInt("application_id"));
            	spObject.setSpName(results.getString("name"));
            	spObject.setUserName(results.getString("authz_user"));
            	spObject.setToken(results.getString("ACCESS_TOKEN"));
            	spObject.setKey(results.getString("consumer_key"));
            	spObject.setSecret(results.getString("consumer_secret"));
            }
            return spObject;
        } catch (SQLException e) {
            log.error("SQL Error in getResponseTimeForAPI");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting Invocation count for Application", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return null;
    }  
    
    
    public static List<String[]> getTotalAPITrafficForLineChart(String fromDate, String toDate, String subscriber, String operator, int applicationId, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException, SQLException {
        String consumerKey = null;
        if (subscriber.equals("__ALL__")) {
            subscriber = "%";
        }
        if (operator.equals("__ALL__")) {
            operator = "%";
        }
        if (applicationId == 0) {
            consumerKey = "%";
        } else {
            consumerKey = AMDataAccessObject.getConsumerKeyByApplication(applicationId);
        }
        if (api.equals("__ALL__")) {
            api = "%";
        }

        if (consumerKey == null) {
            return null;
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        String sql = "select date(time) as date, sum(response_count) hits from SB_API_RESPONSE_SUMMARY\n"
                + "where DATE(time) between STR_TO_DATE(?,'%Y-%m-%d') and STR_TO_DATE(?,'%Y-%m-%d') AND operatorId LIKE ? AND userId LIKE ? AND API LIKE ? AND consumerKey LIKE ? \n"
                + "group by date";
        List<String[]> api_request = new ArrayList<String[]>();

        try {
            conn = getStatsDBConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, fromDate);
            ps.setString(2, toDate);
            ps.setString(3, operator);
            ps.setString(4, subscriber);
            ps.setString(5, api);
            ps.setString(6, consumerKey);
            if (log.isDebugEnabled()) {
        	log.debug("getTotalTrafficForLineChart : SQL " + ps.toString() );
            }
            
            results = ps.executeQuery();
            while (results.next()) {
                String[] temp = {results.getDate(1).toString(), results.getString(2)};
                api_request.add(temp);
            }

        } catch (Exception e) {
            log.error("Error occured while getting total traffic for histogram from the database" + e);            
            handleException("Error occured while getting total traffic for histogram from the database", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, results);
        }
        return api_request;
    }
    
    public static List<APIResponseDTO> getAllResponseTimesForAllAPIs(String operator, String userId, String fromDate,
            String toDate, String timeRange) throws APIMgtUsageQueryServiceClientException, APIManagementException {

	if (log.isDebugEnabled()) {
	    log.debug("getAllResponseTimesForAllAPIs() for  Operator "
		    + operator + " Subscriber " + userId + " betweeen "
		    + fromDate + " to " + toDate);
	}
        
        if (operator.contains("__ALL__")) {
            operator = "%";
        }
        
        if (userId.contains("__ALL__")) {
            userId = "%";
        }
      
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
    
        String sql = "SELECT SUM(response_count) as sumCount, SUM(serviceTime) as sumServiceTime, STR_TO_DATE(time,'%Y-%m-%d') as date "
        		+ "FROM SB_API_RESPONSE_SUMMARY "
        		+ "WHERE (time BETWEEN ? AND ?) AND operatorId LIKE ? AND userId LIKE ? "
        		+ "GROUP BY date;";
        List<APIResponseDTO> responseTimes = new ArrayList<APIResponseDTO>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setString(1, fromDate + " 00:00:00");
            ps.setString(2, toDate + " 23:59:59");
            ps.setString(3, operator);
            ps.setString(4, userId);
            results = ps.executeQuery();
            while (results.next()) {
                APIResponseDTO resp = new APIResponseDTO();
                resp.setResponseCount(results.getInt("sumCount"));
                resp.setServiceTime(results.getInt("sumServiceTime"));
                resp.setDate(results.getDate("date"));

                responseTimes.add(resp);
            }
        } catch (SQLException e) {
            log.error("SQL Error in getAllResponseTimesForAPI");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting all response times for API", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return responseTimes;
    }
    
    public static List<APIResponseDTO> getAllResponseTimesForAPIbyDate(String operator, String userId, String fromDate,
            String toDate, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException {

	if (log.isDebugEnabled()) {
	    log.debug("getAllResponseTimesForAllAPIs() for  Operator "
		    + operator + " Subscriber " + userId + " betweeen "
		    + fromDate + " to " + toDate);
	}
        
        if (operator.contains("__ALL__")) {
            operator = "%";
        }
        
        if (userId.contains("__ALL__")) {
            userId = "%";
        }
      
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
    
        String sql = "SELECT api,SUM(response_count) as sumCount, SUM(serviceTime) as sumServiceTime, STR_TO_DATE(time,'%Y-%m-%d') as date "
        		+ "FROM SB_API_RESPONSE_SUMMARY "
        		+ "WHERE api=? AND (time BETWEEN ? AND ?) AND operatorId LIKE ? AND userId LIKE ? "
        		+ "GROUP BY date;";
        List<APIResponseDTO> responseTimes = new ArrayList<APIResponseDTO>();
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setString(1, api);
            ps.setString(2, fromDate + " 00:00:00");
            ps.setString(3, toDate + " 23:59:59");
            ps.setString(4, operator);
            ps.setString(5, userId);
            results = ps.executeQuery();
            while (results.next()) {
                APIResponseDTO resp = new APIResponseDTO();
                resp.setApiVersion(results.getString("api"));
                resp.setResponseCount(results.getInt("sumCount"));
                resp.setServiceTime(results.getInt("sumServiceTime"));
                resp.setDate(results.getDate("date"));

                responseTimes.add(resp);
            }
        } catch (SQLException e) {
            log.error("SQL Error in getAllResponseTimesForAPIbyDate");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting all response times for API by date", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return responseTimes;
    }


    
    public static String[] getTimeConsumptionByAPI(String operator, String userId, String fromDate,
            String toDate, String api) throws APIMgtUsageQueryServiceClientException, APIManagementException {

	if (log.isDebugEnabled()) {
	    log.debug("getAllResponseTimesForAllAPIs() for  Operator "
		    + operator + " Subscriber " + userId + " betweeen "
		    + fromDate + " to " + toDate);
	}
        
        if (operator.contains("__ALL__")) {
            operator = "%";
        }
        
        if (userId.contains("__ALL__")) {
            userId = "%";
        }
      
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;


        String sql = "SELECT api, MAX(hightestTime) as highestConsumption, SUM(sumServiceTime)/SUM(sumCount) as avgTotalConsump FROM ("
        	+ "SELECT api,SUM(response_count) as sumCount, SUM(serviceTime) as sumServiceTime, (SUM(serviceTime)/SUM(response_count)) as hightestTime, STR_TO_DATE(time,'%Y-%m-%d') as date "
        	+ "FROM SB_API_RESPONSE_SUMMARY "
        	+ "WHERE api = ? AND (time BETWEEN ? AND ?) AND operatorId LIKE ? AND userId LIKE ? "
        	+ "GROUP BY date "
        	+ ") AS T;";

        String[] timeConsumerData = new String[3];
        try {
            connection = getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            ps.setString(1, api);
            ps.setString(2, fromDate + " 00:00:00");
            ps.setString(3, toDate + " 23:59:59");
            ps.setString(4, operator);
            ps.setString(5, userId);
            results = ps.executeQuery();
            while (results.next()) {
              
        	timeConsumerData[0] = results.getString("api");
        	timeConsumerData[1] = Double.toString(results.getDouble("highestConsumption"));
        	timeConsumerData[2] = Double.toString(results.getDouble("avgTotalConsump"));
        	
        	
            }
        } catch (SQLException e) {
            log.error("SQL Error in getAllResponseTimesForAPIbyDate");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting all response times for API by date", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return timeConsumerData;
    }

    /**
     * Get Subscription workflow Creation Time
     * @param appId Application ID
     * @param apiIdent API Identifier
     * @return Time of subscription workflow creation
     * @throws APIManagementException
     * @throws APIMgtUsageQueryServiceClientException
     */
    public static Date getSubscriptionCreatedTime(int appId, APIIdentifier apiIdent)
            throws APIManagementException, APIMgtUsageQueryServiceClientException {
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;
        Timestamp wfCreatedTime = null;
        String sql = "SELECT WF.WF_CREATED_TIME " +
                "FROM AM_SUBSCRIPTION SUBS, AM_WORKFLOWS WF " +
                "WHERE " +
                "SUBS.APPLICATION_ID = ? " +
                "AND SUBS.API_ID = ? " +
                "AND WF.WF_TYPE= ? " +
                "AND WF.WF_REFERENCE=SUBS.SUBSCRIPTION_ID ";

        try {
            connection = getApiMgtDBConnection();
            int apiId = ApiMgtDAO.getAPIID(apiIdent, connection);

            ps = connection.prepareStatement(sql);
            ps.setInt(1, appId);
            ps.setInt(2, apiId);
            ps.setString(3, WorkflowConstants.WF_TYPE_AM_SUBSCRIPTION_CREATION);

            results = ps.executeQuery();
            while (results.next()) {
                wfCreatedTime = results.getTimestamp("WF_CREATED_TIME");
            }
            if (log.isDebugEnabled()) {
                log.debug("Subscription creation Time of workflow for app " + appId + " and API " + apiId
                        + " - " + wfCreatedTime);
            }
        } catch (SQLException e) {
            log.error("SQL Error in getWorkflowCreatedTime");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting workflow created time", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return (wfCreatedTime != null) ? new Date(wfCreatedTime.getTime()) : null;
    }

//====================================PRIYANKA_06608===========================================
    
	public static Map<String,CommissionPercentagesDTO> getCommissionPercentages(String spId, Integer appId) throws APIMgtUsageQueryServiceClientException,APIManagementException {
		Connection connection = null;
		PreparedStatement ps = null;
		ResultSet results = null;
		String sql = "select id,subscriber,merchant_code,app_id,sp_commission,ads_commission,opco_commission from "
                        + "rates_percentages where subscriber=? and app_id=?";
		Map<String,CommissionPercentagesDTO> requestSet = new HashMap<String,CommissionPercentagesDTO>();
		try {
			connection = getStatsDBConnection();
			ps = connection.prepareStatement(sql);
			ps.setString(1, spId);
			ps.setInt(2, appId);

			results = ps.executeQuery();
                        String merchantCode=null;
			while (results.next()) {
                                merchantCode = results.getString("merchant_code");
                            
				CommissionPercentagesDTO dto=new CommissionPercentagesDTO();
				dto.setId(results.getInt("id"));
				dto.setSpId(results.getString("subscriber"));
				dto.setMerchantCode(merchantCode);
				dto.setAppId(results.getInt("app_id"));
				dto.setSpCommission(new BigDecimal(results.getDouble("sp_commission")));
				dto.setAdsCommission(new BigDecimal(results.getDouble("ads_commission")));
				dto.setOpcoCommission(new BigDecimal(results.getDouble("opco_commission")));
				requestSet.put(merchantCode,dto);
			}
		} catch (SQLException e) {
			handleException(
					"Error occurred while getting Payment API count for Application",e);
		} finally {
			APIMgtDBUtil.closeAllConnections(ps, connection, results);
		}

		return requestSet;
	}
    
    public static String getOperationNameById(int operationId) throws APIManagementException,
            APIMgtUsageQueryServiceClientException {

        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet results = null;

        String operationName = null ;
        String sql = "SELECT operation FROM " + HostObjectConstants.API_OPERATION_TYPES_TABLE +
                " WHERE operation_id=?";
        try {
            connection = BillingDataAccessObject.getStatsDBConnection();
            ps = connection.prepareStatement(sql);
            log.debug("getOperationName for operationID---> " + operationId);
            ps.setInt(1, operationId);
            log.debug("SQL (PS) ---> " + ps.toString());
            results = ps.executeQuery();
            while (results.next()) {
                operationName = results.getString("operation");
            }
        } catch (SQLException e) {
            log.error("SQL Error in getTaxesForSubscription");
            log.error(e.getStackTrace());
            handleException("Error occurred while getting Taxes for Subscription", e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, connection, results);
        }
        return operationName;
    }

    /**
     * Convert system time to local time
     * @param timeOffset time difference in minutes to UTC
     * @param time system Time
     * @return String converted into local Time
     */
    public static String convertToLocalTime(String timeOffset, String time){
        Integer offsetValue = Integer.parseInt(timeOffset);

        log.debug("Offset value = " +offsetValue);
        DateTimeZone systemTimeZone = DateTimeZone.getDefault();
        log.debug("system time zone -== " +systemTimeZone.toString());
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime systemDateTime = formatter.parseDateTime(time);
        log.debug("system date time === " + systemDateTime.toString());
        systemDateTime = systemDateTime.withZoneRetainFields(systemTimeZone);
        log.debug("system date time after adding systemtimezone === " + systemDateTime.toString());

        int hours = -1 * offsetValue / 60;
        int minutes = offsetValue % 60;
        minutes = Math.abs(minutes);

        DateTimeZone localTimeZone = DateTimeZone.forOffsetHoursMinutes(hours,minutes);

        log.debug("Local time zone ==== " + localTimeZone.toString());

        DateTime convertedDateTime = systemDateTime.withZone( localTimeZone );

        String convertedDateTimeString = formatter.print(convertedDateTime);

        log.debug("converted time === "+convertedDateTimeString);

        return convertedDateTimeString;

    }

}
