-- ============================================================
-- 历史数据回填：把 http_request_record 中已有但未登记的 project_code
-- 补入 http_request_project，使 Python 回放 GUI 的项目下拉能看到旧数据。
--
-- 背景：旧版采集组件只写 http_request_record，不写 http_request_project；
--       重构后的 Starter 会自动登记，新数据无需执行本脚本。
-- 特性：幂等，可重复执行；只插入缺失项目，不改动任何既有记录。
-- ============================================================
USE request_recorder;

INSERT INTO http_request_project (project_code, project_name, description)
SELECT DISTINCT r.project_code, r.project_code, '由历史采集记录回填'
FROM http_request_record r
WHERE r.project_code IS NOT NULL
  AND r.project_code <> ''
  AND NOT EXISTS (
      SELECT 1 FROM http_request_project p WHERE p.project_code = r.project_code
  );

-- 执行后可在 GUI 中看到项目数量：
-- SELECT COUNT(*) AS project_count FROM http_request_project;
