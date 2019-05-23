/*
 *
 *   Copyright (c) 2019, WSO2 Inc., WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.security.tools.scanmanager.scanner.qualys.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.security.tools.scanmanager.common.model.LogType;
import org.wso2.security.tools.scanmanager.common.model.ScanStatus;
import org.wso2.security.tools.scanmanager.scanners.common.exception.ScannerException;
import org.wso2.security.tools.scanmanager.scanners.common.util.CallbackUtil;
import org.wso2.security.tools.scanmanager.scanners.qualys.model.ScanContext;
import org.wso2.security.tools.scanmanager.scanners.qualys.service.QualysScanner;
import org.wso2.security.tools.scanmanger.scanners.qualys.QualysScannerConstants;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Responsible to check the scan status.
 */
public class StatusHandler {

    private static final Log log = LogFactory.getLog(StatusHandler.class);
    private static final int NUM_THREADS = 1;
    private ScheduledExecutorService scheduler;
    private final long initialDelay;
    private final long delayBetweenRuns;
    // Current status in Scan Manager perspective.
    private ScanStatus currentStatus;
    // Current status in Qualys scanner perspective.
    private String currentScannerStatus;
    private QualysScanHandler qualysScanHandler;
    private ScanContext scanContext;

    public StatusHandler(QualysScanHandler qualysScanHandler, ScanContext scanContext, long initialDelay,
            long delayBetweenRuns) {
        this.qualysScanHandler = qualysScanHandler;
        this.scanContext = scanContext;
        this.initialDelay = initialDelay;
        this.delayBetweenRuns = delayBetweenRuns;
        this.scheduler = Executors.newScheduledThreadPool(NUM_THREADS);
        currentScannerStatus = QualysScannerConstants.SUBMITTED;
        currentStatus = ScanStatus.SUBMITTED;
    }

    /**
     * Activate status checker when Qualys scan id is generated.
     */
    public void activateStatusHandler() {
        log.info("-------------------");
        Runnable checkStatusTask = new UpdateStatusTask();
        scheduler.scheduleWithFixedDelay(checkStatusTask, initialDelay, delayBetweenRuns, TimeUnit.MINUTES);
        log.info("activated status checker");
    }

    /**
     * Runnable class to check status task.
     */
    private final class UpdateStatusTask implements Runnable {
        String logMessage;

        @Override public void run() {
            if (log.isDebugEnabled()) {
                CallbackUtil.persistScanLog(scanContext.getJobID(),
                        " Scheduled Executor Service is started to check scan status periodically", LogType.DEBUG);
            }
            String status;
            try {
                // Retrieve Qualys Scan status from Qualys Scanner.
                status = qualysScanHandler.retrieveScanStatus(QualysScanner.host, scanContext.getScannerScanId());
                if (!currentScannerStatus.equalsIgnoreCase(status)) {
                    currentScannerStatus = status;
                    // Map qualys scanner status with scan manager status.
                    ScanStatus tempScanManagerStatus = mapStatus(status);
                    if (currentStatus.compareTo(tempScanManagerStatus) == 0) {
                        updateStatus(currentStatus);

                    }
                }
            } catch (ScannerException e) {
                CallbackUtil.updateScanStatus(scanContext.getJobID(), ScanStatus.ERROR, null,
                        scanContext.getScannerScanId());
                CallbackUtil.persistScanLog(scanContext.getJobID(), "Could not retrieve the status", LogType.ERROR);
                scheduler.shutdown();
            }
        }

        private synchronized ScanStatus mapStatus(String status) throws ScannerException {
            Runnable scanRelauncher;
            ScanStatus tempScanStatus = null;
            switch (status) {
            case QualysScannerConstants.SUBMITTED:
            case QualysScannerConstants.RUNNING:
                tempScanStatus = ScanStatus.RUNNING;
                //                currentStatus.set(ScanStatus.RUNNING);
                logMessage = "Scan is Running.";
                break;
            case QualysScannerConstants.ERROR:
                tempScanStatus = ScanStatus.ERROR;
                //                currentStatus.set(ScanStatus.ERROR);
                logMessage = "Error occurred while scanning. ";
                break;
            case QualysScannerConstants.TIME_LIMIT_EXCEEDED:
                scanRelauncher = new ScanReLauncher();
                scanRelauncher.run();
                CallbackUtil.persistScanLog(scanContext.getJobID(), "Scan is relaunched due to TIME LIMIT EXCEED",
                        LogType.INFO);
                scheduler.shutdown();
                break;
            case QualysScannerConstants.SCANNER_NOT_AVAILABLE:
                scanRelauncher = new ScanReLauncher();
                scanRelauncher.run();
                CallbackUtil.persistScanLog(scanContext.getJobID(),
                        "Scan is relaunched due to Scanner" + "is not available", LogType.INFO);
                scheduler.shutdown();
                break;
            case QualysScannerConstants.CANCELLED:
                tempScanStatus = ScanStatus.CANCELED;
                logMessage = "Scan is cancelled";
                break;
            case QualysScannerConstants.FINISHED:
                String authStatus = qualysScanHandler
                        .retrieveAuthStatus(QualysScanner.host, scanContext.getScannerScanId());
                String resultsStatus = qualysScanHandler
                        .retrieveResultStatus(QualysScanner.host, scanContext.getScannerScanId());
                Boolean isScanAuthenticationSuccessful = isScanAuthenticationSuccessed(authStatus);
                Boolean isScanSuccessFull = isResultSucceed(resultsStatus);
                if ((isScanAuthenticationSuccessful) && (isScanSuccessFull)) {
                    tempScanStatus = ScanStatus.COMPLETED;
                } else {
                    tempScanStatus = ScanStatus.FAILED;
                }
                break;
            }
            return tempScanStatus;
        }

