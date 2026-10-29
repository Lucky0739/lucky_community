-- 把已有的「关注用户」表改成「关注贴吧」。
-- 旧列 follow_user_id 里如果存的是用户 ID，改名后这些行不再表示贴吧，需要自行清理。
--
--   psql -h127.0.0.1 -p5432 -Upostgres -d community -f sql/follow_to_bar.sql

ALTER TABLE community.tb_follow RENAME COLUMN follow_user_id TO bar_id;
ALTER INDEX community.uk_follow_user_followee RENAME TO uk_follow_user_bar;
ALTER INDEX community.idx_follow_follow_user_id_id RENAME TO idx_follow_bar_id;
COMMENT ON TABLE community.tb_follow IS '用户关注贴吧表';
COMMENT ON COLUMN community.tb_follow.bar_id IS '被关注贴吧ID，指向 tb_bar.id';
