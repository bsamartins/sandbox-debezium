create table "user" (
    id   int GENERATED ALWAYS AS IDENTITY primary key,
    name varchar(256) not null
);

create table user_contact (
    id      int GENERATED ALWAYS AS IDENTITY primary key,
    user_id int not null,
    contact varchar(256) not null,

    constraint fk_user_contact foreign key (user_id) references "user"(id)
);