package com.github.yehortpk.parser.scrapper.site;

import com.github.yehortpk.parser.exceptions.RequestMetadataConnectionException;
import com.github.yehortpk.parser.exceptions.RequestMetadataParamNotImplemented;
import com.github.yehortpk.parser.exceptions.RequestMetadataNotImplementedException;
import com.github.yehortpk.parser.models.*;
import com.github.yehortpk.parser.progress.ParserProgress;
import com.github.yehortpk.parser.services.ParsingProgressService;
import com.github.yehortpk.parser.scrapper.page.PageScrapper;
import com.github.yehortpk.parser.scrapper.page.PageScrapperResponse;
import com.github.yehortpk.parser.scrapper.site.metadata.SiteMetadataParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.github.yehortpk.parser.utils.LogUtils.createErrorMessage;

@Component
@Slf4j
public abstract class SiteScrapperImpl implements SiteScrapper {
    @Autowired
    protected ParsingProgressService parsingProgressService;
    protected CompanyDTO company;

    @Override
    public List<CompletableFuture<PageDTO>> scrapCompanyVacancies(CompanyDTO company){
        this.company = company;

        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());

        Map<String, String> requestData = new HashMap<>(company.getData());
        Map<String, String> requestHeaders = new HashMap<>(company.getHeaders());
        Map<String, String> requestCookies = new HashMap<>(company.getCookies());

        int pagesCount = 1;

        if (isRequestNeedMetadata()) {
            MetadataParams metadataParams = generateRequestMetadata();

            for (Map.Entry<String, String> dataES : requestData.entrySet()) {
                String value = dataES.getValue();
                if (isValueBinding(value) && !value.equals("{page}")) {
                    String key = dataES.getKey();
                    requestData.put(key, metadataParams.getRequestData().get(value));
                }
            }

            for (Map.Entry<String, String> headersES : requestHeaders.entrySet()) {
                String value = headersES.getValue();
                if (isValueBinding(value) && !value.equals("{page}")) {
                    String key = headersES.getKey();
                    requestData.put(key, metadataParams.getRequestHeaders().get(value));
                }
            }

            requestCookies = metadataParams.getRequestCookies();
            pagesCount = metadataParams.getPagesCount();

            parserProgress.initPages(pagesCount);
        }

        PageRequestParams pageRequestParams = PageRequestParams.builder()
                .data(requestData)
                .cookies(requestCookies)
                .headers(requestHeaders)
                .build();

