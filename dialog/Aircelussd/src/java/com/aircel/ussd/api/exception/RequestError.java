/*
 * RequestError.java
 * Apr 2, 2013  11:20:38 AM
 **
 *
 * Copyright (C) Dialog Axiata PLC. All Rights Reserved.
 */

package com.aircel.ussd.api.exception;

import com.aircel.ussd.api.responsebean.sms.ISMSresponse;


/**
 * <TO-DO>
 * <code>RequestError</code>
 *
 * @version $Id: RequestError.java,v 1.00.000
 */

public class RequestError implements ISMSresponse {

    private ServiceException serviceException;

    public ServiceException getServiceException() {
        return serviceException;
    }

    public void setServiceException(ServiceException serviceException) {
        this.serviceException = serviceException;
    }
    private PolicyException policyException;

    public PolicyException getPolicyException() {
        return policyException;
    }

    public void setPolicyException(PolicyException policyException) {
        this.policyException = policyException;
    }
}