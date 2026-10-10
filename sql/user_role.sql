create table tb_user_role
(
    id          bigint                              not null
        primary key,
    user_id     bigint                              not null,
    role_id     bigint                              not null,
    deleted     integer   default 0                 not null,
    create_time timestamp default CURRENT_TIMESTAMP not null,
    update_time timestamp default CURRENT_TIMESTAMP not null,
    constraint uk_user_role
        unique (user_id, role_id, deleted)
);

comment on table tb_user_role is '用户角色关联表';

comment on column tb_user_role.id is '主键，雪花ID';

comment on column tb_user_role.user_id is '用户ID，指向 tb_user.id';

comment on column tb_user_role.role_id is '角色ID，指向 tb_role.id';

comment on column tb_user_role.deleted is '逻辑删除：0未删除，1已删除。唯一约束含本列，便于撤销后再次授予';

comment on column tb_user_role.create_time is '创建时间';

comment on column tb_user_role.update_time is '更新时间，由触发器维护';

alter table tb_user_role
    owner to postgres;

create index idx_user_role_role_id
    on tb_user_role (role_id);

create index idx_user_role_user_id
    on tb_user_role (user_id);

create trigger trg_tb_user_role_touch
    before update
    on tb_user_role
    for each row
    execute procedure touch_update_time();