        return scrapPages(pagesCount, pageRequestParams);
    }


    private List<CompletableFuture<PageDTO>> scrapPages(int pagesCount, PageRequestParams pageRequestParams) {
        // Scrap all pages in parallel with specific interval
        List<CompletableFuture<PageDTO>> pageScrapperListFut = new ArrayList<>();

        for (int pageID = 1; pageID <= pagesCount; pageID++) {
            PageRequestParams localPageRequestParams = generatePageRequestParams(pageRequestParams, pageID);

            int finalPageID = pageID;
            // Pages of one company are staggered to keep the site from seeing a burst of requests.
            // The scrapping itself is throttled further downstream by the browser pool.
            Executor delayedExecutor = CompletableFuture.delayedExecutor(
                    (long) (finalPageID - 1) * generateDelayBetweenPagesMS(), TimeUnit.MILLISECONDS);
            CompletableFuture<PageDTO> scheduledFuture = CompletableFuture.supplyAsync(
                    () -> scrapPage(finalPageID, localPageRequestParams), delayedExecutor);

            pageScrapperListFut.add(scheduledFuture);
        }

        return pageScrapperListFut;
    }

    protected PageRequestParams generatePageRequestParams(PageRequestParams commonSiteParams, int pageID) {
        PageRequestParams localPageRequestParams = SerializationUtils.clone(commonSiteParams);

        localPageRequestParams.setPageURL(generateVacanciesPageURL(pageID));
        localPageRequestParams.setConnectionMethod(generateRequestMethod(pageID));

        for (Map.Entry<String, String> es : localPageRequestParams.getData().entrySet()) {
            if (es.getValue().equals("{page}")) {
                localPageRequestParams.getData().put(es.getKey(), String.valueOf(pageID));
            }
        }

        for (Map.Entry<String, String> es : localPageRequestParams.getHeaders().entrySet()) {
            if (es.getValue().equals("{page}")) {
                localPageRequestParams.getHeaders().put(es.getKey(), String.valueOf(pageID));
            }
        }

        return localPageRequestParams;
    }

    private PageDTO scrapPage(int pageID, PageRequestParams pageRequestParams) {
        PageScrapper pageScrapper = generatePageScrapper(pageID);
        ParserProgress parserProgress = parsingProgressService.getParsers().get(company.getCompanyId());
        PageScrapperResponse pageScrapperResponse;
        try {
            pageScrapperResponse = pageScrapper.scrapPage(pageRequestParams);
        } catch (IOException e) {
            String errorLog = String.format("Company: %s, Page: %s error: %s",
                    company.getTitle(), pageID, createErrorMessage(e));
            log.error(errorLog);

            parserProgress.markPageError(pageID);
            parserProgress.addPageLog(pageID, ParserProgress.LogLevelEnum.ERROR, errorLog);
            throw new RuntimeException(e);
        }

        String successLog = String.format("Company: %s, Page: %s, was scrapped", company.getTitle(), pageID);
        parserProgress.addPageLog(pageID, ParserProgress.LogLevelEnum.INFO, successLog);
        log.info(successLog);

        return new PageDTO(
                pageID,
                pageRequestParams.getPageURL(),
                pageRequestParams.getData(),
                pageRequestParams.getHeaders(),
                pageScrapperResponse.getBody()
        );
    }


    private MetadataParams generateRequestMetadata() {
        if (!(this instanceof SiteMetadataParser siteMetadataParser)) {
            throw new RequestMetadataNotImplementedException();
        }
        PageRequestParams metadataRequestConnectionParams = generateMetadataRequestConnectionParams();
        PageScrapperResponse requestMetadataScrapperResponse;
        try {
            requestMetadataScrapperResponse = generatePageScrapper(1).scrapPage(metadataRequestConnectionParams);
        } catch (IOException e) {
            throw new RequestMetadataConnectionException(e);
        }

        int pagesCount = siteMetadataParser.extractPagesCount(requestMetadataScrapperResponse);
        Map<String, String> metadataHeaders = siteMetadataParser.extractHeaders(requestMetadataScrapperResponse);
        for (String value : company.getHeaders().values()) {
            if (isValueBinding(value) && !value.equals("{page}") &&
                    (metadataHeaders == null ||
                            metadataHeaders.get(value) == null)) {
                throw new RequestMetadataParamNotImplemented(value);
            }
        }

        Map<String, String> metadataData = siteMetadataParser.extractData(requestMetadataScrapperResponse);
        for (String value : company.getData().values()) {
            if (isValueBinding(value) && !value.equals("{page}") &&
                    (metadataData == null ||
                    metadataData.get(value) == null)) {
                throw new RequestMetadataParamNotImplemented(value);
            }
        }

        Map<String, String> metadataCookies = siteMetadataParser.extractCookies(requestMetadataScrapperResponse);

        return new MetadataParams(pagesCount, metadataData, metadataHeaders, metadataCookies);
    }

    protected PageRequestParams generateMetadataRequestConnectionParams() {
        Map<String, String> requestData = new HashMap<>(company.getData());
        requestData = requestData.entrySet().stream().filter(es -> !isValueBinding(es.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, String> requestHeaders = new HashMap<>(company.getHeaders());
        requestHeaders = requestHeaders.entrySet().stream().filter(es -> !isValueBinding(es.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


        PageRequestParams pageRequestParams = PageRequestParams.builder()
                .data(requestData)
                .headers(requestHeaders)
                .pageURL(company.getVacanciesURL().replace("{page}", "1"))
                .build();

        if (company.getData().get("page") != null) {
            pageRequestParams.getData().put("page", "1");
        }

        return pageRequestParams;
    }

    protected boolean isRequestNeedMetadata() {
        for (Map.Entry<String, String> dataEntry : company.getData().entrySet()) {
            if (isValueBinding(dataEntry.getValue())) {
                return true;
            }
        }

        for (Map.Entry<String, String> headerEntry : company.getHeaders().entrySet()) {
            if (isValueBinding(headerEntry.getValue())) {
                return true;
            }
        }

        return isValueBinding(company.getVacanciesURL());
    }

    private boolean isValueBinding(String value) {
        return value.contains("{") && value.contains("}") && value.indexOf('{') < value.indexOf('}');
    }

    protected abstract String generateVacanciesPageURL(int pageID);
    protected abstract Connection.Method generateRequestMethod(int pageID);
    protected abstract int generateDelayBetweenPagesMS();
    protected abstract PageScrapper generatePageScrapper(int pageID);
}
