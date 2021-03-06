/*
 * ValidateLocation.java
 * Oct 1, 2014  1:47:42 PM
 * Roshan.Saputhanthri
 *
 * Copyright (C) Dialog Axiata PLC. All Rights Reserved.
 */

package com.axiata.dialog.oneapi.validation.impl;

import com.axiata.dialog.oneapi.validation.AxiataException;
import com.axiata.dialog.oneapi.validation.IServiceValidate;
import com.axiata.dialog.oneapi.validation.UrlValidator;
import com.axiata.dialog.oneapi.validation.ValidationNew;
import com.axiata.dialog.oneapi.validation.ValidationRule;

/**
 * <TO-DO> <code>ValidateLocation</code>
 * @version $Id: ValidateLocation.java,v 1.00.000
 */
public class ValidateLocation implements IServiceValidate {

    private final String[] validationRules = {"queries","location"};

    public void validate(String[] params) throws AxiataException {
        //Send parameters within String array according to following order, String[] params = "registrationId", "maxBatchSize";
        String address = nullOrTrimmed(params[0]);
        Double requestAccuracy = Double.parseDouble(params[1]);
        
        ValidationRule[] rules = {
                new ValidationRule(ValidationRule.VALIDATION_TYPE_MANDATORY_TEL, "address", address),
                new ValidationRule(ValidationRule.VALIDATION_TYPE_MANDATORY_LOC_ACCURACY, "requestedAccuracy", requestAccuracy)};
        
        ValidationNew.checkRequestParams(rules);
        
    }

    private static String nullOrTrimmed(String s) {
        String rv = null;
        if (s != null && s.trim().length() > 0) {
            rv = s.trim();
        }
        return rv;
    }

    public void validate(String json) throws AxiataException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void validateUrl(String pathInfo) throws AxiataException {
        String[] requestParts = null;
        if (pathInfo != null) {
            if (pathInfo.startsWith("/")) {
                pathInfo = pathInfo.substring(1);
            }
            requestParts = pathInfo.split("/");
        }

        //remove batchSize param
        int reqlength = requestParts.length -1;
        requestParts[reqlength] = requestParts[reqlength].split("\\?")[0];

        UrlValidator.validateRequest(requestParts, validationRules);
    }
}
