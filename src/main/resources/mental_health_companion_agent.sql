-- =============================================================================
-- Mental Health Companion Agent｜智能心理健康管理与支持平台
-- 数据库初始化脚本
-- -----------------------------------------------------------------------------
-- 数据库名   : mental_health_companion_agent
-- 字符集     : utf8mb4 / utf8mb4_general_ci
-- 表数量     : 16
-- 适用版本   : MySQL 8.0+
-- 整理日期   : 2026-09-23
-- 命名规范   : 见 docs/db-naming-convention.md
-- -----------------------------------------------------------------------------
-- 本版本相对上一版的变化（命名规范化）
--   1. 表名：拼音/语义错误 → 英文规范名
--      yonghu→student、xinlilaoshi→counselor、jiankangzhishi→knowledge_article、
--      tongzhi→notification、exam*→assessment_*、xinlilaoshi_collection→counselor_favorite、
--      xinlilaoshi_liuyan→counselor_message、xinlilaoshi_order→counseling_appointment、
--      users→admin_user、token→auth_token、config→sys_config、dictionary→sys_dict_item
--   2. 列名：去表名前缀、拼音转英文、时间统一 created_at/updated_at、
--      逻辑删除统一 is_deleted、字典字段去 _types 后缀
--   3. 字典编码：sex_types→gender、shijianduan_types→time_slot 等 8 类
--   4. auth_token.user_type 取值改为规范表名（admin_user / student / counselor）
-- -----------------------------------------------------------------------------
-- ⚠️ 警告：本脚本包含 DROP DATABASE IF EXISTS，执行会清空同名数据库！
-- 【导入】建议显式指定字符集，避免 Windows 平台 GBK 控制台导致中文乱码：
--        mysql --default-character-set=utf8mb4 -uroot -p < mental_health_companion_agent.sql
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP DATABASE IF EXISTS `mental_health_companion_agent`;
CREATE DATABASE `mental_health_companion_agent`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `mental_health_companion_agent`;


-- =============================================================================
-- 1. sys_config｜系统配置
-- =============================================================================
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(100) DEFAULT NULL COMMENT '配置值',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='系统配置';

INSERT INTO `sys_config` (`id`,`config_key`,`config_value`) VALUES (1,'轮播图1','http://localhost:8080/xinlijiankangxitong/upload/config1.jpg'),(2,'轮播图2','http://localhost:8080/xinlijiankangxitong/upload/config2.jpg'),(3,'轮播图3','http://localhost:8080/xinlijiankangxitong/upload/config3.jpg');


