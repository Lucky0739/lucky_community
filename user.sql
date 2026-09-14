CREATE TABLE `tb_user_info` (
                                `id` bigint unsigned NOT NULL COMMENT '主键',
                                `user_id` bigint unsigned NOT NULL COMMENT '用户id',
                                `city` varchar(64) DEFAULT '' COMMENT '城市名称',
                                `introduce` varchar(128) DEFAULT NULL COMMENT '个人介绍，不要超过128个字符',
                                `fans` int unsigned DEFAULT '0' COMMENT '粉丝数量',
                                `followee` int unsigned DEFAULT '0' COMMENT '关注的人的数量',
                                `gender` tinyint unsigned DEFAULT '0' COMMENT '性别，0：男，1：女',
                                `birthday` date DEFAULT NULL COMMENT '生日',
                                `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='用户扩展信息表';
CREATE TABLE `tb_user` (
                           `id` bigint unsigned NOT NULL COMMENT '主键',
                           `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '手机号码',
                           `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '密码，加密存储',
                           `nick_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '昵称，默认是用户id',
                           `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '人物头像',
                           `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`) USING BTREE,
                           UNIQUE KEY `uniqe_key_phone` (`phone`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT;

CREATE TABLE `tb_user_phone` (
                                 `id` bigint NOT NULL COMMENT '主键id',
                                 `user_id` bigint NOT NULL COMMENT '用户id',
                                 `phone` varchar(512) NOT NULL COMMENT '手机号',
                                 `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 KEY `phone_idx` (`phone`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户手机表';

CREATE TABLE `tb_role` (
                           `id` bigint unsigned NOT NULL COMMENT '角色主键ID',
                           `role_name` varchar(32) NOT NULL COMMENT '角色名称',
                           `role_code` varchar(32) NOT NULL COMMENT '角色编码(程序判断使用)',
                           `description` varchar(128) DEFAULT '' COMMENT '角色说明',
                           `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`) USING BTREE,
                           UNIQUE KEY `uk_role_code` (`role_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='角色表';

CREATE TABLE `tb_user_role` (
                                `id` bigint unsigned NOT NULL COMMENT '主键ID',
                                `user_id` bigint unsigned NOT NULL COMMENT '用户ID，关联tb_user.id',
                                `role_id` bigint unsigned NOT NULL COMMENT '角色ID，关联tb_role.id',
                                `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                UNIQUE KEY `uk_user_role` (`user_id`,`role_id`) USING BTREE,
                                KEY `idx_user_id` (`user_id`) USING BTREE,
                                KEY `idx_role_id` (`role_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='用户角色关联表';

-- 贴吧管理员关联表（仅标记哪些用户是哪个吧的吧务）
CREATE TABLE `tb_bar_manager` (
                                  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                                  `bar_id` bigint unsigned NOT NULL COMMENT '贴吧ID',
                                  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
                                  `grant_user_id` bigint unsigned NOT NULL COMMENT '任命人user_id，只能是平台管理员',
                                  `grant_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任命时间',
                                  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1生效，0撤销',
                                  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`) USING BTREE,
                                  UNIQUE KEY `uk_bar_user` (`bar_id`,`user_id`) USING BTREE COMMENT '同一用户在同一个贴吧最多一条吧务身份',
                                  KEY `idx_bar_id` (`bar_id`),
                                  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='贴吧吧务任命表';


-- 初始化角色数据
INSERT INTO `tb_role` (`id`, `role_name`, `role_code`, `description`) VALUES
                                                                          (1, '普通吧友', 'NORMAL_USER', '用户注册后的默认身份'),
                                                                          (2, '本吧吧务', 'BAR_ADMIN', '由大吧/平台管理员任命'),
                                                                          (3, '平台官方管理员', 'PLATFORM_ADMIN', '系统后台新增，允许多个');

-- 1. 插入平台管理员用户 tb_user
INSERT INTO tb_user (id, phone, password, nick_name, icon)
VALUES (1, '13800138000', '$2a$10$xxxx', '平台管理员', '');

-- 2. 插入用户扩展信息 tb_user_info
INSERT INTO tb_user_info (id, user_id, city, introduce, fans, followee, gender, birthday)
VALUES (1, 1, '广州', '平台后台管理员账号', 0, 0, 0, NULL);

-- 3. 绑定角色：PLATFORM_ADMIN（角色id=3）
INSERT INTO tb_user_role (id, user_id, role_id)
VALUES (1, 1, 3);
