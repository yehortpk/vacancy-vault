# VacancyVault

[![Static Badge](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Static Badge](https://img.shields.io/badge/SpringBoot-3-green.svg)](https://docs.spring.io/spring-boot/docs/3.1.5/reference/html/)
[![Static Badge](https://img.shields.io/badge/Angular-18-red.svg)](https://v18.angular.dev/overview)

## Description
This project aims to make the process of job searching easier by automating the collection of vacancies from various company websites.
With this system, users can effortlessly stay updated on job openings without the hassle of manually scouring multiple websites.
Our project includes a robust library that allows users to create custom parsers for vacancy sites of their choice.
This flexibility ensures compatibility with a wide range of company websites, catering to diverse job seekers' needs.
Also, users can subscribe to specific companies of interest, enabling the parser to automatically detect and notify them of new job postings.

Available via Telegram Bot: https://t.me/vacancy_notification_bot

## Table of Contents
- [Installation](#installation)
  - [Ports](#ports)
  - [Access levels](#access-levels)
- [Architecture](#architecture)
- [Usage](#usage)
  - [Code Usage](#code-usage)
  - [Parser structure](#parser-structure)
    - [Vacancy pages](#vacancy-pages)
  - [Scraper structure](#scraper-structure)
  - [Class diagram](#class-diagram)
  - [Database usage](#database-usage)
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

   **Optional**

   Every variable below falls back to the value shown, so add only the ones you want to change.

   Security:
   ```env
   JWT_SECRET=bDGvjQnfBnIiGi8eLnKJds3Oka82zHhLO83YIcIp8Bs=
   SERVICE_CONNECTOR_TOKEN=SERVICE_CONNECTOR_TOKEN
   ```
   - <code>JWT_SECRET</code> - key the router signs the users' login tokens with; no other service holds it.
     At least 32 bytes, either Base64 or raw text - generate one with <code>openssl rand -base64 32</code>.
     Changing it invalidates every existing session.
   - <code>SERVICE_CONNECTOR_TOKEN</code> - shared secret the bot presents to the router's <code>/connector</code>
     endpoints, the same for both services - generate one with <code>openssl rand -hex 32</code>.

   These defaults are public, so on a server reachable from the internet generate your own.

   Databases:
   ```env
   POSTGRES_USERNAME=notifier
   POSTGRES_PASSWORD=notifier
   MONGO_ROOT_USERNAME=progress
   MONGO_ROOT_PASS=progress
   ```
   - <code>POSTGRES_USERNAME</code>, <code>POSTGRES_PASSWORD</code> - credentials of the notifier PostgreSQL
     database, created as its superuser. The parser, the dashboard and the router share the database, each in a
     schema of its own.
   - <code>MONGO_ROOT_USERNAME</code>, <code>MONGO_ROOT_PASS</code> - root credentials of the parser-progress
     MongoDB database, where the parser keeps its runs.

   The credentials are applied only when the database volumes are first created, so changing them
   later also means changing them inside the database. The database ports are published on the
   host, so pick your own passwords on a server reachable from the internet.

   Parser admin:
   ```env
   PARSER_ADMIN_USERNAME=admin
   PARSER_ADMIN_PASSWORD=PARSER_ADMIN_PASSWORD
   ```
   The first admin of the parser's own panel, created by the parser on startup. The password needs at least 8
   characters. See [Access levels](#access-levels).

   These defaults are public, so set your own password on a server reachable from the internet. Set
   both to empty values to create no admin account.

   Parser load:
   ```env
   PARSER_BROWSER_POOL_SIZE=2
   ```
   <code>PARSER_BROWSER_POOL_SIZE</code> is how many Chromium instances may render dynamic sites at once, or
   <code>max</code>.

   Chromium dominates the parser's memory, at roughly 400MB per instance, so this is the main
   knob. Raise it to parse dynamic sites faster, or set <code>max</code> for as many as the host
   can afford: half of its CPUs, but no more than fit into its memory once about 3GB is left to
   the rest of the stack, and never less than <code>1</code>. The parser logs the size it picked
   at startup.

   The parser runs without CPU or memory limits by default. To cap it, for example on a server
   shared with other apps, add a <code>docker-compose.override.yaml</code> next to
   <code>docker-compose.yaml</code>; docker compose picks it up on its own:
   ```yaml
   services:
     parser:
       mem_limit: 2g # hard ceiling, the parser is killed when it runs out
       cpus: 2       # soft ceiling, the parser only slows down
   ```
   With a memory limit, <code>max</code> sizes the pool from the limit rather than the host, still
   leaving the 3GB reserve, so it picks <code>1</code> below about 4GB. Set a number instead if the
   limit allows more: the parser itself takes about 1GB, each instance about 400MB more.

5. Create and run with docker
```
docker compose up --build
```
The parser does not parse on its own: every run is started from its admin panel at http://localhost:4201 (see
[Access levels](#access-levels)).

### Ports
Docker compose publishes these ports on the host:

| Port | Service |
|---|---|
| 4200 | Vacancy dashboard web app (Angular dev server, reloads on change) |
| 4201 | Parser admin panel (Angular dev server, reloads on change) |
| 8081 | Router API |
| 8082 | Parser API |
| 8083 | Dashboard API |
| 5005 | Parser remote debugging (JDWP) |
| 5006 | Router remote debugging (JDWP) |
| 5007 | Telegram bot remote debugging (JDWP) |
| 5008 | Dashboard remote debugging (JDWP) |
| 5432 | PostgreSQL |
| 27017 | MongoDB |
| 29092 | Kafka, for clients on the host |

### Access levels
- <b>Guests</b> (not logged in) browse the vacancy dashboard at http://localhost:4200.
- <b>Users</b> register at <code>/register</code> and get their own account room at <code>/account</code>: linking a
  messenger with a one-time code (for Telegram, send <code>/link &lt;code&gt;</code> to the bot) and the list of
  linked chats. At <code>/filters</code> they write their [filters](#filters) and choose which linked chats each one
  notifies. Every account is the same: there are no roles.
- <b>The operator</b> logs in to the parser's own admin panel at http://localhost:4201, to start a parse and watch
  its progress. This is a separate login, unrelated to the user accounts: a user's session opens nothing in the
  panel, and the operator needs no user account.

The operator's account comes from <code>PARSER_ADMIN_USERNAME</code> and <code>PARSER_ADMIN_PASSWORD</code> (see their
defaults in the optional env variables): on startup the parser creates it if that username does not exist yet, and
you log in with exactly those credentials. It only ever creates the account, never changes one, so changing
<code>PARSER_ADMIN_PASSWORD</code> later does not change the stored password. There is no registration: any further
admin is a row in the <code>parser.admin</code> table, with a BCrypt hash as its password.

## Architecture
Three services, each owning its own data and sharing nothing but Kafka topics:

- <b>parser</b> crawls the company sites and publishes what it finds. It keeps the companies, its memory of the
  vacancies it has already published, the history of its runs (MongoDB) and its own admins, and it serves its own
  admin panel. No other service calls it.
- <b>dashboard</b> stores the published vacancies and serves the public vacancy listing. It has no users and no
  authentication.
- <b>router</b> holds the accounts: registration and login, filters, linked chats, and matching every new vacancy
  against the filters to notify the chats they are bound to. It stores no vacancies.

| Topic | Producer | Consumers | Message |
|---|---|---|---|
| <code>vacancies</code> | parser | dashboard, router | a new vacancy with its company (id, title, URL), or the link of a vacancy that is gone for good; keyed by the link |
| <code>telegram-notifier</code> | router | telegram-bot | one notification for one chat |

A vacancy that disappears from its site is kept for a week, in case it comes back; only then does the parser drop it
and publish its removal to <code>vacancies</code>. Both kinds share one topic keyed by the link, so a vacancy that is
removed and then listed again reaches every consumer in that order; the kind travels in the <code>__TypeId__</code>
header, and the router skips removals. Every consumer skips a link it has already seen, so a
redelivered or republished vacancy is stored once and notified about once.

The services share one PostgreSQL database, <code>notifier</code>, each in its own schema (<code>parser</code>,
<code>dashboard</code>, <code>router</code>) that it creates and migrates itself. No service reads another's schema.
The only REST call between services is the Telegram bot reporting linked chats to the router.

## Usage
The app needs the client to create parsing information for both sides - in the code itself and in the database. Let's look at the features of the code.

### Code usage
Every company is crawled by a Spring bean that extends the abstract class <code>DefaultCompanyCrawler</code>. The base
class runs the whole fetch flow, so a company crawler only describes its site. It implements two methods:
- <code>configureFetchSettings(company, pageId)</code> - how the page is requested, as a <code>FetchSettings</code>.
  The crawler always sets <code>pageURL</code>: it is never taken from the company's config. Leave data, headers or
  cookies unset to take them from the company's config. A crawler that reads the company's <code>vacancies_url</code>
  as is extends <code>VacanciesUrlCompanyCrawler</code> (in the companies' <code>common</code> package) instead of
  implementing this method. A crawler that reads the site's API sets <code>pageURL(company.getApiVacanciesURL())</code>,
  and <code>connectionMethod(POST)</code> when the API needs it.
- <code>parsePreviewVacancies(page, company)</code> - turns a fetched page into vacancies, as the page itself describes them.
  Read HTML with <code>Jsoup.parse(page.getBody())</code> and JSON with Gson.

It may also override three hooks:
- <code>configurePageScraper(pageId)</code> - the scraper the page is fetched with. Defaults to <code>new StaticPageScraper()</code>.
  Return <code>new DynamicPageScraper(browserPool, readySelector)</code> for a site whose vacancies a browser has to render
  (inject the <code>BrowserPool</code> bean into the crawler). See [Scraper structure](#scraper-structure) for choosing one.
- <code>configureMetadataCrawler()</code> - the call made before the pages, to learn the page count or values the requests
  need. Defaults to <code>NoCallMetadataCrawler</code>: no request, one page. See [Dynamic request parameters](#dynamic-request-parameters).
- <code>configureVacancyCrawler()</code> - the call made for every vacancy of a parsed page, to read the vacancy's own page
  (e.g. its description). Defaults to <code>NoCallVacancyCrawler</code>: no request, the vacancy stays as its page described it.
  See [Vacancy pages](#vacancy-pages).

The delay between pages defaults to 0, so every page of a company is sent at once. A site that needs pacing sets it per page
in <code>configureFetchSettings</code>, e.g. <code>delay(Duration.ofSeconds(pageId - 1))</code> to start page N after N-1 seconds.

```java
@Component("acme-company")
public class AcmeCrawler extends DefaultCompanyCrawler {
    @Override
    protected FetchSettings configureFetchSettings(CompanyDTO company, int pageId) {
        return FetchSettings.builder().build();
    }

    @Override
    protected List<VacancyDTO> parsePreviewVacancies(PageScraperResponse page, CompanyDTO company) {
        return Jsoup.parse(page.getBody()).select("a.vacancy").stream()
                .map(link -> VacancyDTO.builder().title(link.text()).link(link.attr("href")).build())
                .toList();
    }
}
```

The bean name (<code>acme-company</code> here) is the company's <code>bean_class</code> in the database, see [Database usage](#database-usage).
Crawlers hold no state of a run, so one bean may serve several companies.

### Parser structure
![Parser structure](docs/parser-structure.svg)

<code>ParserRunnerService</code> receives every crawler bean as a map by bean name and looks up each company's crawler
once. It calls <code>scrapeCompanyVacancies(company)</code>, which <code>DefaultCompanyCrawler</code> implements as:
1. <b>Metadata call.</b> The crawler's <code>CompanyMetadataCrawler</code> describes its request
   (<code>configureFetchSettings(company)</code>, <code>configurePageScraper()</code>); the base class makes it, and the metadata
   crawler reads the <code>MetadataParams</code> from the response (<code>parseMetadata</code>): the page count, placeholder values, and
   headers and cookies to add to every page.
2. <b>Every page's settings</b>, built before any page is sent: <code>configureFetchSettings(company, pageId)</code>, unset fields
   filled from the company, the metadata headers and cookies added, and the placeholders filled.
3. <b>Every page</b> waits out its <code>delay</code>, then is fetched on a virtual thread of its own with
   <code>configurePageScraper(pageId)</code>. A stop request ends the wait at once.

The runner then parses each fetched page with <code>parseVacancies(page, company)</code>, which <code>DefaultCompanyCrawler</code>
implements (it is final) as:
1. <b>Preview vacancies.</b> <code>parsePreviewVacancies(page, company)</code> turns the page into vacancies.
2. <b>Vacancy calls.</b> Every vacancy gets a virtual thread of its own: the crawler's <code>VacancyCrawler</code> describes the
   request (<code>configureFetchSettings(previewVacancy, company, vacancyOrderOnPage)</code>, <code>configurePageScraper()</code>), the
   call waits out its <code>delay</code>, is made, and <code>parseVacancy(previewVacancy, response)</code> returns the vacancy that
   replaces the preview one. All calls are waited for before the page's vacancies are returned.

It then publishes the new vacancies and, once every page was parsed, marks the vanished ones outdated.

A company crawler that needs a metadata call implements <code>CompanyMetadataCrawler</code> in a private nested class
(e.g. <code>AcmeMetadataCrawler</code>) and returns a new instance of it from <code>configureMetadataCrawler()</code>. Its
<code>configureFetchSettings</code> describes the request, used as returned (nothing is filled from the company), and its
<code>configurePageScraper</code> creates the scraper with <code>new</code>.

A metadata call that fails, or whose response can't be parsed, marks the company ERROR.

#### Vacancy pages
<b>Warning!</b> A vacancy call is made for <b>every vacancy of every page on every run</b>, not only for the new ones. It
multiplies the requests sent to the company's site, which can get the parser rate-limited or banned there. Use it with
consideration, and space the calls out with a delay.

A company crawler that needs it implements <code>VacancyCrawler</code> in a private nested class and returns a new instance
of it from <code>configureVacancyCrawler()</code>:

```java
private static final Duration VACANCY_DELAY = Duration.ofSeconds(2);

@Override
protected VacancyCrawler configureVacancyCrawler() {
    return new AcmeVacancyCrawler();
}

private static class AcmeVacancyCrawler implements VacancyCrawler {
    @Override
    public FetchSettings configureFetchSettings(VacancyDTO previewVacancy, CompanyDTO company, int vacancyOrderOnPage) {
        return FetchSettings.builder()
                .pageURL(previewVacancy.getLink())
                .delay(VACANCY_DELAY.multipliedBy(vacancyOrderOnPage - 1L))
                .build();
    }

    @Override
    public PageScraper configurePageScraper() {
        return new StaticPageScraper();
    }

    @Override
    public VacancyDTO parseVacancy(VacancyDTO previewVacancy, PageScraperResponse response) {
        return previewVacancy.toBuilder()
                .description(Jsoup.parse(response.getBody()).selectFirst(".job-description").text())
                .build();
    }
}
```

- The settings are used as returned: nothing is filled from the vacancy or the company.
- <code>vacancyOrderOnPage</code> is the vacancy's place on its page, starting from 1. The delay counts from the moment the page's
  vacancies are scheduled, so the example sends one call every 2 seconds per page. Pages are parsed in parallel, so the
  calls of different pages can overlap.
- The returned vacancy replaces the preview one. Start from <code>previewVacancy.toBuilder()</code> and set only what the page adds,
  and keep the <code>link</code>: it is the vacancy's identity, and a different link makes it another vacancy.
- A call that fails (<code>VacancyFetchException</code>), can't be parsed or returns <code>null</code> (<code>VacancyParseException</code>), or is
  skipped by a stop request keeps the preview vacancy and is logged on its page. The page itself doesn't fail.

### Scraper structure
A <code>PageScraper</code> fetches one page from a <code>FetchSettings</code> and returns a <code>PageScraperResponse</code>
(the page's id and URL, the response headers and cookies, and the body). It does I/O only: no waiting and no retries.
Both scrapers are plain classes created with <code>new</code>:
- <code>StaticPageScraper</code> - the default. Based on Java's <code>HttpClient</code>, for pages whose vacancies are in the HTML or the
  API response itself. Sends a GET or POST, follows redirects unless told not to, and decodes gzip and the response charset.
- <code>DynamicPageScraper(browserPool, readySelector)</code> - for pages a browser has to render. Uses <b>Playwright</b>: it borrows a
  headless Chromium from the <code>BrowserPool</code>, opens the page and waits until <code>readySelector</code> is on it, rather than for
  the whole page. <b>Warning!</b> It is resource-intensive, so use it only when <code>StaticPageScraper</code> is not enough.

<code>data</code> is the query string of a GET and the form body of a POST. If the request's <code>Content-Type</code> header is
<code>application/json</code>, a POST sends <code>data</code> as a JSON object instead, with every value as a string.

### Class diagram
The detailed view of the same structure, as a UML 2 class diagram: attributes, operations, visibility, defaults and thrown
exceptions, including the data classes (<code>FetchSettings</code>, <code>MetadataParams</code>,
<code>PageScraperResponse</code>, <code>VacancyDTO</code>).

![Parser class diagram](docs/parser-class-diagram.svg)

### Database usage
To add a new site for analysis, all you have to do is add the entity to the <i>company</i> table in the 
<i>parser</i> schema of the <i>notifier</i> database (<code>parser.company</code>). The schema and the table will be
created automatically after the first launch of the parser. Companies live only there: the dashboard and the router
learn a company's title and URL from the vacancies the parser publishes. You must fill out the following fields:
- <code>company_id</code> - id of the company
- <code>bean_class</code> - name of the @Bean in the code that corresponds this db entity;
- <code>title</code> - company title.
- <code>parsing_enabled</code> - enable/disable specific parser;
- <code>main_page_url</code> - URL to the site main page;
- <code>vacancies_url</code> - URL to the page with the vacancies in the site;
- <code>api_vacancies_url</code> - URL to the endpoint for API to retrieve vacancies; used when the crawler sets it as <code>pageURL</code>

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
  <code>CompanyMetadataCrawler</code>: the crawler returns it from <code>configureMetadataCrawler()</code>, and its
  <code>parseMetadata</code> returns the value in <code>MetadataParams.placeholders</code>, under the name without braces.
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
- Google, (developer|engineer) software -senior -lead -qa; will find vacancies from company Google that contains developer or engineer, contains software, doesn't contain senior, lead, qa keywords
- java -senior -lead -qa; will find vacancies from all companies which contains java keyword and doesn't contain senior, lead and qa
- Amazon,; will find all vacancies from company Amazon

### Telegram bot
The bot only links chats and delivers notifications; filters are managed in the web app. It answers:
- <code>/start</code>: how to link the chat from your account page.
- <code>/link &lt;code&gt;</code>: links the chat with the one-time code from the account page. In a group, the bot
  deletes the message, since anyone who sends the code links their chat to its owner, so give it the right to
  delete messages.
- <code>/status</code>: which account the chat is linked to, and how many of its filters notify it.
- <code>/unlink</code>: stops notifications to the chat.