-- =============================================================================
-- 2. sys_dict_item｜字典项
-- =============================================================================
DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_code` varchar(200) DEFAULT NULL COMMENT '字典编码',
  `dict_name` varchar(200) DEFAULT NULL COMMENT '字典名称',
  `item_code` int(11) DEFAULT NULL COMMENT '字典项编码',
  `item_name` varchar(200) DEFAULT NULL COMMENT '字典项名称',
  `parent_id` int(11) DEFAULT NULL COMMENT '父项id',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典项';

INSERT INTO `sys_dict_item` (`id`,`dict_code`,`dict_name`,`item_code`,`item_name`,`parent_id`,`remark`,`created_at`) VALUES (1,'gender','性别',1,'男',NULL,NULL,'2022-03-29 11:36:44'),(2,'gender','性别',2,'女',NULL,NULL,'2022-03-29 11:36:44'),(3,'counselor_favorite_type','收藏类型',1,'收藏',NULL,NULL,'2022-03-29 11:36:44'),(4,'appointment_status','预约状态',1,'待审核',NULL,NULL,'2022-03-29 11:36:44'),(5,'appointment_status','预约状态',2,'同意',NULL,NULL,'2022-03-29 11:36:44'),(6,'appointment_status','预约状态',3,'拒绝',NULL,NULL,'2022-03-29 11:36:44'),(7,'time_slot','时间段',1,'08:00-09:00',NULL,NULL,'2022-03-29 11:36:44'),(8,'time_slot','时间段',2,'09:00-10:00',NULL,NULL,'2022-03-29 11:36:44'),(9,'time_slot','时间段',3,'10:00-11:00',NULL,NULL,'2022-03-29 11:36:44'),(10,'time_slot','时间段',4,'11:00-12:00',NULL,NULL,'2022-03-29 11:36:44'),(11,'time_slot','时间段',5,'14:00-15:00',NULL,NULL,'2022-03-29 11:36:44'),(12,'time_slot','时间段',6,'15:00-16:00',NULL,NULL,'2022-03-29 11:36:44'),(13,'time_slot','时间段',7,'16:00-17:00',NULL,NULL,'2022-03-29 11:36:44'),(14,'time_slot','时间段',8,'17:00-18:00',NULL,NULL,'2022-03-29 11:36:44'),(15,'notification_category','通知分类',1,'通知类型1',NULL,NULL,'2022-03-29 11:36:44'),(16,'notification_category','通知分类',2,'通知类型2',NULL,NULL,'2022-03-29 11:36:44'),(17,'knowledge_category','知识分类',1,'健康知识类型1',NULL,NULL,'2022-03-29 11:36:44'),(18,'knowledge_category','知识分类',2,'健康知识类型2',NULL,NULL,'2022-03-29 11:36:44'),(19,'question_type','题目类型',1,'单选题',NULL,NULL,'2022-03-29 11:36:44'),(20,'question_type','题目类型',2,'多选题',NULL,NULL,'2022-03-29 11:36:44'),(21,'question_type','题目类型',3,'判断题',NULL,NULL,'2022-03-29 11:36:44'),(22,'question_type','题目类型',4,'填空题',NULL,NULL,'2022-03-29 11:36:44'),(23,'assessment_paper_status','测评状态',1,'启用',NULL,NULL,'2022-03-29 11:36:44'),(24,'assessment_paper_status','测评状态',2,'禁用',NULL,NULL,'2022-03-29 11:36:44'),(25,'notification_category','通知分类',3,'通知类型3',NULL,'','2022-03-29 13:05:29');


-- =============================================================================
-- 3. assessment_paper｜测评量表
-- =============================================================================
DROP TABLE IF EXISTS `assessment_paper`;
CREATE TABLE `assessment_paper` (
  `id` int(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(200) NOT NULL COMMENT '量表名称',
  `duration_minutes` int(11) NOT NULL COMMENT '作答时长(分钟)',
  `total_score` int(20) NOT NULL DEFAULT '0' COMMENT '量表总分',
  `status` int(11) NOT NULL DEFAULT '0' COMMENT '状态（字典 assessment_paper_status）',
  `is_deleted` int(11) DEFAULT '0' COMMENT '逻辑删除（0未删除 1已删除）',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测评量表';

INSERT INTO `assessment_paper` (`id`,`name`,`duration_minutes`,`total_score`,`status`,`is_deleted`,`created_at`) VALUES (1,'考试试卷1',100,100,1,1,'2022-03-29 11:36:44'),(2,'试卷2',60,10,1,1,'2022-03-29 13:06:12');


-- =============================================================================
-- 4. assessment_question｜测评题目
-- =============================================================================
DROP TABLE IF EXISTS `assessment_question`;
CREATE TABLE `assessment_question` (
  `id` int(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `paper_id` int(20) NOT NULL COMMENT '所属量表id',
  `content` varchar(200) NOT NULL COMMENT '题目内容',
  `options` longtext COMMENT '选项（JSON 字符串）',
  `score` int(20) DEFAULT '0' COMMENT '分值',
  `answer` varchar(200) DEFAULT NULL COMMENT '正确答案',
  `analysis` longtext COMMENT '答案解析',
  `question_type` int(20) DEFAULT '0' COMMENT '题目类型（字典 question_type）',
  `sort_order` int(20) DEFAULT '100' COMMENT '排序，值越大越靠前',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测评题目';

INSERT INTO `assessment_question` (`id`,`paper_id`,`content`,`options`,`score`,`answer`,`analysis`,`question_type`,`sort_order`,`created_at`) VALUES (1,1,'单选题1','[{\"text\":\"答案A\",\"code\":\"A\"},{\"text\":\"答案B\",\"code\":\"B\"},{\"text\":\"正确答案C\",\"code\":\"C\"},{\"text\":\"答案D\",\"code\":\"D\"}]',10,'C','无',1,1,'2022-03-29 11:36:44'),(2,1,'多选题1','[{\"text\":\"答案A\",\"code\":\"A\"},{\"text\":\"正确答案B\",\"code\":\"B\"},{\"text\":\"正确答案C\",\"code\":\"C\"},{\"text\":\"答案D\",\"code\":\"D\"}]',34,'B,C','无',2,2,'2022-03-29 11:36:44'),(3,1,'填空题(正)_____','[]',41,'正','无',4,3,'2022-03-29 11:36:44'),(4,1,'判断题1','[{\"text\":\"A.对\",\"code\":\"A\"},{\"text\":\"B.错\",\"code\":\"B\"}]',11,'A','无',3,4,'2022-03-29 11:36:44'),(5,1,'单选题2','[{\"text\":\"答案A\",\"code\":\"A\"},{\"text\":\"正确答案B\",\"code\":\"B\"},{\"text\":\"答案C\",\"code\":\"C\"},{\"text\":\"答案D\",\"code\":\"D\"}]',4,'B','无',1,5,'2022-03-29 11:36:44'),(6,2,'试题11','[{\"text\":\"A\",\"code\":\"A\"},{\"text\":\"B\",\"code\":\"B\"},{\"text\":\"正确答案C\",\"code\":\"C\"},{\"text\":\"正确答案D\",\"code\":\"D\"}]',10,'C,D','w',2,1,'2022-03-29 13:07:36');


-- =============================================================================
-- 5. assessment_record｜测评记录
-- =============================================================================
DROP TABLE IF EXISTS `assessment_record`;
CREATE TABLE `assessment_record` (
  `id` int(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_no` varchar(200) DEFAULT NULL COMMENT '测评流水号',
  `student_id` int(20) NOT NULL COMMENT '学生id',
  `paper_id` int(20) NOT NULL COMMENT '量表id',
  `total_score` int(20) DEFAULT NULL COMMENT '所得总分',
  `submitted_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '提交时间',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测评记录';

INSERT INTO `assessment_record` (`id`,`record_no`,`student_id`,`paper_id`,`total_score`,`submitted_at`,`created_at`) VALUES (1,'1648530233813',1,1,15,'2022-03-29 13:03:54','2022-03-29 13:03:54');


-- =============================================================================
-- 6. assessment_answer｜答题明细
-- =============================================================================
DROP TABLE IF EXISTS `assessment_answer`;
CREATE TABLE `assessment_answer` (
  `id` int(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_no` varchar(200) DEFAULT NULL COMMENT '测评流水号',
  `student_id` int(20) NOT NULL COMMENT '学生id',
  `question_id` int(20) NOT NULL COMMENT '题目id',
  `student_answer` varchar(200) DEFAULT NULL COMMENT '学生答案',
  `score` int(20) NOT NULL DEFAULT '0' COMMENT '本题得分',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='答题明细';

