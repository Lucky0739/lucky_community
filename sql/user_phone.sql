create table tb_user_phone
(
    id          bigint                              not null
        primary key,
    user_id     bigint                              not null,
    phone       varchar(512)                        not null,
    create_time timestamp default CURRENT_TIMESTAMP not null,
    update_time timestamp default CURRENT_TIMESTAMP not null
);

comment on table tb_user_phone is '用户手机表';

comment on column tb_user_phone.id is '主键，雪花ID';

comment on column tb_user_phone.user_id is '用户ID';

comment on column tb_user_phone.phone is '手机号审计副本。用户逻辑删除后 tb_user.phone 会置NULL，这里仍保留原号码';

comment on column tb_user_phone.create_time is '创建时间';

comment on column tb_user_phone.update_time is '更新时间，由触发器维护';

alter table tb_user_phone
    owner to postgres;

create index idx_user_phone_phone
    on tb_user_phone (phone);

create trigger trg_tb_user_phone_touch
    before update
    on tb_user_phone
    for each row
execute procedure touch_update_time();

