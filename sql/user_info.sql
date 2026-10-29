create table tb_user_info
(
    id          bigint                                not null
        primary key,
    user_id     bigint                                not null,
    city        varchar(64) default ''::character varying,
    introduce   varchar(128),
    fans        integer     default 0,
    followee    integer     default 0,
    gender      integer     default 0,
    birthday    date,
    create_time timestamp   default CURRENT_TIMESTAMP not null,
    update_time timestamp   default CURRENT_TIMESTAMP not null
);

comment on table tb_user_info is '用户扩展信息表';

comment on column tb_user_info.id is '主键，雪花ID';

comment on column tb_user_info.user_id is '用户ID，唯一，指向 tb_user.id';

comment on column tb_user_info.city is '城市';

comment on column tb_user_info.introduce is '个人简介';

comment on column tb_user_info.fans is '粉丝数';

comment on column tb_user_info.followee is '关注数';

comment on column tb_user_info.gender is '性别：0男，1女';

comment on column tb_user_info.birthday is '生日';

comment on column tb_user_info.create_time is '创建时间';

comment on column tb_user_info.update_time is '更新时间，由触发器维护';

alter table tb_user_info
    owner to postgres;

create unique index uk_user_info_user_id
    on tb_user_info (user_id);

create trigger trg_tb_user_info_touch
    before update
    on tb_user_info
    for each row
    execute procedure touch_update_time();