INSERT INTO `assessment_answer` (`id`,`record_no`,`student_id`,`question_id`,`student_answer`,`score`,`created_at`) VALUES (1,'1648530233813',1,5,'B',4,'2022-03-29 13:03:57'),(2,'1648530233813',1,4,'A',11,'2022-03-29 13:04:00'),(3,'1648530233813',1,3,'A',0,'2022-03-29 13:04:03'),(4,'1648530233813',1,2,'B,D',0,'2022-03-29 13:04:07'),(5,'1648530233813',1,1,'B',0,'2022-03-29 13:04:11');


-- =============================================================================
-- 7. assessment_wrong_question｜错题
-- =============================================================================
DROP TABLE IF EXISTS `assessment_wrong_question`;
CREATE TABLE `assessment_wrong_question` (
  `id` int(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` int(20) NOT NULL COMMENT '学生id',
  `paper_id` int(20) NOT NULL COMMENT '量表id',
  `question_id` int(20) NOT NULL COMMENT '题目id',
  `student_answer` varchar(200) DEFAULT NULL COMMENT '学生答案',
  `answered_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作答时间',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='错题';

INSERT INTO `assessment_wrong_question` (`id`,`student_id`,`paper_id`,`question_id`,`student_answer`,`answered_at`,`created_at`) VALUES (1,1,1,3,'A','2022-03-29 13:04:03','2022-03-29 13:04:03'),(2,1,1,2,'B,D','2022-03-29 13:04:07','2022-03-29 13:04:07'),(3,1,1,1,'B','2022-03-29 13:04:11','2022-03-29 13:04:11');


-- =============================================================================
-- 8. knowledge_article｜健康知识
-- =============================================================================
DROP TABLE IF EXISTS `knowledge_article`;
CREATE TABLE `knowledge_article` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `cover_url` varchar(200) DEFAULT NULL COMMENT '封面图',
  `category` int(11) NOT NULL COMMENT '分类（字典 knowledge_category）',
  `published_at` timestamp NULL DEFAULT NULL COMMENT '发布时间',
  `content` text COMMENT '正文（RAG 知识源）',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='健康知识';

INSERT INTO `knowledge_article` (`id`,`title`,`cover_url`,`category`,`published_at`,`content`,`created_at`) VALUES (1,'健康知识名称1','http://localhost:8080/xinlijiankangxitong/upload/yonghu1.jpg',1,'2022-03-29 11:39:43','健康知识详情1','2022-03-29 11:39:43'),(2,'健康知识名称2','http://localhost:8080/xinlijiankangxitong/upload/yonghu2.jpg',2,'2022-03-29 11:39:43','健康知识详情2','2022-03-29 11:39:43'),(3,'健康知识名称3','http://localhost:8080/xinlijiankangxitong/upload/yonghu3.jpg',1,'2022-03-29 11:39:43','健康知识详情3','2022-03-29 11:39:43'),(4,'健康知识名称4','http://localhost:8080/xinlijiankangxitong/upload/yonghu1.jpg',1,'2022-03-29 11:39:43','健康知识详情4','2022-03-29 11:39:43'),(5,'健康知识名称5','http://localhost:8080/xinlijiankangxitong/upload/yonghu2.jpg',1,'2022-03-29 11:39:43','健康知识详情5','2022-03-29 11:39:43');


-- =============================================================================
-- 9. auth_token｜登录令牌
-- =============================================================================
DROP TABLE IF EXISTS `auth_token`;
CREATE TABLE `auth_token` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `user_type` varchar(100) DEFAULT NULL COMMENT '用户类型（admin_user/student/counselor）',
  `role` varchar(100) DEFAULT NULL COMMENT '角色名',
  `token` varchar(200) NOT NULL COMMENT '令牌',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expired_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '过期时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='登录令牌';

INSERT INTO `auth_token` (`id`,`user_id`,`username`,`user_type`,`role`,`token`,`created_at`,`expired_at`) VALUES (1,6,'admin','admin_user','管理员','qsh241u4wenz9q880lzg956l6gdvnfy5','2022-03-29 11:49:29','2022-03-29 14:10:47'),(2,1,'a1','student','学生','1o29ta3k4dzq9qthlcltpazu9kmmu6ie','2022-03-29 11:50:44','2022-03-29 14:08:30'),(3,1,'a1','counselor','心理老师','nu8v2a1pz7u1qg0es7c4yrjs6vd9r4j3','2022-03-29 13:07:56','2022-03-29 14:08:52');


-- =============================================================================
-- 10. notification｜通知
-- =============================================================================
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `cover_url` varchar(200) DEFAULT NULL COMMENT '封面图',
  `category` int(11) NOT NULL COMMENT '分类（字典 notification_category）',
  `published_at` timestamp NULL DEFAULT NULL COMMENT '发布时间',
  `content` text COMMENT '正文',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知';

INSERT INTO `notification` (`id`,`title`,`cover_url`,`category`,`published_at`,`content`,`created_at`) VALUES (1,'通知名称1','http://localhost:8080/xinlijiankangxitong/upload/yonghu1.jpg',2,'2022-03-29 11:39:43','通知详情1','2022-03-29 11:39:43'),(2,'通知名称2','http://localhost:8080/xinlijiankangxitong/upload/yonghu2.jpg',1,'2022-03-29 11:39:43','通知详情2','2022-03-29 11:39:43'),(3,'通知名称3','http://localhost:8080/xinlijiankangxitong/upload/yonghu3.jpg',1,'2022-03-29 11:39:43','通知详情3','2022-03-29 11:39:43'),(4,'通知名称4','http://localhost:8080/xinlijiankangxitong/upload/yonghu1.jpg',2,'2022-03-29 11:39:43','通知详情4','2022-03-29 11:39:43'),(5,'通知名称5','http://localhost:8080/xinlijiankangxitong/upload/yonghu2.jpg',1,'2022-03-29 11:39:43','<p>通知详情51111</p>','2022-03-29 11:39:43');


-- =============================================================================
-- 11. admin_user｜管理员
-- =============================================================================
DROP TABLE IF EXISTS `admin_user`;
CREATE TABLE `admin_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `role` varchar(100) DEFAULT '管理员' COMMENT '角色名',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='管理员';

INSERT INTO `admin_user` (`id`,`username`,`password`,`role`,`created_at`) VALUES (6,'admin','admin','管理员','2022-05-02 14:51:13');


-- =============================================================================
-- 12. counselor｜心理老师
-- =============================================================================
DROP TABLE IF EXISTS `counselor`;
CREATE TABLE `counselor` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(200) DEFAULT NULL COMMENT '账户',
  `password` varchar(200) DEFAULT NULL COMMENT '密码',
  `name` varchar(200) DEFAULT NULL COMMENT '姓名',
  `phone` varchar(200) DEFAULT NULL COMMENT '手机号',
  `avatar_url` varchar(200) DEFAULT NULL COMMENT '头像',
  `gender` int(11) DEFAULT NULL COMMENT '性别（字典 gender）',
  `email` varchar(200) DEFAULT NULL COMMENT '电子邮箱',
  `expertise` varchar(200) DEFAULT NULL COMMENT '擅长领域',
  `resume` varchar(200) DEFAULT NULL COMMENT '履历',
  `introduction` text COMMENT '详细介绍',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='心理老师';

INSERT INTO `counselor` (`id`,`username`,`password`,`name`,`phone`,`avatar_url`,`gender`,`email`,`expertise`,`resume`,`introduction`,`created_at`) VALUES (1,'a1','123456','心理老师姓名1','17703786901','http://localhost:8080/xinlijiankangxitong/upload/xinlilaoshi1.jpg',2,'1@qq.com','擅长1','履历1','心理老师详细介绍1','2022-03-29 11:39:43'),(2,'a2','123456','心理老师姓名2','17703786902','http://localhost:8080/xinlijiankangxitong/upload/xinlilaoshi2.jpg',2,'2@qq.com','擅长2','履历2','心理老师详细介绍2','2022-03-29 11:39:43'),(3,'a3','123456','心理老师姓名3','17703786903','http://localhost:8080/xinlijiankangxitong/upload/xinlilaoshi3.jpg',1,'3@qq.com','擅长3','履历3','心理老师详细介绍3','2022-03-29 11:39:43');


-- =============================================================================
-- 13. counselor_favorite｜心理老师收藏
-- =============================================================================
DROP TABLE IF EXISTS `counselor_favorite`;
CREATE TABLE `counselor_favorite` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `counselor_id` int(11) DEFAULT NULL COMMENT '心理老师id',
  `student_id` int(11) DEFAULT NULL COMMENT '学生id',
  `favorite_type` int(11) DEFAULT NULL COMMENT '收藏类型（字典 counselor_favorite_type）',
  `favorited_at` timestamp NULL DEFAULT NULL COMMENT '收藏时间',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='心理老师收藏';

INSERT INTO `counselor_favorite` (`id`,`counselor_id`,`student_id`,`favorite_type`,`favorited_at`,`created_at`) VALUES (1,2,3,1,'2022-03-29 11:39:43','2022-03-29 11:39:43'),(2,3,2,1,'2022-03-29 11:39:43','2022-03-29 11:39:43'),(3,1,1,1,'2022-03-29 11:39:43','2022-03-29 11:39:43'),(4,1,3,1,'2022-03-29 11:39:43','2022-03-29 11:39:43'),(5,2,2,1,'2022-03-29 11:39:43','2022-03-29 11:39:43'),(6,2,1,1,'2022-03-29 13:04:50','2022-03-29 13:04:50');


-- =============================================================================
-- 14. counselor_message｜心理老师留言
-- =============================================================================
DROP TABLE IF EXISTS `counselor_message`;
CREATE TABLE `counselor_message` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `counselor_id` int(11) DEFAULT NULL COMMENT '心理老师id',
  `student_id` int(11) DEFAULT NULL COMMENT '学生id',
  `content` text COMMENT '留言内容',
  `sent_at` timestamp NULL DEFAULT NULL COMMENT '留言时间',
  `reply_content` text COMMENT '回复内容',
  `replied_at` timestamp NULL DEFAULT NULL COMMENT '回复时间',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='心理老师留言';

INSERT INTO `counselor_message` (`id`,`counselor_id`,`student_id`,`content`,`sent_at`,`reply_content`,`replied_at`,`created_at`) VALUES (1,2,2,'留言内容1','2022-03-29 11:39:43','回复信息1','2022-03-29 11:39:43','2022-03-29 11:39:43'),(2,1,1,'留言内容2','2022-03-29 11:39:43','回复信息2','2022-03-29 11:39:43','2022-03-29 11:39:43'),(3,1,3,'留言内容3','2022-03-29 11:39:43','回复信息3','2022-03-29 11:39:43','2022-03-29 11:39:43'),(4,2,2,'留言内容4','2022-03-29 11:39:43','回复信息4','2022-03-29 11:39:43','2022-03-29 11:39:43'),(5,3,3,'留言内容5','2022-03-29 11:39:43','回复信息5','2022-03-29 11:39:43','2022-03-29 11:39:43'),(6,1,1,'333333','2022-03-29 13:04:37','22222222222','2022-03-29 13:08:11','2022-03-29 13:04:37');


-- =============================================================================
-- 15. counseling_appointment｜心理咨询预约
-- =============================================================================
DROP TABLE IF EXISTS `counseling_appointment`;
CREATE TABLE `counseling_appointment` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `appointment_no` varchar(200) DEFAULT NULL COMMENT '预约流水号',
  `counselor_id` int(11) DEFAULT NULL COMMENT '心理老师id',
  `student_id` int(11) DEFAULT NULL COMMENT '学生id',
  `appointment_date` date DEFAULT NULL COMMENT '预约日期',
  `time_slot` int(11) DEFAULT NULL COMMENT '时间段（字典 time_slot）',
  `status` int(11) DEFAULT NULL COMMENT '状态（字典 appointment_status）',
  `review_comment` text COMMENT '审核意见',
  `applied_at` timestamp NULL DEFAULT NULL COMMENT '申请时间',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='心理咨询预约';

INSERT INTO `counseling_appointment` (`id`,`appointment_no`,`counselor_id`,`student_id`,`appointment_date`,`time_slot`,`status`,`review_comment`,`applied_at`,`created_at`) VALUES (1,'1648525919361',2,1,'2022-03-30',8,1,NULL,'2022-03-29 11:51:59','2022-03-29 11:51:59'),(2,'1648530223381',3,1,'2022-03-30',8,1,NULL,'2022-03-29 13:03:43','2022-03-29 13:03:43'),(3,'1648530268888',1,1,'2022-03-31',3,2,'key','2022-03-29 13:04:29','2022-03-29 13:04:29');


-- =============================================================================
-- 16. student｜学生
-- =============================================================================
DROP TABLE IF EXISTS `student`;
CREATE TABLE `student` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(200) DEFAULT NULL COMMENT '账户',
  `password` varchar(200) DEFAULT NULL COMMENT '密码',
  `name` varchar(200) DEFAULT NULL COMMENT '姓名',
  `phone` varchar(200) DEFAULT NULL COMMENT '手机号',
  `id_card_no` varchar(200) DEFAULT NULL COMMENT '身份证号',
  `avatar_url` varchar(200) DEFAULT NULL COMMENT '头像',
  `gender` int(11) DEFAULT NULL COMMENT '性别（字典 gender）',
  `email` varchar(200) DEFAULT NULL COMMENT '电子邮箱',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学生';

INSERT INTO `student` (`id`,`username`,`password`,`name`,`phone`,`id_card_no`,`avatar_url`,`gender`,`email`,`created_at`) VALUES (1,'a1','123456','学生姓名1','17703786901','410224199610232001','http://localhost:8080/xinlijiankangxitong/upload/yonghu1.jpg',2,'1@qq.com','2022-03-29 11:39:43'),(2,'a2','123456','学生姓名2','17703786902','410224199610232002','http://localhost:8080/xinlijiankangxitong/upload/yonghu2.jpg',2,'2@qq.com','2022-03-29 11:39:43'),(3,'a3','123456','学生姓名3','17703786903','410224199610232003','http://localhost:8080/xinlijiankangxitong/upload/yonghu3.jpg',2,'3@qq.com','2022-03-29 11:39:43');


SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 脚本结束｜共 16 张表
-- =============================================================================
