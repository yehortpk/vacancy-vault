# VacancyVault

[![Static Badge](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Static Badge](https://img.shields.io/badge/SpringBoot-3-green.svg)](https://docs.spring.io/spring-boot/docs/3.1.5/reference/html/)
[![Static Badge](https://img.shields.io/badge/Angular-18-red.svg)](https://v18.angular.dev/overview)

## Description
This project aims to make the process of job searching easier by automating the collection of vacancies from various company websites.
With this system, users can effortlessly stay updated on job openings without the hassle of manually scouring multiple websites.
Our project includes a robust library that allows users to create custom crawlers for vacancy sites of their choice.
This flexibility ensures compatibility with a wide range of company websites, catering to diverse job seekers' needs.
Also, users can subscribe to specific companies of interest, enabling the crawler to automatically detect and notify them of new job postings.

## Table of Contents
- [Installation](#installation)
  - [Ports](#ports)
  - [Access levels](#access-levels)
- [Architecture](#architecture)
- [Usage](#usage)
  - [Code Usage](#code-usage)
    - [Scraper](#scraper)
      - [Metadata call](#metadata-call)
    - [Parser](#parser)
      - [Vacancy details](#vacancy-details)
    - [Full example](#full-example)
  - [Crawler structure](#crawler-structure)
  - [Page scrapers](#page-scrapers)
  - [Class diagrams](#class-diagrams)
  - [Create companies](#create-companies)
    - [Company source](#company-source)
    - [Request parameters](#request-parameters)
      - [Dynamic request parameters](#dynamic-request-parameters)
- [Filters](#filters)
- [Telegram bot](#telegram-bot)

## Installation
1. Create a new Telegram bot through <a href="https://t.me/BotFather">Bot Father</a>;
2. Extract API Token and Username of the bot;
3. Clone the repository:
```
git clone https://github.com/yehortpk/vacancy-vault.git
```
4. Create a <code>.env</code> file in the project root.

   **Mandatory**

   Services (only Telegram for now):
   ```env
   TG_BOT_API_KEY=
   TG_BOT_USERNAME=
   ```
   - <code>TG_BOT_API_KEY</code> - API token of the bot from step 2.
   - <code>TG_BOT_USERNAME</code> - username of the bot from step 2.

   Security:
   ```env
   JWT_SECRET=
   TELEGRAM_PROVIDER_SECRET=
   ```
   - <code>JWT_SECRET</code> - key the router signs the users' login tokens with; no other service holds it.
     At least 32 bytes, either Base64 or raw text - generate one with <code>openssl rand -base64 32</code>.
     Changing it invalidates every existing session.
   - <code>TELEGRAM_PROVIDER_SECRET</code> - shared secret the Telegram service presents to the router's
     <code>/connector</code> endpoints, the same for both services - generate one with <code>openssl rand -base64 32</code>.

   Docker compose refuses to start without either of them.

   **Optional**

   Every variable below falls back to the value shown, so add only the ones you want to change.

   Databases:
   ```env
   POSTGRES_USERNAME=notifier
   POSTGRES_PASSWORD=notifier
   MONGO_ROOT_USERNAME=progress
   MONGO_ROOT_PASS=progress
   ```
   - <code>POSTGRES_USERNAME</code>, <code>POSTGRES_PASSWORD</code> - credentials of the notifier PostgreSQL
     database, created as its superuser. The crawler, the dashboard and the router share the database, each in a
     schema of its own.
   - <code>MONGO_ROOT_USERNAME</code>, <code>MONGO_ROOT_PASS</code> - root credentials of the crawler-progress
     MongoDB database, where the crawler keeps its runs.

   The credentials are applied only when the database volumes are first created, so changing them
   later also means changing them inside the database. The database ports are published on the
   host, so pick your own passwords on a server reachable from the internet.

   Crawler admin:
   ```env
   CRAWLER_ADMIN_USERNAME=admin
   CRAWLER_ADMIN_PASSWORD=CRAWLER_ADMIN_PASSWORD
   ```
   The first admin of the crawler's own panel, created by the crawler on startup. The password needs at least 8
   characters. See [Access levels](#access-levels).

   These defaults are public, so set your own password on a server reachable from the internet. Set
   both to empty values to create no admin account.

   Crawler load:
   ```env
   CRAWLER_BROWSER_POOL_SIZE=2
   ```
   <code>CRAWLER_BROWSER_POOL_SIZE</code> is how many Chromium instances may render dynamic sites at once, or
   <code>max</code>.

   Chromium dominates the crawler's memory, at roughly 400MB per instance, so this is the main
   knob. Raise it to crawl dynamic sites faster, or set <code>max</code> for as many as the host
   can afford: half of its CPUs, but no more than fit into its memory once about 3GB is left to
   the rest of the stack, and never less than <code>1</code>. The crawler logs the size it picked
   at startup.

   The crawler runs without CPU or memory limits by default. To cap it, for example on a server
   shared with other apps, add a <code>docker-compose.override.yaml</code> next to
   <code>docker-compose.yaml</code>; docker compose picks it up on its own:
   ```yaml
   services:
     crawler:
       mem_limit: 2g # hard ceiling, the crawler is killed when it runs out
       cpus: 2       # soft ceiling, the crawler only slows down
   ```
   With a memory limit, <code>max</code> sizes the pool from the limit rather than the host, still
   leaving the 3GB reserve, so it picks <code>1</code> below about 4GB. Set a number instead if the
   limit allows more: the crawler itself takes about 1GB, each instance about 400MB more.

5. Create and run with docker
```
docker compose up --build
```
The crawler does not crawl on its own: every run is started from its admin panel at http://localhost:4201 (see
[Access levels](#access-levels)).

### Ports
Docker compose publishes these ports on the host:

| Port | Service |
|---|---|
| 4200 | Vacancy dashboard web app (Angular dev server, reloads on change) |
| 4201 | Crawler admin panel (Angular dev server, reloads on change) |
| 8081 | Router API |
| 8082 | Crawler API |
| 8083 | Dashboard API |
| 5005 | Crawler remote debugging (JDWP) |
| 5006 | Router remote debugging (JDWP) |
| 5007 | Telegram bot remote debugging (JDWP) |
| 5008 | Dashboard remote debugging (JDWP) |
| 5432 | PostgreSQL |
| 27017 | MongoDB |
| 29092 | Kafka, for clients on the host |

### Access levels
- <b>Guests</b> (not logged in) browse the vacancy dashboard at http://localhost:4200, and one company's vacancies
  at <code>/company/&lt;id&gt;</code>, opened from the company name on a vacancy.
- <b>Users</b> register at <code>/register</code> and get their own account room at <code>/account</code>: linking a
  messenger with a one-time code (for Telegram, send <code>/link &lt;code&gt;</code> to the bot) and the list of
  linked chats. At <code>/filters</code> they write their [filters](#filters) and choose which linked chats each one
  notifies. Every account is the same: there are no roles.
- <b>The operator</b> logs in to the crawler's own admin panel at http://localhost:4201, to start a crawl and watch
  its progress. This is a separate login, unrelated to the user accounts: a user's session opens nothing in the
  panel, and the operator needs no user account.

The operator's account comes from <code>CRAWLER_ADMIN_USERNAME</code> and <code>CRAWLER_ADMIN_PASSWORD</code> (see their
defaults in the optional env variables): on startup the crawler creates it if that username does not exist yet, and
you log in with exactly those credentials. It only ever creates the account, never changes one, so changing
<code>CRAWLER_ADMIN_PASSWORD</code> later does not change the stored password. There is no registration: any further
admin is a row in the <code>crawler.admin</code> table, with a BCrypt hash as its password.

## Architecture
Three services, each owning its own data and sharing nothing but Kafka topics:

- <b>crawler</b> crawls the company sites and publishes what it finds. It keeps the companies, its memory of the
  vacancies it has already published, the history of its runs (MongoDB) and its own admins, and it serves its own
  admin panel. No other service calls it.
- <b>dashboard</b> stores the published vacancies and serves the public vacancy listing, also per company. It has
  no users and no authentication.
- <b>router</b> holds the accounts: registration and login, filters, linked chats, and matching every new vacancy
  against the filters to notify the chats they are bound to. It stores no vacancies.

| Topic | Producer | Consumers | Message |
|---|---|---|---|
| <code>vacancies</code> | crawler | dashboard, router | a new vacancy with its company (id, title, URL), or the link of a vacancy that is gone for good; keyed by the link |
| <code>telegram-notifier</code> | router | telegram-bot | one notification for one chat |

A vacancy that disappears from its site is kept for a week, in case it comes back; only then does the crawler drop it
and publish its removal to <code>vacancies</code>. Both kinds share one topic keyed by the link, so a vacancy that is
removed and then listed again reaches every consumer in that order; the kind travels in the <code>__TypeId__</code>
header, and the router skips removals. Every consumer skips a link it has already seen, so a
redelivered or republished vacancy is stored once and notified about once.

The services share one PostgreSQL database, <code>notifier</code>, each in its own schema (<code>crawler</code>,
<code>dashboard</code>, <code>router</code>) that it creates and migrates itself. No service reads another's schema.
The only REST call between services is the Telegram bot reporting linked chats to the router.

## Usage
The app needs the client to create crawling information for both sides - in the code itself and in the database. Let's look at the features of the code.

### Code usage
Every company is crawled by a Spring bean that implements the <code>CompanyCrawler</code> interface. The interface has two
halves, and a company crawler needs both to work:
- the <b>scraper</b>, <code>scrapeCompanyVacancies(company)</code>, fetches every page of the company's vacancies, see [Scraper](#scraper);
- the <b>parser</b>, <code>parsePageVacancies(page, company)</code>, turns one fetched page into vacancies, see [Parser](#parser).

You don't have to write either half yourself. The abstract class <code>DefaultCompanyCrawler</code> implements both and
runs the main flow: the metadata call, the placeholders, the delays, the vacancy details calls, the stop requests and
the errors. A company crawler extends it and only describes its site, with two methods:
- <code>configurePageScrapeSettings(company, pageId)</code> - how a page is requested, for the scraper.
- <code>configurePageParser()</code> - how a page turns into vacancies, for the parser.

```java
@Component("acme-company")
public class AcmeCrawler extends DefaultCompanyCrawler {
    @Override
    protected ScrapeSettings configurePageScrapeSettings(CompanyDTO company, int pageId) {
        return ScrapeSettings.builder().build();
    }

    @Override
    protected PageScraper configurePageScraper(int pageId) {
        return new StaticPageScraper();
    }

    @Override
    protected PageParser configurePageParser() {
        return new AcmePageParser();
    }

    private static class AcmePageParser implements PageParser {
        @Override
        public List<VacancyDTO> parsePage(PageScraperResponse page, CompanyDTO company) {
            return Jsoup.parse(page.getBody()).select("a.vacancy").stream()
                    .map(link -> VacancyDTO.builder().title(link.text()).link(link.attr("href")).build())
                    .toList();
        }
    }
}
```

The bean name (<code>acme-company</code> here) is the company's <code>bean_class</code> in the database, see [Create companies](#create-companies).
Crawlers hold no state of a run, so one bean may serve several companies.

A vacancy's <code>countries</code> are ISO 3166-1 alpha-2 codes: two lower-case letters per country, e.g.
<code>pl</code>, <code>ua</code>, <code>us</code>, and <code>gb</code> for the United Kingdom (not <code>uk</code>).
<code>Locations</code> (in the <code>utils</code> package) lists every code with its lower-case English name:
<code>findCountryCode("Poland")</code> gives <code>pl</code> and <code>findCountryName("pl")</code> gives <code>poland</code>.
A crawler maps any other spelling a site uses itself. Leave the set empty when the site does not say. The dashboard
shows each code by its English name and filters the listing by it.

#### Scraper
The scraper fetches the company's pages. A company crawler describes them with:
- <code>configurePageScrapeSettings(company, pageId)</code> - how the page is requested, as a <code>ScrapeSettings</code>.
  The crawler always sets <code>pageURL</code>: it is never taken from the company's config. Leave data, headers or
  cookies unset to take them from the company's config. A crawler that reads the company's <code>vacancies_url</code>
  as is extends <code>VacanciesUrlCompanyCrawler</code> (in the companies' <code>common</code> package) instead of
  implementing this method. A crawler that reads the site's API sets <code>pageURL(company.getApiVacanciesURL())</code>,
  and <code>connectionMethod(POST)</code> when the API needs it.
- <code>configurePageScraper(pageId)</code> - an optional hook, the scraper the page is fetched with. Defaults to
  <code>new StaticPageScraper()</code>. Return <code>new DynamicPageScraper(browserPool, readySelector)</code> for a site
  whose vacancies a browser has to render (inject the <code>BrowserPool</code> bean into the crawler). See
  [Page scrapers](#page-scrapers) for choosing one.
- <code>configureMetadataCrawler()</code> - an optional hook, the call made before the pages. Defaults to
  <code>NoCallMetadataCrawler</code>: no request, one page. See [Metadata call](#metadata-call).

Every call of the scraper - the pages, the metadata call and the vacancy details calls - is fetched with a
<code>PageScraper</code>, see [Page scrapers](#page-scrapers).

<code>pageId</code> is the page's number, starting from 1. Use it to request that page, e.g. as an offset
<code>(pageId - 1) * PAGE_SIZE</code> in the request data, or put the <code>{page}</code> placeholder into the URL or data
instead (see [Dynamic request parameters](#dynamic-request-parameters)). Use it to space the pages out as well: the delay
between pages defaults to 0, so every page of a company is sent at once, and a site that needs pacing sets it per page,
e.g. <code>delay(Duration.ofSeconds(pageId - 1))</code> to start page N after N-1 seconds.

##### Metadata call
A metadata call is one request made before any page, to learn what the pages need: the page count, placeholder values
like a CSRF token or a session id that only a first request reveals, and headers and cookies to send with every page.

A company crawler that needs it implements <code>CompanyMetadataCrawler</code> in a private nested class (e.g.
<code>AcmeMetadataCrawler</code>) and returns a new instance of it from <code>configureMetadataCrawler()</code>:
- <code>configureScrapeSettings(company)</code> describes the request. It is used as returned: nothing is filled from
  the company.
- <code>configurePageScraper()</code> creates the scraper with <code>new</code>. A metadata call whose answer is known
  without a request, like a fixed page count, returns <code>new NoCallPageScraper()</code>.
- <code>parseMetadata(response)</code> reads the <code>MetadataParams</code> from the response: <code>pagesCount</code>
  (1 unless set), <code>placeholders</code> by name without braces, and <code>requestHeaders</code> and
  <code>requestCookies</code>, which are added to every page, overriding configured ones by key.

A metadata call that fails, or whose response can't be parsed, marks the company ERROR. See the
[Full example](#full-example).

#### Parser
The parser turns a fetched page into vacancies. A company crawler describes it with:
- <code>configurePageParser()</code> - the <code>PageParser</code> a page is parsed with. A company crawler implements
  <code>PageParser</code> in a private nested class (e.g. <code>AcmePageParser</code>) and returns a new instance of it.
  Its <code>parsePage(page, company)</code> turns a fetched page into vacancies, as the page itself
  describes them, and throws <code>VacancyPageParseException</code> when the page can't be parsed. Read HTML with
  <code>Jsoup.parse(page.getBody())</code> and JSON with Gson.
- <code>configureVacancyDetailsCrawler()</code> - an optional hook, the call made for every vacancy of a parsed page, to
  read the vacancy's own page (e.g. its description). Defaults to <code>NoCallVacancyDetailsCrawler</code>: no request,
  the vacancy stays as its page described it. See [Vacancy details](#vacancy-details).

##### Vacancy details
<b>Warning!</b> A vacancy details call is made for <b>every vacancy of every page on every run</b>, not only for the new
ones. It multiplies the requests sent to the company's site, which can get the crawler rate-limited or banned there.
Use it with consideration, and space the calls out with a delay.

A company crawler that needs it implements <code>VacancyDetailsCrawler</code> in a private nested class (e.g.
<code>AcmeVacancyDetailsCrawler</code>) and returns a new instance of it from <code>configureVacancyDetailsCrawler()</code>:
- <code>configureScrapeSettings(previewVacancy, company, vacancyOrderOnPage)</code> describes the request. It is used as
  returned: nothing is filled from the vacancy or the company. <code>vacancyOrderOnPage</code> is the vacancy's place on
  its page, starting from 1, for spacing the calls out: the delay counts from the moment the page's vacancies are
  scheduled. Pages are parsed in parallel, so the calls of different pages can overlap.
- <code>configurePageScraper()</code> creates the scraper with <code>new</code>.
- <code>parseVacancyDetails(previewVacancy, response)</code> returns the vacancy that replaces the preview one. Start
  from <code>previewVacancy.toBuilder()</code> and set only what the page adds, and keep the <code>link</code>: it is the
  vacancy's identity, and a different link makes it another vacancy.

A call that fails (<code>VacancyDetailsScrapeException</code>), can't be parsed or returns <code>null</code>
(<code>VacancyDetailsParseException</code>), or is skipped by a stop request keeps the preview vacancy and is logged on
its page. The page itself doesn't fail. See the [Full example](#full-example).

#### Full example
A crawler whose metadata call reads the page count and a CSRF token from the first vacancies page, and whose vacancy
details calls read every vacancy's description:

```java
@Component("acme-company")
public class AcmeCrawler extends DefaultCompanyCrawler {
    private static final Duration PAGE_DELAY = Duration.ofSeconds(1);
    private static final Duration VACANCY_DETAILS_DELAY = Duration.ofSeconds(2);

    @Override
    protected ScrapeSettings configurePageScrapeSettings(CompanyDTO company, int pageId) {
        return ScrapeSettings.builder()
                .pageURL(company.getVacanciesURL() + "?page={page}&csrf={csrf}")
                .delay(PAGE_DELAY.multipliedBy(pageId - 1L))
                .build();
    }

    @Override
    protected CompanyMetadataCrawler configureMetadataCrawler() {
        return new AcmeMetadataCrawler();
    }

    @Override
    protected PageParser configurePageParser() {
        return new AcmePageParser();
    }

    @Override
    protected VacancyDetailsCrawler configureVacancyDetailsCrawler() {
        return new AcmeVacancyDetailsCrawler();
    }

    private static class AcmeMetadataCrawler implements CompanyMetadataCrawler {
        @Override
        public ScrapeSettings configureScrapeSettings(CompanyDTO company) {
            return ScrapeSettings.builder()
                    .pageURL(company.getVacanciesURL())
                    .build();
        }

        @Override
        public PageScraper configurePageScraper() {
            return new StaticPageScraper();
        }

        @Override
        public MetadataParams parseMetadata(PageScraperResponse response) {
            Document document = Jsoup.parse(response.getBody());
            return MetadataParams.builder()
                    .pagesCount(Integer.parseInt(document.select(".pagination a").last().text()))
                    .placeholders(Map.of("csrf", document.selectFirst("meta[name=csrf-token]").attr("content")))
                    .requestCookies(response.getCookies())
                    .build();
        }
    }

    private static class AcmePageParser implements PageParser {
        @Override
        public List<VacancyDTO> parsePage(PageScraperResponse page, CompanyDTO company) {
            return Jsoup.parse(page.getBody()).select("a.vacancy").stream()
                    .map(link -> VacancyDTO.builder().title(link.text()).link(link.absUrl("href")).build())
                    .toList();
        }
    }

    private static class AcmeVacancyDetailsCrawler implements VacancyDetailsCrawler {
        @Override
        public ScrapeSettings configureScrapeSettings(VacancyDTO previewVacancy, CompanyDTO company,
                                                      int vacancyOrderOnPage) {
            return ScrapeSettings.builder()
                    .pageURL(previewVacancy.getLink())
                    .delay(VACANCY_DETAILS_DELAY.multipliedBy(vacancyOrderOnPage - 1L))
                    .build();
        }

        @Override
        public PageScraper configurePageScraper() {
            return new StaticPageScraper();
        }

        @Override
        public VacancyDTO parseVacancyDetails(VacancyDTO previewVacancy, PageScraperResponse response) {
            return previewVacancy.toBuilder()
                    .description(Jsoup.parse(response.getBody()).selectFirst(".job-description").text())
                    .build();
        }
    }
}
```

Here the metadata call fetches the first vacancies page, so the pages start one second apart and the vacancy details
calls of a page two seconds apart. The session cookie the first page sets is sent with every page.

### Crawler structure
![Crawler structure](docs/crawler-structure.svg)

<code>CrawlerRunnerService</code> receives every crawler bean as a map by bean name and looks up each company's crawler
once. It calls the scraper, <code>scrapeCompanyVacancies(company)</code>, which <code>DefaultCompanyCrawler</code>
implements as:
1. <b>Metadata call.</b> The crawler's <code>CompanyMetadataCrawler</code> describes its request
   (<code>configureScrapeSettings(company)</code>, <code>configurePageScraper()</code>); the base class makes it, and the metadata
   crawler reads the <code>MetadataParams</code> from the response (<code>parseMetadata</code>).
2. <b>Every page's settings</b>, built before any page is sent: <code>configurePageScrapeSettings(company, pageId)</code>, unset fields
   filled from the company, the metadata headers and cookies added, and the placeholders filled.
3. <b>Every page</b> waits out its <code>delay</code>, then is fetched on a virtual thread of its own with
   <code>configurePageScraper(pageId)</code>. A stop request ends the wait at once.

The runner then calls the parser, <code>parsePageVacancies(page, company)</code>, for each fetched page, which
<code>DefaultCompanyCrawler</code> implements (it is final) as:
1. <b>Preview vacancies.</b> The crawler's <code>PageParser</code> (<code>configurePageParser()</code>) turns the page
   into vacancies with <code>parsePage(page, company)</code>.
2. <b>Vacancy details calls.</b> Every vacancy gets a virtual thread of its own: the crawler's
   <code>VacancyDetailsCrawler</code> describes the request (<code>configureScrapeSettings(previewVacancy, company,
   vacancyOrderOnPage)</code>, <code>configurePageScraper()</code>), the call waits out its <code>delay</code>, is made, and
   <code>parseVacancyDetails(previewVacancy, response)</code> returns the vacancy that replaces the preview one. All calls
   are waited for before the page's vacancies are returned.

It then publishes the new vacancies and, once every page was parsed, marks the vanished ones outdated.

### Page scrapers
A <code>PageScraper</code> fetches one page from a <code>ScrapeSettings</code> and returns a <code>PageScraperResponse</code>
(the page's id and URL, the response headers and cookies, and the body). It does I/O only: no waiting and no retries.
The pages, the metadata call and the vacancy details calls are all fetched with one. The scrapers are plain classes
created with <code>new</code>:
- <code>StaticPageScraper</code> - the default. Based on Java's <code>HttpClient</code>, for pages whose vacancies are in the HTML or the
  API response itself. Sends a GET or POST, follows redirects unless told not to, and decodes gzip and the response charset.
- <code>DynamicPageScraper(browserPool, readySelector)</code> - for pages a browser has to render. Uses <b>Playwright</b>: it borrows a
  headless Chromium from the <code>BrowserPool</code>, opens the page and waits until <code>readySelector</code> is on it, rather than for
  the whole page. <b>Warning!</b> It is resource-intensive, so use it only when <code>StaticPageScraper</code> is not enough.
- <code>NoCallPageScraper</code> - makes no request: returns an empty response with the settings' page id and URL. For
  a call whose answer is known without one; <code>NoCallMetadataCrawler</code> and
  <code>NoCallVacancyDetailsCrawler</code> use it.

<code>data</code> is the query string of a GET and the form body of a POST. If the request's <code>Content-Type</code> header is
<code>application/json</code>, a POST sends <code>data</code> as a JSON object instead, with every value as a string.

### Class diagrams
The detailed view of the same structure, as UML 2 class diagrams: attributes, operations, visibility, defaults and
thrown exceptions, including the data classes. Each shows one half of <code>CompanyCrawler</code>.

The scraper, <code>scrapeCompanyVacancies</code>, with the metadata call and the page scrapers
(<code>ScrapeSettings</code>, <code>MetadataParams</code>, <code>PageScraperResponse</code>):

![Crawler scraper class diagram](docs/crawler-scraper-class-diagram.svg)

The parser, <code>parsePageVacancies</code>, with the page parsers and the vacancy details call (<code>ScrapeSettings</code>,
<code>VacancyDTO</code>, <code>PageScraperResponse</code>):

![Crawler parser class diagram](docs/crawler-parser-class-diagram.svg)

### Create companies
The database is the preferred place to configure companies. You can also take them from another source by implementing
the <code>CompanySource</code> interface as a <code>@Component</code>, see [Company source](#company-source).

To add a new site for analysis, all you have to do is add the entity to the <i>company</i> table in the 
<i>crawler</i> schema of the <i>notifier</i> database (<code>crawler.company</code>). The schema and the table will be
created automatically after the first launch of the crawler. Companies live only there: the dashboard and the router
learn a company's title and URL from the vacancies the crawler publishes. You must fill out the following fields:
- <code>company_id</code> - id of the company
- <code>bean_class</code> - name of the @Bean in the code that corresponds this db entity;
- <code>title</code> - company title.
- <code>crawling_enabled</code> - enable/disable specific crawler;
- <code>main_page_url</code> - URL to the site main page;
- <code>vacancies_url</code> - URL to the page with the vacancies in the site;
- <code>api_vacancies_url</code> - URL to the endpoint for API to retrieve vacancies; used when the crawler sets it as <code>pageURL</code>

#### Company source
The crawler takes the companies of a run from a <code>CompanySource</code> bean (in the <code>company</code> package),
which returns them as a <code>List&lt;CompanyDTO&gt;</code>. The default, <code>DatabaseCompanySource</code>, reads the
companies above whose <code>crawling_enabled</code> is set. To take them from somewhere else, add your own
<code>@Component</code> that implements <code>CompanySource</code>: <code>DatabaseCompanySource</code> is marked
<code>@ConditionalOnMissingBean(CompanySource.class)</code>, so it is not created when another implementation exists.
Every other table, such as the crawled vacancies and the crawling history, stays in the database either way. Your source
must return the same <code>companyId</code> for a company on every run, since its crawled vacancies are kept under that
id, and a <code>beanClass</code> that names a crawler bean.

#### Request parameters
If you have request data that need to be sent with the request (e.g. GET request query data, or POST request body)
you may add this data in the <code>company_data</code> table of the same schema. Data becomes the query string of a GET
and the form body of a POST; a POST whose <code>Content-Type</code> header is <code>application/json</code> sends it as a JSON
object, with every value as a string.
Also, if you have special headers or cookies you want to send with every request, you may add them by creating new key-value
pairs in the <code>company_headers</code> and <code>company_cookies</code> tables.

##### Dynamic request parameters
Values may hold <code>{name}</code> placeholders, which are filled for every page: in the vacancies URL, in
<code>company_data</code> values and in <code>company_headers</code> values.
- <code>{page}</code> is provided automatically: the number of the page, starting from 1.
- Any other placeholder, like a CSRF token or a session id that only a first request reveals, needs a
  [metadata call](#metadata-call): its <code>parseMetadata</code> returns the value in
  <code>MetadataParams.placeholders</code>, under the name without braces.
- A placeholder without a value fails all pages of that company before any of them is requested. The company still ends
  FINISHED, with the error in its logs.
- Headers and cookies the metadata call returns are added to every page, overriding configured ones by key.

### Filters
Filters are created in the web app at <code>/filters</code>, after logging in. A filter has a title, an expression
(below) and the linked chats it notifies. A filter bound to no chat is a draft: it is kept, but matches nothing until
you bind it. Unlinking a chat leaves its filters in place, as drafts if that was their only chat. A chat gets a new
vacancy once, however many of your filters match it.

The filter structure should be as follows:

- <i>Company name,</i>: No formatting, just a simple keyword with <b>comma at the end</b> (comma is necessary for separate company keywords from mandatory keywords). This keyword <b>must match</b> company title; if not - the vacancy is not applicable. (Examples: Microsoft, Simple Company,)
- <i>(keyword|keyword)</i>: Binary choice. One of these keywords <b>must be</b> in the vacancy title; if not - the vacancy is not applicable. (Example: (developer|engineer))
- <i>keyword</i>: No formatting, just a simple keyword. This keyword <b>must be</b> in the vacancy title; if not, the vacancy is not applicable. (Example: java)
- <i>-keyword</i>: A minus (or hyphen) before the keyword. This keyword <b>must not</b> be in the vacancy title; if it is - the vacancy is not applicable. (Example: -lead -architect -qa)


Keywords can be combined. Examples:
- <code>Google, (developer|engineer) software -senior -lead -qa</code> - will find vacancies from company Google that contains developer or engineer, contains software, doesn't contain senior, lead, qa keywords
- <code>java -senior -lead -qa</code> - will find vacancies from all companies which contains java keyword and doesn't contain senior, lead and qa
- <code>Amazon,</code> - will find all vacancies from company Amazon

### Telegram bot
The bot only links chats and delivers notifications; filters are managed in the web app. It answers:
- <code>/start</code>: how to link the chat from your account page.
- <code>/link &lt;code&gt;</code>: links the chat with the one-time code from the account page. In a group, the bot
  deletes the message, since anyone who sends the code links their chat to its owner, so give it the right to
  delete messages.
- <code>/status</code>: which account the chat is linked to, and how many of its filters notify it.
- <code>/unlink</code>: stops notifications to the chat.
