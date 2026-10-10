create table tb_role
(
    id          bigint                                 not null
        primary key,
    role_name   varchar(32)                            not null,
    role_code   varchar(32)                            not null,
    description varchar(128) default ''::character varying,
    create_time timestamp    default CURRENT_TIMESTAMP not null,
    update_time timestamp    default CURRENT_TIMESTAMP not null
);

comment on table tb_role is '角色表';

comment on column tb_role.id is '主键';

comment on column tb_role.role_name is '角色名称';

comment on column tb_role.role_code is '角色编码，唯一。如 NORMAL_USER、BAR_ADMIN、PLATFORM_ADMIN';

comment on column tb_role.description is '角色说明';

comment on column tb_role.create_time is '创建时间';

comment on column tb_role.update_time is '更新时间，由触发器维护';

alter table tb_role
    owner to postgres;

create unique index uk_role_code
    on tb_role (role_code);

create trigger trg_tb_role_touch
    before update
    on tb_role
    for each row
    execute procedure touch_update_time();