        private synchronized void updateStatus(ScanStatus scanStatus) throws ScannerException {
            currentStatus = scanStatus;
            switch (scanStatus) {
            case COMPLETED:
                ReportHandler reportHandler = new ReportHandler(qualysScanHandler);
                reportHandler.execute(scanContext.getWebAppId(), scanContext.getJobID());
                // todo update report file path
                CallbackUtil.updateScanStatus(scanContext.getJobID(), ScanStatus.COMPLETED, null,
                        scanContext.getScannerScanId());
                CallbackUtil.persistScanLog(scanContext.getJobID(), "Scan is successfully completed", LogType.INFO);
                scheduler.shutdown();
                break;
            case RUNNING:
            case SUBMITTED:
                CallbackUtil.persistScanLog(scanContext.getJobID(), logMessage, LogType.INFO);
                CallbackUtil.updateScanStatus(scanContext.getJobID(), ScanStatus.RUNNING, null,
                        scanContext.getScannerScanId());
                break;
            case CANCELED:
            case ERROR:
            case FAILED:
                CallbackUtil.updateScanStatus(scanContext.getJobID(), scanStatus, null, scanContext.getScannerScanId());
                CallbackUtil.persistScanLog(scanContext.getJobID(), logMessage, LogType.ERROR);
                scheduler.shutdown();
            }
        }

        private synchronized boolean isScanAuthenticationSuccessed(String authStatus) {
            boolean isScanAuthenticationSuccessful = false;
            switch (authStatus) {
            case QualysScannerConstants.AUTH_PARTIAL:
                CallbackUtil.persistScanLog(scanContext.getJobID(),
                        "Scan is failed since authentication is partially successful", LogType.ERROR);
                break;
            case QualysScannerConstants.AUTH_FAILED:
                CallbackUtil.persistScanLog(scanContext.getJobID(), "Scan is failed due to authentication failure",
                        LogType.ERROR);
                break;
            case QualysScannerConstants.AUTH_SUCCESSFUL:
                isScanAuthenticationSuccessful = true;
                CallbackUtil.persistScanLog(scanContext.getJobID(), "Authentication is succeed.", LogType.INFO);
                break;
            default:
                isScanAuthenticationSuccessful = false;
            }
            return isScanAuthenticationSuccessful;
        }

        private synchronized boolean isResultSucceed(String resultStatus) {
            boolean isScanSuccessFull = false;
            switch (resultStatus) {
            case QualysScannerConstants.NO_HOST_ALIVE:
            case QualysScannerConstants.NO_WEB_SERVICE:
                CallbackUtil.persistScanLog(scanContext.getJobID(),
                        "Scan is failed " + resultStatus + " . Please check qualys documentation for"
                                + " more information", LogType.ERROR);
                break;
            case QualysScannerConstants.SCAN_RESULTS_INVALID:
                CallbackUtil.persistScanLog(scanContext.getJobID(),
                        "Scan is finished but scan result is invalid. Please check qualys "
                                + "documentation for more information", LogType.ERROR);
                break;
            case QualysScannerConstants.TIME_LIMIT_EXCEEDED:
                Runnable scanRelauncher = new ScanReLauncher();
                scanRelauncher.run();
                CallbackUtil.persistScanLog(scanContext.getJobID(), "Scan is relaunched due to TIME LIMIT EXCEED",
                        LogType.INFO);
                scheduler.shutdown();
                break;
            case QualysScannerConstants.SERVICE_ERROR:
                CallbackUtil.persistScanLog(scanContext.getJobID(),
                        "Scan is failed due to service error. Please check qualys documentation"
                                + " for more information", LogType.ERROR);
                break;
            case QualysScannerConstants.SCAN_INTERNAL_ERROR:
                CallbackUtil.persistScanLog(scanContext.getJobID(),
                        "Scan is failed due to scan internal error. Please check qualys "
                                + "documentation for more information", LogType.ERROR);
                break;
            case QualysScannerConstants.SUCCESSFUL:
                isScanSuccessFull = true;
                CallbackUtil.persistScanLog(scanContext.getJobID(),
                        "Scan is completed in Qualys end. Please wait to" + " create and download reports",
                        LogType.INFO);
                break;
            default:
            }
            return isScanSuccessFull;
        }

    }

    /**
     * ReLaunch Scan.
     */
    private final class ScanReLauncher implements Runnable {
        @Override public void run() {
            try {
                qualysScanHandler.launchScan(scanContext, QualysScanner.host);
            } catch (ScannerException e) {
                CallbackUtil.updateScanStatus(scanContext.getJobID(), ScanStatus.ERROR, null,
                        scanContext.getScannerScanId());
                CallbackUtil.persistScanLog(scanContext.getJobID(), "Failed to relaunch teh scan", LogType.ERROR);
            }
        }
    }
}
