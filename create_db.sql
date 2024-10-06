create table uuidv4_table (
  id CHAR(36) PRIMARY KEY,
  text varchar(40),
  createdate TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_uuidv4_table_createdate ON uuidv4_table(createdate);

create table uuidv7_table (
  id CHAR(36) PRIMARY KEY,
  text varchar(40),
  createdate TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_uuidv7_table_createdate ON uuidv7_table(createdate);


