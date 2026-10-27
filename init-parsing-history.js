db = db.getSiblingDB(process.env.MONGO_INITDB_DATABASE);

// Create the collection
db.createCollection('parsing-history', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['parsing_hash', 'parsed_vacancies', 'new_vacancies', 'finished', 'parsers'],
      properties: {
        parsing_hash: {
          bsonType: 'string'
        },
        parsed_vacancies: {
          bsonType: 'int'
        },
        new_vacancies: {
          bsonType: 'int'
        },
        finished: {
          bsonType: 'bool'
        },
        finished_at: {
          bsonType: 'date'
        },
        parsers: {
          bsonType: 'array',
          items: {
            bsonType: 'object',
            required: ['parser_id', 'parser_title', 'parser_total_pages', 'parsed_vacancies', 'new_vacancies', 'outdated_vacancies', 'parser_pages'],
            properties: {
              parser_id: {
                bsonType: 'int'
              },
              parser_title: {
                bsonType: 'string'
              },
              parser_total_pages: {
                bsonType: 'int'
              },
              parsed_vacancies: {
                bsonType: 'int'
              },
              new_vacancies: {
                bsonType: 'int'
              },
              parser_pages: {
                bsonType: 'array',
                items: {
                  bsonType: 'object',
                  required: ['page_id', 'page_status', 'page_parsed_vacancies', 'page_logs'],
                  properties: {
                    page_id: {
                      bsonType: 'int'
                    },
                    page_status: {
                      enum: ['STEP_PENDING', 'STEP_DONE', 'STEP_ERROR']
                    },
                    page_parsed_vacancies: {
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

// Create indexes if needed
db.parsing_sessions.createIndex({ "parsing_hash": 1 }, { unique: true });
db.parsing_sessions.createIndex({ "parsers.parser_id": 1 });

// Create a view - parsers keyed by title, pages keyed by id
db.createView('parsers', 'parsing-history', [
  {
    $set: {
      parsers: {
        $map: {
          input: '$parsers',
          as: 'p',
          in: {
            k: '$$p.parser_title',
            v: {
              parser_total_pages: '$$p.parser_total_pages',
              parsed_vacancies: '$$p.parsed_vacancies',
              new_vacancies: '$$p.new_vacancies',
              outdated_vacancies: '$$p.outdated_vacancies',
              parser_pages: {
                $arrayToObject: {
                  $map: {
                    input: '$$p.parser_pages',
                    as: 'pg',
                    in: {
                      k: { $toString: '$$pg.page_id' },
                      v: {
                        $mergeObjects: [
                          {
                            page_status: '$$pg.page_status',
                            page_parsed_vacancies: '$$pg.page_parsed_vacancies'
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
      parsing_hash: 1,
      parsed_vacancies: 1,
      new_vacancies: 1,
      outdated_vacancies: 1,
      finished_at: {
        $dateToString: { format: '%m:%d:%H:%M:%S', date: '$finished_at' }
      },
      parsers: { $arrayToObject: '$parsers' }
    }
  }
]);
