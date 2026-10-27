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
- [Frontend app](#frontend-app)

## Installation
1. Create a new Telegram bot through <a href="https://t.me/BotFather">Bot Father</a>;
2. Extract API Token and Username of the bot;
3. Clone the repository:
```
git clone https://github.com/yehortpk/vacancy-vault.git
```
4. Create .env.local environment file with the next variables:
   - POSTGRES_USERNAME - username that will be used to access <code>notifier</code> db;
   - POSTGRES_PASSWORD - password that will be used to access <code>notifier</code> db;
   - TG_BOT_API_KEY - API key from your bot extracted on previous step;
   - TG_BOT_USERNAME - username of your bot extracted on previous step;
   - MONGO_ROOT_USERNAME - username that will be used to access <code>parser-progress</code> db;
   - MONGO_ROOT_PASS - password that will be used to access <code>parser-progress</code> db;
   - JWT_SECRET - key used to sign the login tokens. At least 32 bytes, either Base64 or raw
     text - generate one with <code>openssl rand -base64 32</code>. Changing it invalidates
     every existing session;
   - SERVICE_CONNECTOR_TOKEN - shared secret the bot presents to the router's
     <code>/connector</code> endpoints. Must be identical for both services - generate one
     with <code>openssl rand -hex 32</code>.

5. Create and run with docker
```
docker compose --env-file=.env.local up --build
```

### Access levels
- <b>Guests</b> (not logged in) browse the vacancy dashboard.
- <b>Users</b> register at <code>/register</code> and get their own account room at <code>/account</code>: the API key
  for linking Telegram and the list of linked chats.
- <b>Admins</b> also get the parser room at <code>/parser</code>, to start a parse and watch its progress.

Every new account is a user. There is no endpoint that grants the admin role, so promote an account directly in
the <code>notifier</code> PostgreSQL database:
```
UPDATE app_user SET role = 'ADMIN' WHERE email = 'you@example.com';
```
The role is read into the login token, so it takes effect after the next login, or within 15 minutes when the
token refreshes.

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
To add a new site for analysis, all you have to do is add the entity to the <i>company</i> table inside the 
<i>notifier</i> database. The database and table <i>company</i> will be created automatically after the first launch of
the application. You must fill out the following fields:
- company_id - id of the company
- bean_class - name of the @Bean in the code that corresponds this db entity;
- title - company title.
- parsing_enabled - enable/disable specific parser;
- main_page_url - URL to the site main page;
- vacancies_url - URL to the page with the vacancies in the site;
- api_vacancies_url - URL to the endpoint for API to retrieve vacancies (only for <i>APISiteParser</i>)

#### Request parameters
If you have request data that need to be sent with the request (e.g. GET request query data, or POST request body)
you may add this data in the <code>company_data</code> table. 
Also, if you have special headers you want to send with every request, you may add them by creating new key-value pair in
<code>company_headers</code> table.

##### Dynamic request parameters
You may also add dynamic request parameters that will be different for every page in the parser, or may be generated 
only by pre-accessing the page - like CSRF tokens or SESSION_ID parameters. To add them, you need to add this parameter in {} brackets,
like key=page, val={page}. If you add these parameters in your <code>company_data</code> or <code>company_headers</code>
table, you need to implement <code>parseSiteMetadata</code> method in your parser. Error will be thrown in case of the method not be implemented.


### Filters
You can create specific filters that will be used for new vacancy notifications if they match the filter. The filter structure should be as follows:

- <i>Company name,</i>: No formatting, just a simple keyword with <b>comma at the end</b> (comma is necessary for separate company keywords from mandatory keywords). This keyword <b>must match</b> company title; if not - the vacancy is not applicable. (Examples: Microsoft, Simple Company,)
- <i>(keyword|keyword)</i>: Binary choice. One of these keywords <b>must be</b> in the vacancy title; if not - the vacancy is not applicable. (Example: (developer|engineer))
- <i>keyword</i>: No formatting, just a simple keyword. This keyword <b>must be</b> in the vacancy title; if not, the vacancy is not applicable. (Example: java)
- <i>-keyword</i>: A minus (or hyphen) before the keyword. This keyword <b>must not</b> be in the vacancy title; if it is - the vacancy is not applicable. (Example: -lead -architect -qa)


Keywords can be combined. Examples: 
- Google, (developer|engineer) software -senior -lead -qa; will find vacancies from company Google that contains developer or engineer, contains software, doesn't contain senior, lead, qa keywords
- java -senior -lead -qa; will find vacancies from all companies which contains java keyword and doesn't contain senior, lead and qa
- Amazon,; will find all vacancies from company Amazon

Also, you can view all stored vacancies filtered with chosen filter.

### Frontend app
To start frontend app you have to run

```
npm install
npx ng serve
```

You can access the frontend representation of the bot at the following URL: http://localhost:4200. In this application, you can view, search, sort, and filter existing vacancies.
Additionally, you can start parsing by accessing the /parser endpoint (or by clicking "Parser" on the main sidebar). There, you can monitor the parsing progress in real time, and view statistics at the end.