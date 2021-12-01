create table "user" (
    id    int GENERATED ALWAYS AS IDENTITY primary key,
    email varchar(256) not null
)