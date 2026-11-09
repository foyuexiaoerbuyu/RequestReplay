CREATE DATABASE IF NOT EXISTS request_recorder CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE request_recorder;
CREATE TABLE IF NOT EXISTS http_request_project(
 id BIGINT PRIMARY KEY AUTO_INCREMENT, project_code VARCHAR(100) NOT NULL UNIQUE,
 project_name VARCHAR(200) NOT NULL, description VARCHAR(500),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS http_request_record(
 id BIGINT PRIMARY KEY AUTO_INCREMENT, project_code VARCHAR(100) NOT NULL, project_name VARCHAR(200),
 request_id VARCHAR(64) NOT NULL, request_time DATETIME(3) NOT NULL, method VARCHAR(20) NOT NULL,
 url TEXT NOT NULL, uri VARCHAR(1000), query_params LONGTEXT, path_params LONGTEXT,
 request_headers LONGTEXT, request_body LONGTEXT, client_ip VARCHAR(64), user_agent VARCHAR(1000),
 response_status INT, response_headers LONGTEXT, response_body LONGTEXT, duration_ms BIGINT,
 response_size BIGINT, exception_message TEXT, request_truncated TINYINT(1) DEFAULT 0,
 response_truncated TINYINT(1) DEFAULT 0, created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_project_time(project_code,request_time), KEY idx_method_status(method,response_status),
 KEY idx_uri(uri(191)), KEY idx_request_id(request_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS http_replay_history(
 id BIGINT PRIMARY KEY AUTO_INCREMENT, source_record_id BIGINT, project_code VARCHAR(100) NOT NULL,
 target_url TEXT NOT NULL, method VARCHAR(20) NOT NULL, replay_time DATETIME(3) NOT NULL,
 replay_status VARCHAR(30) NOT NULL, response_status INT, response_headers LONGTEXT,
 response_body LONGTEXT, duration_ms BIGINT, exception_message TEXT,
 KEY idx_project_time(project_code,replay_time), KEY idx_source(source_record_id)
) ENGINE=InnoDB;

-- 兼容升级：老库（http_request_project 无 updated_at 列）自动补列，脚本可重复执行
DROP PROCEDURE IF EXISTS request_recorder_add_updated_at;
DELIMITER //
CREATE PROCEDURE request_recorder_add_updated_at()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'http_request_project' AND column_name = 'updated_at'
  ) THEN
    ALTER TABLE http_request_project
      ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);
  END IF;
END //
DELIMITER ;
CALL request_recorder_add_updated_at();
DROP PROCEDURE request_recorder_add_updated_at;
