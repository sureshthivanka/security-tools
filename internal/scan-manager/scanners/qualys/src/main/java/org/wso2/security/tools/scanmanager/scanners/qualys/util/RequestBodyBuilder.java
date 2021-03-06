/*
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
 */

package org.wso2.security.tools.scanmanager.scanners.qualys.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wso2.security.tools.scanmanager.scanners.common.util.FileUtil;
import org.wso2.security.tools.scanmanager.scanners.common.util.XMLUtil;
import org.wso2.security.tools.scanmanager.scanners.qualys.QualysScannerConstants;
import org.wso2.security.tools.scanmanager.scanners.qualys.model.CrawlingScript;
import org.wso2.security.tools.scanmanager.scanners.qualys.model.ScanContext;

import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

/**
 * Qualys scanner accepts XML format request body which contains the data related for scan. This class is responsible to
 * create XML format request body.
 */
public class RequestBodyBuilder {

    private static final Log log = LogFactory.getLog(RequestBodyBuilder.class);

    /**
     * Build request body to update web app with authentication script. Using this request body crawling scope also
     * will be updated for a web application
     *
     * @param jobID          web application name
     * @param webAppName     web application name
     * @param authId         auth script Id
     * @param applicationUrl application url for scan
     * @param crawlingScope  crawling scope for the scan
     * @param blacklistRegex list of blacklist Regex
     * @return update web app request body in XML format
     * @throws ParserConfigurationException error occurred while parsing
     * @throws TransformerException         error occurred while building secure string writer
     */
    public static String buildWebAppConfigUpdateRequest(String jobID, String webAppName, String authId,
            String applicationUrl, String crawlingScope, List<String> blacklistRegex)
            throws ParserConfigurationException, TransformerException {
        String updateWebAppRequestBody;
        DocumentBuilderFactory dbf = XMLUtil.getSecuredDocumentBuilderFactory();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document doc = builder.newDocument();
        Element root = doc.createElement(QualysScannerConstants.SERVICE_REQUEST);
        doc.appendChild(root);

        Element data = doc.createElement(QualysScannerConstants.DATA);
        root.appendChild(data);

        Element webApp = doc.createElement(QualysScannerConstants.QUALYS_WEBAPP_KEYWORD);
        data.appendChild(webApp);

        Element scope = doc.createElement(QualysScannerConstants.QUALYS_CRAWLING_SCOPE_KEYWORD);
        scope.appendChild(doc.createTextNode(crawlingScope));
        webApp.appendChild(scope);

        Element blacklist = doc.createElement(QualysScannerConstants.BLACKLIST_KEY_WORD);
        webApp.appendChild(blacklist);

        Element setBlacklist = doc.createElement(QualysScannerConstants.SET);
        blacklist.appendChild(setBlacklist);

        for (int i = 0; i < blacklistRegex.size(); i++) {
            Element urlEntry = doc.createElement(QualysScannerConstants.URL_ENTRY_WITH_REGEX);
            urlEntry.setAttribute(QualysScannerConstants.REGEX, QualysScannerConstants.TRUE);
            urlEntry.appendChild(doc.createCDATASection(blacklistRegex.get(i)));
            setBlacklist.appendChild(urlEntry);
        }

        Element url = doc.createElement(QualysScannerConstants.SCAN_URL_KEYWORD);
        url.appendChild(doc.createTextNode(applicationUrl));
        webApp.appendChild(url);

        // authId will be null when authentication script is not provided. In this case application default auth id
        // will be taken by Qualys Scan
        if (authId != null) {
            Element authRecords = doc.createElement(QualysScannerConstants.AUTH_RECORDS);
            webApp.appendChild(authRecords);

            Element add = doc.createElement(QualysScannerConstants.ADD);
            authRecords.appendChild(add);

            Element webAppAuthRecord = doc.createElement(QualysScannerConstants.WEB_APP_AUTH_RECORD);
            add.appendChild(webAppAuthRecord);

            Element id = doc.createElement(QualysScannerConstants.ID_KEYWORD);
            id.appendChild(doc.createTextNode(authId));
            webAppAuthRecord.appendChild(id);
        }

        StringWriter stringWriter = XMLUtil.buildSecureStringWriter(doc);
        updateWebAppRequestBody = stringWriter.getBuffer().toString();

        return updateWebAppRequestBody;
    }

