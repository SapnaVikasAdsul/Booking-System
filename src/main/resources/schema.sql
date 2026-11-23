-- A shared row makes the slot duplicate check and insert one serialized operation.
create table if not exists slot_creation_lock (
    id integer primary key
);

merge into slot_creation_lock (id) key (id) values (1);
