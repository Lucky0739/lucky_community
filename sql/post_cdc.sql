-- 帖子缓存 CDC：PostgreSQL WAL 逻辑解码（MySQL binlog 的对应物）
--
-- 应用里的 Debezium 也会按需自动创建同名 publication。已经在跑的库可以手工执行本文件。
-- 前置：postgresql.conf 里 wal_level=logical，并重启。本地 docker-compose 已带上这些参数。
--
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/post_cdc.sql

CREATE PUBLICATION IF NOT EXISTS lucky_post_cache
    FOR TABLE community.tb_post, community.tb_post_image;
