CREATE TABLE `tb_user_info` (
                                `id` bigint unsigned NOT NULL COMMENT '主键',
                                `user_id` bigint unsigned NOT NULL COMMENT '用户id',
                                `city` varchar(64) DEFAULT '' COMMENT '城市名称',
                                `introduce` varchar(128) DEFAULT NULL COMMENT '个人介绍，不要超过128个字符',
                                `fans` int unsigned DEFAULT '0' COMMENT '粉丝数量',
                                `followee` int unsigned DEFAULT '0' COMMENT '关注的人的数量',
                                `gender` tinyint unsigned DEFAULT '0' COMMENT '性别，0：男，1：女',
                                `birthday` date DEFAULT NULL COMMENT '生日',
                                `credits` int unsigned DEFAULT '0' COMMENT '积分',
                                `level` tinyint unsigned DEFAULT '0' COMMENT '会员级别，0~9级,0代表未开通会员',
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

-- 初始化角色数据
INSERT INTO `tb_role` (`id`, `role_name`, `role_code`, `description`) VALUES
                                                                          (1, '普通用户', 'USER', '普通注册用户'),
                                                                          (2, '商家', 'MERCHANT', '商家身份'),
                                                                          (3, '管理员', 'ADMIN', '后台管理员');
