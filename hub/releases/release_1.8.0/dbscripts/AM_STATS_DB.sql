/*
* ==========================================
* MySQL script related to custom tables in
* Api Manager Stats database
* Database: AM stats database
* ==========================================
*/

/*
* Table for application approval audit data.
*/
CREATE TABLE IF NOT EXISTS `app_approval_audit` (
        `APP_APPROVAL_ID` INT UNSIGNED NOT NULL AUTO_INCREMENT,
	`APP_NAME` varchar(100) DEFAULT NULL,
	`APP_CREATOR` varchar(50) DEFAULT NULL,
	`APP_STATUS` varchar(50) DEFAULT 'ON_HOLD',
	`APP_APPROVAL_TYPE` varchar(50) DEFAULT NULL,
	`COMPLETED_BY_ROLE` varchar(50) DEFAULT NULL,
	`COMPLETED_BY_USER` varchar(50) DEFAULT NULL,
	`COMPLETED_ON` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (`APP_APPROVAL_ID`)
);

/*
* Table for subscription approval audit data.
*/
CREATE TABLE IF NOT EXISTS `sub_approval_audit` (
	`API_PROVIDER` varchar(200) DEFAULT NULL,
  	`API_NAME` varchar(200) DEFAULT NULL,
  	`API_VERSION` varchar(30) DEFAULT NULL,
	`APP_ID` int(11) NOT NULL,
	`SUB_STATUS` varchar(50) DEFAULT 'ON_HOLD',
	`SUB_APPROVAL_TYPE` varchar(50) DEFAULT NULL,
	`COMPLETED_BY_ROLE` varchar(50) DEFAULT NULL,
	`COMPLETED_BY_USER` varchar(50) DEFAULT NULL,
	`COMPLETED_ON` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (`APP_ID`, `API_PROVIDER`, `API_NAME`, `API_VERSION`, `COMPLETED_BY_ROLE`)
);

/*
* Table for tax type definition
*/
CREATE TABLE IF NOT EXISTS tax ( 
    id INT NOT NULL AUTO_INCREMENT,
    type VARCHAR(25) NOT NULL,
    effective_from DATE,
    effective_to DATE,
    value DECIMAL(7,6) NOT NULL,
    PRIMARY KEY (id)
);

/*
* Table for API subscriptions to tax type mapping
*/
CREATE TABLE IF NOT EXISTS subscription_tax ( 
    application_id INT NOT NULL,
    api_id INT NOT NULL,
    tax_type VARCHAR(25) NOT NULL,
    PRIMARY KEY (application_id, api_id, tax_type)
);


/*
* Table for API subscriptions to charge rate mapping
*/
CREATE TABLE IF NOT EXISTS subscription_rates ( 
    application_id INT NOT NULL,
    api_id INT NOT NULL,
    operator_name varchar(45) NOT NULL,
    rate_name varchar(50) DEFAULT NULL,
    PRIMARY KEY (application_id, api_id, operator_name)
);

/*
* Tables for whitelist & blacklist
*/
CREATE TABLE `blacklistmsisdn` (
 `Index` int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
 `MSISDN` varchar(45) NOT NULL,
 `API_ID` varchar(45) NOT NULL,
 `API_NAME` varchar(45) NOT NULL,
 `USER_ID` varchar(45) NOT NULL,
  UNIQUE KEY `UNQ_blacklistmsisdn` (`API_NAME`, `MSISDN`));

CREATE TABLE IF NOT EXISTS`subscription_WhiteList` (
  `index` int(11) NOT NULL AUTO_INCREMENT,
  `subscriptionID` varchar(45) NOT NULL,
  `msisdn` varchar(45) NOT NULL UNIQUE,
  `api_id` varchar(45) NOT NULL,
  `application_id` varchar(45) NOT NULL,
  PRIMARY KEY (`index`)
);

CREATE TABLE IF NOT EXISTS `admin_comments` (
  `TaskID` int(11) NOT NULL,
  `Comment` varchar(255) DEFAULT NULL,
  `Status` varchar(255) DEFAULT NULL,
  `Description` varchar(1000) NOT NULL,
  PRIMARY KEY (`TaskID`)
);

CREATE TABLE IF NOT EXISTS `subscription_comments` (
  `TaskID` varchar(255) NOT NULL,
  `Comment` varchar(1024) NOT NULL,
  `Status` varchar(255) NOT NULL,
  `Description` varchar(1024) NOT NULL,
  PRIMARY KEY (`TaskID`)
);

CREATE TABLE  `rates_percentages` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_code` varchar(45) NOT NULL,
  `app_id` int(10) unsigned NOT NULL,
  `sp_commission` double NOT NULL,
  `ads_commission` double NOT NULL,
  `opco_commission` double NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=latin1;



CREATE TABLE IF NOT EXISTS `subscription_rates_nb` (
  `application_id` int(11) NOT NULL,
  `api_id` int(11) NOT NULL,
  `rate_id_nb` varchar(50) DEFAULT NULL,
  `operation_id` int(11) NOT NULL,
  PRIMARY KEY (`application_id`,`api_id`,`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


CREATE TABLE IF NOT EXISTS `api_operation_types` (
  `operation_id` int(11) NOT NULL DEFAULT '0',
  `api` varchar(225) DEFAULT NULL,
  `operation` varchar(225) DEFAULT NULL,
  `default_rate` varchar(255) NOT NULL,
  PRIMARY KEY (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


INSERT INTO `api_operation_types` (`operation_id`, `api`, `operation`,`default_rate`) VALUES
(100, 'payment', 'Charge', 'p1'),
(101, 'payment', 'Refund', 'RF2'),
(200, 'smsmessaging', 'Send SMS' ,'SM1'),
(201, 'smsmessaging', 'Retrive SMS','SM2'),
(202, 'smsmessaging', 'Query SMS Delivery','SM2'),
(203, 'smsmessaging', 'Delivery Subscription','SM2'),
(204, 'smsmessaging', 'Stop Delivery Subscription','SM2'),
(205, 'smsmessaging', 'Retrive SMS Subscription','SM2'),
(206, 'smsmessaging ', 'Stop Retrive SMS Subscription','SM2'),
(207, 'smsmessaging', 'SMS Inbound Notification','SM2'),
(300, 'location', 'Location','lb1'),
(400, 'ussd', 'Send USSD','u1');





CREATE TABLE `merchant_rates_percentages` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `subscriber` varchar(45) NOT NULL,
  `merchant_code` varchar(45) NOT NULL,
  `app_id` int(10) unsigned NOT NULL,
  `sp_commission` double NOT NULL,
  `ads_commission` double NOT NULL,
  `opco_commission` double NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


