create table info_channels
(
    channel        unsigned big int not null primary key,
    location       text             not null,
    force_recreate boolean          not null,
    hash           text
);