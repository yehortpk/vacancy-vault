db = db.getSiblingDB(process.env.MONGO_INITDB_DATABASE);

// Create the collection
db.createCollection('parsing-history', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['parsing_hash', 'parsed_vacancies', 'new_vacancies', 'outdated_vacancies', 'finished', 'finished_at', 'parsers'],
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
        outdated_vacancies: {
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