    /**
     * Build request body to create report.
     *
     * @param webAppId         web app id
     * @param jobId            job id
     * @param reportFormat     report format
     * @param reportTemplateID template id for report creation
     * @return request body
     * @throws ParserConfigurationException error occurred while parsing
     * @throws TransformerException         error occurred while building secure string writer
     */
    public static String buildReportCreationRequest(String webAppId, String jobId, String reportFormat,
            String reportTemplateID) throws ParserConfigurationException, TransformerException {
        String createReportRequestBody;
        DocumentBuilderFactory dbf = XMLUtil.getSecuredDocumentBuilderFactory();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document document = builder.newDocument();
        Element root = document.createElement(QualysScannerConstants.SERVICE_REQUEST);
        document.appendChild(root);

        Element data = document.createElement(QualysScannerConstants.DATA);
        root.appendChild(data);

        Element report = document.createElement(QualysScannerConstants.REPORT);
        data.appendChild(report);

        Element name = document.createElement(QualysScannerConstants.NAME_KEYWORD);
        name.appendChild(document.createTextNode("Web Application Report " + webAppId + " : "));
        report.appendChild(name);

        Element description = document.createElement(QualysScannerConstants.DESCRIPTION);
        description.appendChild(document.createTextNode("Web App Report for : " + jobId + " Date : " + getDate()));
        report.appendChild(description);

        Element format = document.createElement(QualysScannerConstants.FORMAT);
        format.appendChild(document.createTextNode(reportFormat));
        report.appendChild(format);

        Element type = document.createElement(QualysScannerConstants.TYPE_KEYWORD);
        type.appendChild(document.createTextNode(QualysScannerConstants.WAS_APP_REPORT));
        report.appendChild(type);

        Element config = document.createElement(QualysScannerConstants.CONFIG_KEYWORD);
        report.appendChild(config);

        Element webAppReport = document.createElement(QualysScannerConstants.WEB_APP_REPORT_KEYWORD);
        config.appendChild(webAppReport);

        Element target = document.createElement(QualysScannerConstants.TARGET);
        webAppReport.appendChild(target);

        Element webapps = document.createElement(QualysScannerConstants.WEBAPPS_KEYWORD);
        target.appendChild(webapps);

        Element webapp = document.createElement(QualysScannerConstants.QUALYS_WEBAPP_KEYWORD);
        webapps.appendChild(webapp);

        Element id = document.createElement(QualysScannerConstants.ID_KEYWORD);
        id.appendChild(document.createTextNode(webAppId));
        webapp.appendChild(id);

        Element template = document.createElement(QualysScannerConstants.TEMPLATE_KEYWORD);
        report.appendChild(template);

        Element templateId = document.createElement(QualysScannerConstants.ID_KEYWORD);
        templateId.appendChild(document.createTextNode(reportTemplateID));
        template.appendChild(templateId);

        StringWriter stringWriter = XMLUtil.buildSecureStringWriter(document);
        createReportRequestBody = stringWriter.getBuffer().toString();

        return createReportRequestBody;

    }

    /**
     * Build launch scan request body.
     *
     * @param scanContext qualys scanner parameters
     * @return launch scan request body in XML format
     * @throws ParserConfigurationException error occurred while parsing
     * @throws TransformerException         error occurred while building secure string writer
     */
    public static String buildScanLaunchRequest(ScanContext scanContext)
            throws ParserConfigurationException, TransformerException {
        String launchScanRequestBody;
        DocumentBuilderFactory dbf = XMLUtil.getSecuredDocumentBuilderFactory();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document doc = builder.newDocument();
        Element root = doc.createElement(QualysScannerConstants.SERVICE_REQUEST);
        doc.appendChild(root);

        Element data = doc.createElement(QualysScannerConstants.DATA);
        root.appendChild(data);

        Element wasScan = doc.createElement(QualysScannerConstants.WAS_SCAN);
        data.appendChild(wasScan);

        Element name = doc.createElement(QualysScannerConstants.NAME_KEYWORD);
        name.appendChild(doc.createTextNode(
                QualysScannerConstants.QUALYS_SCAN_NAME_PREFIX + scanContext.getWebAppId() + getDate()));
        wasScan.appendChild(name);

        Element type = doc.createElement(QualysScannerConstants.TYPE_KEYWORD);
        type.appendChild(doc.createTextNode(scanContext.getType()));
        wasScan.appendChild(type);

        Element target = doc.createElement(QualysScannerConstants.TARGET);
        wasScan.appendChild(target);

        Element webApp = doc.createElement("webApp");
        target.appendChild(webApp);

        Element webAppId = doc.createElement(QualysScannerConstants.ID_KEYWORD);
        webAppId.appendChild(doc.createTextNode(scanContext.getWebAppId()));
        webApp.appendChild(webAppId);

        Element webAppAuthRecord = doc.createElement("webAppAuthRecord");
        target.appendChild(webAppAuthRecord);

        // If authentication script is not provided, default auth record will be used.
        if (scanContext.getAuthId() == null) {
            Element defaultAuthRecord = doc.createElement(QualysScannerConstants.IS_DEFAULT);
            defaultAuthRecord.appendChild(doc.createTextNode(QualysScannerConstants.TRUE));
            webAppAuthRecord.appendChild(defaultAuthRecord);
        } else {
            Element webAppAuthRecordId = doc.createElement(QualysScannerConstants.ID_KEYWORD);
            webAppAuthRecordId.appendChild(doc.createTextNode(scanContext.getAuthId()));
            webAppAuthRecord.appendChild(webAppAuthRecordId);
        }

        Element scannerAppliance = doc.createElement(QualysScannerConstants.SCANNER_APPILIANCE);
        target.appendChild(scannerAppliance);

        Element scannerApplianceType = doc.createElement(QualysScannerConstants.TYPE_KEYWORD);
        scannerApplianceType.appendChild(doc.createTextNode(scanContext.getScannerApplianceType()));
        scannerAppliance.appendChild(scannerApplianceType);

        Element profile = doc.createElement(QualysScannerConstants.PROFILE);
        wasScan.appendChild(profile);

        Element profileId = doc.createElement(QualysScannerConstants.ID_KEYWORD);
        profileId.appendChild(doc.createTextNode(scanContext.getProfileId()));
        profile.appendChild(profileId);

        Element progressiveScanning = doc.createElement(QualysScannerConstants.PROGRESSIVE_SCANNING_KEYWORD);
        progressiveScanning.appendChild(doc.createTextNode(scanContext.getProgressiveScanning()));
        wasScan.appendChild(progressiveScanning);

        StringWriter stringWriter = XMLUtil.buildSecureStringWriter(doc);
        launchScanRequestBody = stringWriter.getBuffer().toString();

        return launchScanRequestBody;
    }

