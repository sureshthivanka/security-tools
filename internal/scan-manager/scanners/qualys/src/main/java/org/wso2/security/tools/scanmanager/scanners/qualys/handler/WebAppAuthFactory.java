/*
 *
 *   Copyright (c) 2020, WSO2 Inc., WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 * /
 */

package org.wso2.security.tools.scanmanager.scanners.qualys.handler;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wso2.security.tools.scanmanager.common.internal.model.ScannerScanRequest;
import org.wso2.security.tools.scanmanager.scanners.common.exception.InvalidRequestException;
import org.wso2.security.tools.scanmanager.scanners.common.exception.ScannerException;
import org.wso2.security.tools.scanmanager.scanners.common.model.CallbackLog;
import org.wso2.security.tools.scanmanager.scanners.qualys.QualysScannerConstants;
import org.wso2.security.tools.scanmanager.scanners.qualys.model.SeleniumAuth;
import org.wso2.security.tools.scanmanager.scanners.qualys.model.StandardAuth;
import org.wso2.security.tools.scanmanager.scanners.qualys.model.WebAppAuth;

import java.io.File;
import java.util.List;

/**
 * Factory class to generate web abb auth objects.
 */
public class WebAppAuthFactory {

    private static final Logger log = LogManager.getLogger(WebAppAuthFactory.class);

    public WebAppAuth getWebAppAuth(ScannerScanRequest scannerScanRequest)
            throws InvalidRequestException, ScannerException {
        String errorMessage;
        String webAppAuthType = scannerScanRequest.getPropertyMap().get(QualysScannerConstants.WEBAPP_AUTH_TYPE).get(0);
        String logMessage = null;
        // Check whether web app auth type is provided or not, if not provided, it will be set to NONE type.
        if (StringUtils.isEmpty(webAppAuthType)) {
            errorMessage = "Authentication type is not provided. Please select NONE/STANDARD/SELENIUM";
            throw new InvalidRequestException(errorMessage);
        }

        switch (webAppAuthType) {
        case QualysScannerConstants.NONE:
            logMessage =
                    "WebApp authentication type for scan is not provided. NONE type is set for " + scannerScanRequest
                            .getAppId();
            log.warn(new CallbackLog(scannerScanRequest.getJobId(), logMessage));
            return null;
        case QualysScannerConstants.STANDARD_AUTH:
            // If auth type is standard and credential is not provided by the user.
            if (StringUtils.isEmpty(
                    scannerScanRequest.getPropertyMap().get(QualysScannerConstants.STANDARD_AUTH_USERNAME).get(0))
                    || StringUtils.isEmpty(
                    scannerScanRequest.getPropertyMap().get(QualysScannerConstants.STANDARD_AUTH_PASSWORD).get(0))) {
                errorMessage = "Credential for STANDARD Authentication is not provided" + scannerScanRequest.getAppId();
                throw new InvalidRequestException(errorMessage);
            } else {
                return new StandardAuth(scannerScanRequest.getPropertyMap().
                        get(QualysScannerConstants.STANDARD_AUTH_USERNAME).get(0).toCharArray(),
                        scannerScanRequest.getPropertyMap().
                                get(QualysScannerConstants.STANDARD_AUTH_PASSWORD).get(0).toCharArray());
            }
        case QualysScannerConstants.SELENIUM:
            List<String> authFiles = scannerScanRequest.getFileMap().get(QualysScannerConstants.AUTHENTICATION_SCRIPTS);
            if (authFiles.size() != 0) {
                for (int i = 0; i < authFiles.size(); i++) {
                    File file = new File(authFiles.get(0));
                    if (!file.getName().endsWith(QualysScannerConstants.XML)) {
                        errorMessage = "Invalid file type for Authentication Script";
                        throw new InvalidRequestException(errorMessage);
                    }
                }

                // If authentication script is provided, authentication status checker regex should be provided.
                if (StringUtils.isEmpty(
                        scannerScanRequest.getPropertyMap().get(QualysScannerConstants.AUTH_REGEX_KEYWORD).get(0))) {
                    errorMessage = "Authentication checker regex is not provided for authentication script";
                    throw new InvalidRequestException(errorMessage);
                } else {
                    SeleniumAuth seleniumAuth = new SeleniumAuth(
                            scannerScanRequest.getPropertyMap().get(QualysScannerConstants.AUTH_REGEX_KEYWORD).get(0));
                    seleniumAuth.downloadAuthenticationScripts(authFiles.get(0), scannerScanRequest.getJobId());
                    return seleniumAuth;
                }
            } else {
                logMessage = "Authentication script for the scan is not provided. Default authentication script will"
                        + " be used. ";
                log.info(new CallbackLog(scannerScanRequest.getJobId(), logMessage));
                return null;
            }
        default:
            return null;
        }
    }
}
