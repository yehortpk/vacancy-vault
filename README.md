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
- [Architecture](#architecture)
- [Usage](#usage)
  - [Code Usage](#code-usage)
  - [Parser structure](#parser-structure)
    - [Abstraction](#abstraction)
  - [Scrapper structure](#scrapper-structure)
    - [Scrapper decorators](#scrapper-decorators)
  - [Database usage](#database-usage)
    - [Request parameters](#request-parameters)
      - [Dynamic request parameters](#dynamic-request-parameters)
- [Filters](#filters)
- [Telegram bot](#telegram-bot)
- [Frontend app](#frontend-app)

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
   # API token of the bot from step 2
   TG_BOT_API_KEY=
   # Username of the bot from step 2
   TG_BOT_USERNAME=
   ```

   **Optional**

   Every variable below falls back to the value shown, so add only the ones you want to change.

   Security:
   ```env
   # Key the router signs the users' login tokens with; no other service holds it. At least 32
   # bytes, either Base64 or raw text - generate one with `openssl rand -base64 32`. Changing it
   # invalidates every existing session
   JWT_SECRET=bDGvjQnfBnIiGi8eLnKJds3Oka82zHhLO83YIcIp8Bs=
   # Shared secret the bot presents to the router's /connector endpoints, the same for both
   # services - generate one with `openssl rand -hex 32`
   SERVICE_CONNECTOR_TOKEN=SERVICE_CONNECTOR_TOKEN
   ```
   These defaults are public, so on a server reachable from the internet generate your own.

   Databases:
   ```env
   # Credentials of the notifier PostgreSQL database, created as its superuser. The parser, the
   # dashboard and the router share the database, each in a schema of its own
   POSTGRES_USERNAME=notifier
   POSTGRES_PASSWORD=notifier
   # Root credentials of the parser-progress MongoDB database, where the parser keeps its runs
   MONGO_ROOT_USERNAME=progress
   MONGO_ROOT_PASS=progress
   ```
   The credentials are applied only when the database volumes are first created, so changing them
   later also means changing them inside the database. The database ports are published on the
   host, so pick your own passwords on a server reachable from the internet.

   Parser admin:
   ```env
   # The first admin of the parser's own panel, created by the parser on startup. The password
   # needs at least 8 characters. See Access levels below
   PARSER_ADMIN_USERNAME=admin
   PARSER_ADMIN_PASSWORD=PARSER_ADMIN_PASSWORD
   ```
   These defaults are public, so set your own password on a server reachable from the internet. Set
   both to empty values to create no admin account.

   Parser load:
   ```env
   # How many Chromium instances may render dynamic sites at once, or max
   PARSER_BROWSER_POOL_SIZE=2
   ```
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
There are 3 type of web apps that can be parsed by implementing the next abstract classes:
- <b>StaticSiteParser</b> - default web apps with a common page design and pagination.
- <b>DynamicSiteParser</b> - based on dynamic web apps, DOM structure of which is rendered in-time.
  Can't be parsed by default request-based parser, so we use <b>Playwright</b> library here. You need to implement <i>createDynamicElementQuerySelector</i> method for specify
  a query selector for the element that can only be available after the page rendering. Most widely used #root or #app sections
- <b>APISiteParser</b> - based on web apps that used API requests to retrieve information from remote server in-time and update it on the page. You may also override
  <i>getConnectionMethod</i> which by default is GET, but many sites use POST as well.

### Parser structure
#### Abstraction
![image](https://github.com/user-attachments/assets/54c5be98-6ab1-45d5-929b-71632f7efbb6)
Diagram above shows parsers structure. There is <code>SiteParser</code> interface with <code>parseVacancies</code> method. It takes Company object as a parameter. Company object is loaded from <code>company</code> table.
<code>SiteParserImpl</code> is abstract implementation of <code>SiteParser</code>. It implements <code>parseVacancies</code> method. For this purpose it required 3 additional methods to be implemented in specific
parsers that will extend this one. These methods are:
- <code>generatePageConnectionParams(pageID:int, company: Company)</code>. This method require from parsers to generate connection parameters necessary for connection to the parsing page. These parameters:
    - <code>pageURL</code>. Final url to the page
    - <code>data</code>. This is request data. In case of GET request transforms into query params (param1=val1&param2=val2). In case of POST request becomes request body. In case of Content-type=application/json in headers becomes json string in the request body.
    - <code>headers</code>. Request headers
    - <code>method</code>. Request method (e.g GET, POST, PUT, etc).
    - <code>proxy</code>. Proxy object that will be used for sending request through proxy server.
    - <code>timeout</code>. Timeout for page loading.
- <code>parsePage(pageConnectionParams: PageConnectionParams): Page</code>. This method require from parsers to create its own way to create <code>Page</code> object from parsing web page with <code>pageConnectionParams</code> parameters.
- <code>extractVacancyElementsFromPage(page: Document): List[Element]</code>. This method require logic from parser to
  extract all similar blocks with vacancies from <code>page</code>. <code>Page</code> is a Jsoup object, so extracting comes down
to create correct selector that extract all vacancy elements from the page.
- <code>generateVacancyFromElement(element: Element): [VacancyDTO]</code>. This method requires logic from parser to dynamically create new vacancy object
from every <code>vacancy</code> element. Its final point.

Additionally, you need to create new site parsers as Spring Beans with @Component and explicitly specify the 
bean name in the db (this will be used to associate this Java bean class with an object inside the database, which will be mentioned later).

### Scrapper structure
There are two types of page scrappers
- <code>StaticPageScrapper</code> - page scrapper that used <code>Jsoup</code> library to scrap pages that not rendering in time and not need JavaScript to create 
information on the page. So with simple GET/POST request scrapper can retrieve all necessary data.
- <code>DynamicPageScrapper</code> - page scrapper that used <code>Playwright</code> library to load pages rendered in time. It uses headless browser to
load the page and wait until page will be rendered. It requires implementing <code>createDynamicElementQuerySelector</code> method for speed up data load
process (the parser will not wait until full page will be loaded, but rather for the selector to be visible). <b>Warning!</b> This scrapper is resource intensive, 
so try to use it as a last possible solution in case of <code>StaticPageParser</code> not enough.

#### Scrapper decorators
You may implement <code>PageScrapperDecorator</code> for add additional logic to page scrapping. For example, there is <code>ProxyPoolPageScrapper</code>
that wrap all requests with pool of proxies that works in parallel (you could specify the pool size, default - 5 threads). It will help unnecessary webpage
loading with your own IP address. <b>Warning!</b> It would be better not to use it with <code>DynamicPageScrapper</code> 
unless you have enough memory in the system.

### Database usage
To add a new site for analysis, all you have to do is add the entity to the <i>company</i> table in the 
<i>parser</i> schema of the <i>notifier</i> database (<code>parser.company</code>). The schema and the table will be
created automatically after the first launch of the parser. Companies live only there: the dashboard and the router
learn a company's title and URL from the vacancies the parser publishes. You must fill out the following fields:
- company_id - id of the company
- bean_class - name of the @Bean in the code that corresponds this db entity;
- title - company title.
- parsing_enabled - enable/disable specific parser;
- main_page_url - URL to the site main page;
- vacancies_url - URL to the page with the vacancies in the site;
- api_vacancies_url - URL to the endpoint for API to retrieve vacancies (only for <i>APISiteParser</i>)

#### Request parameters
If you have request data that need to be sent with the request (e.g. GET request query data, or POST request body)
you may add this data in the <code>company_data</code> table of the same schema. 
Also, if you have special headers you want to send with every request, you may add them by creating new key-value pair in
<code>company_headers</code> table.

##### Dynamic request parameters
You may also add dynamic request parameters that will be different for every page in the parser, or may be generated 
only by pre-accessing the page - like CSRF tokens or SESSION_ID parameters. To add them, you need to add this parameter in {} brackets,
like key=page, val={page}. If you add these parameters in your <code>company_data</code> or <code>company_headers</code>
table, you need to implement <code>parseSiteMetadata</code> method in your parser. Error will be thrown in case of the method not be implemented.


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

### Frontend app
Docker compose starts both web apps. To start the users' app by hand you have to run, in <code>frontend/</code>:

```
npm install
npx ng serve
```

You can access it at the following URL: http://localhost:4200. In this application, you can view and search existing
vacancies, narrow them to remote ones or to those with a salary, and manage your account and filters.

The parser's admin panel is a separate app in <code>parser/admin-ui/</code>, served at http://localhost:4201. After
logging in (see [Access levels](#access-levels)) you can start and stop parsing there, monitor the parsing progress
in real time, and view statistics at the end. Its dev server forwards the API calls to the parser, so it is meant to
run next to the parser, as docker compose runs it.