    /**
     * Build request body to add crawling scripts and relevant configuration for an application in qualys.
     *
     * @param listOfCrawlingScript crawling script objects
     * @return request body in XML format
     * @throws ParserConfigurationException error occurred while parsing
     * @throws IOException                  IOException error occurred while performing any file operations
     * @throws TransformerException         error occurred while building secure string writer
     */
    public static String buildCrawlingSettingRequestBody(List<CrawlingScript> listOfCrawlingScript)
            throws ParserConfigurationException, IOException, TransformerException {
        String addCrawlingScriptRequestBody;
        DocumentBuilderFactory dbf = XMLUtil.getSecuredDocumentBuilderFactory();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element root = doc.createElement(QualysScannerConstants.SERVICE_REQUEST);
        doc.appendChild(root);

        Element data = doc.createElement(QualysScannerConstants.DATA);
        root.appendChild(data);

        Element webApp = doc.createElement(QualysScannerConstants.QUALYS_WEBAPP_KEYWORD);
        data.appendChild(webApp);

        Element crawlingScripts = doc.createElement(QualysScannerConstants.CRAWLINGSCRIPTS);
        webApp.appendChild(crawlingScripts);

        Element set = doc.createElement(QualysScannerConstants.SET);
        crawlingScripts.appendChild(set);

        for (int i = 0; i < listOfCrawlingScript.size(); i++) {
            Element seleniumScript = doc.createElement(QualysScannerConstants.CRAWLING_SELENIUM_SCRIPT);
            set.appendChild(seleniumScript);

            Element name = doc.createElement(QualysScannerConstants.NAME_KEYWORD);
            name.appendChild(doc.createCDATASection(
                    listOfCrawlingScript.get(i).getScriptFileName() + " : " + RequestBodyBuilder.getDate()));
            seleniumScript.appendChild(name);

            Element startingUrl = doc.createElement(QualysScannerConstants.STARTING_URL);
            startingUrl.appendChild(doc.createCDATASection(listOfCrawlingScript.get(i).getStartingUrl()));
            seleniumScript.appendChild(startingUrl);

            Element scriptContent = doc.createElement(QualysScannerConstants.DATA);
            scriptContent.appendChild(doc.createCDATASection(
                    FileUtil.getContentFromFile(listOfCrawlingScript.get(i).getScriptFile().getAbsolutePath())));
            seleniumScript.appendChild(scriptContent);

            Element requiresAuthentication = doc.createElement(QualysScannerConstants.REQUIRE_AUTHENTICATION);
            requiresAuthentication
                    .appendChild(doc.createTextNode(listOfCrawlingScript.get(i).getRequredAuthentication().toString()));
            seleniumScript.appendChild(requiresAuthentication);

            Element startingUrlRegex = doc.createElement(QualysScannerConstants.STARTING_URL_REGEX);
            startingUrlRegex
                    .appendChild(doc.createTextNode(listOfCrawlingScript.get(i).getStartingUrlRegex().toString()));
            seleniumScript.appendChild(startingUrlRegex);
        }

        StringWriter stringWriter = XMLUtil.buildSecureStringWriter(doc);
        addCrawlingScriptRequestBody = stringWriter.getBuffer().toString();

        return addCrawlingScriptRequestBody;
    }

    /**
     * Get the current date.
     *
     * @return formatted date and time
     */
    public static String getDate() {
        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("E yyyy.MM.dd 'at' hh:mm:ss");
        return ft.format(date);
    }
}
