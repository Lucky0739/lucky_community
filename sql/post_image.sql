create table tb_post_image
(
    id          bigint                              not null
        primary key,
    post_id     bigint                              not null,
    url         varchar(512)                        not null,
    object_name varchar(255)                        not null,
    sort_no     integer   default 0                 not null,
    deleted     integer   default 0                 not null,
    create_time timestamp default CURRENT_TIMESTAMP not null
);

comment on table tb_post_image is '帖子图片表';

comment on column tb_post_image.id is '主键，雪花ID';

comment on column tb_post_image.post_id is '所属帖子ID';

comment on column tb_post_image.url is '图片访问URL';

comment on column tb_post_image.object_name is 'MinIO对象名，用于孤儿对象对账；同一对象可被多行引用';

comment on column tb_post_image.sort_no is '展示顺序，从0开始；第一张即封面';

comment on column tb_post_image.deleted is '逻辑删除：0未删除，1已删除。删图留行，便于按 object_name 对账';

comment on column tb_post_image.create_time is '创建时间';

alter table tb_post_image
    owner to postgres;

create index idx_post_image_post_id_sort
    on tb_post_image (post_id, sort_no);

