db = db.getSiblingDB(process.env.MONGO_INITDB_DATABASE);

// Create the collection
db.createCollection('crawling-history', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['crawling_hash', 'crawled_vacancies', 'new_vacancies', 'outdated_vacancies', 'finished', 'finished_at', 'crawlers'],
      properties: {
        crawling_hash: {
          bsonType: 'string'
        },
        crawled_vacancies: {
          bsonType: 'int'
        },
        new_vacancies: {
          bsonType: 'int'
        },
        outdated_vacancies: {
          bsonType: 'int'
        },
        status: {
          enum: ['IDLE', 'RUNNING', 'STOPPING', 'FINISHED', 'STOPPED', 'FAILED']
        },
        finished: {
          bsonType: 'bool'
        },
        started_at: {
          bsonType: 'date'
        },
        finished_at: {
          bsonType: 'date'
        },
        crawlers: {
          bsonType: 'array',
          items: {
            bsonType: 'object',
            required: ['crawler_id', 'crawler_title', 'crawler_total_pages', 'crawled_vacancies', 'new_vacancies', 'outdated_vacancies', 'crawler_pages'],
            properties: {
              crawler_id: {
                bsonType: 'int'
              },
              crawler_title: {
                bsonType: 'string'
              },
              crawler_total_pages: {
                bsonType: 'int'
              },
              crawler_status: {
                enum: ['RUNNING', 'FINISHED', 'ERROR']
              },
              crawled_vacancies: {
                bsonType: 'int'
              },
              new_vacancies: {
                bsonType: 'int'
              },
              crawler_pages: {
                bsonType: 'array',
                items: {
                  bsonType: 'object',
                  required: ['page_id', 'page_status', 'page_crawled_vacancies', 'page_logs'],
                  properties: {
                    page_id: {
                      bsonType: 'int'
                    },
                    page_status: {
                      enum: ['STEP_PENDING', 'STEP_DONE', 'STEP_ERROR']
                    },
                    page_crawled_vacancies: {
                      bsonType: 'int'
                    },
                    page_logs: {
                      bsonType: 'array',
                      items: {
                        bsonType: 'object',
                        required: ['log_id', 'log_level', 'log_timestamp', 'log_message'],
                        properties: {
                          log_id: {
                            bsonType: 'int'
                          },
                          log_level: {
                            bsonType: 'string'
                          },
                          log_timestamp: {
                            bsonType: 'date'
                          },
                          log_message: {
                            bsonType: 'string'
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
});

// Create a view - crawlers keyed by title, pages keyed by id
db.createView('crawlers', 'crawling-history', [
  {
    $set: {
      crawlers: {
        $map: {
          input: '$crawlers',
          as: 'p',
          in: {
            k: '$$p.crawler_title',
            v: {
              crawler_total_pages: '$$p.crawler_total_pages',
              crawled_vacancies: '$$p.crawled_vacancies',
              new_vacancies: '$$p.new_vacancies',
              outdated_vacancies: '$$p.outdated_vacancies',
              crawler_pages: {
                $arrayToObject: {
                  $map: {
                    input: '$$p.crawler_pages',
                    as: 'pg',
                    in: {
                      k: { $toString: '$$pg.page_id' },
                      v: {
                        $mergeObjects: [
                          {
                            page_status: '$$pg.page_status',
                            page_crawled_vacancies: '$$pg.page_crawled_vacancies'
                          },
                          {
                            $cond: [
                              { $eq: ['$$pg.page_status', 'STEP_ERROR'] },
                              { page_logs: '$$pg.page_logs' },
                              {}
                            ]
                          }
                        ]
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  },
  { $sort: { finished_at: -1 } },
  {
    $project: {
      _id: 0,
      crawling_hash: 1,
      crawled_vacancies: 1,
      new_vacancies: 1,
      outdated_vacancies: 1,
      finished_at: {
        $dateToString: {
          format: '%d-%m-%Y %H:%M:%S',
          date: '$finished_at',
          timezone: 'Europe/Kyiv'
        }
      },
      crawlers: { $arrayToObject: '$crawlers' }
    }
  }
]);
